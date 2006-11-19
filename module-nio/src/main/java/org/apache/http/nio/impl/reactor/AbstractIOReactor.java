/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2006 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.nio.impl.reactor;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOReactor;
import org.apache.http.nio.reactor.IOSession;

public abstract class AbstractIOReactor implements IOReactor {

    public static int TIMEOUT_CHECK_INTERVAL = 1000;
    
    private volatile boolean closed = false;
    
    private final Selector selector;
    private final SessionSet sessions;
    private final SessionQueue closedSessions;
    
    private long lastTimeoutCheck;
    
    protected IOEventDispatch eventDispatch = null;
    
    public AbstractIOReactor() throws IOException {
        super();
        this.selector = Selector.open();
        this.sessions = new SessionSet();
        this.closedSessions = new SessionQueue();
        this.lastTimeoutCheck = System.currentTimeMillis();
    }

    protected abstract void acceptable(SelectionKey key);
    
    protected abstract void connectable(SelectionKey key);

    protected abstract void readable(SelectionKey key);

    protected abstract void writable(SelectionKey key);
    
    protected abstract void timeoutCheck(SelectionKey key, long now);

    protected SelectionKey registerChannel(final SocketChannel channel) 
            throws IOException {
        channel.configureBlocking(false);
        return channel.register(this.selector, 0);
    }
    
    protected IOSession newSession(final SelectionKey key) {
        IOSession session = new IOSessionImpl(key, new SessionClosedCallback() {

            public void sessionClosed(IOSession session) {
                closedSessions.push(session);
            }
            
        });
        this.sessions.add(session);
        return session;
    }
    
    public void execute(final IOEventDispatch eventDispatch) {
        if (eventDispatch == null) {
            throw new IllegalArgumentException("Event dispatcher may not be null");
        }
        this.eventDispatch = eventDispatch;
        
        try {
            for (;;) {
                
                int readyCount = 0;
                try {
                    readyCount = this.selector.select(TIMEOUT_CHECK_INTERVAL);
                } catch (IOException ex) {
                    this.closed = true;
                }
                if (this.closed) {
                    break;
                }
                if (readyCount > 0) {
                    processEvents(this.selector.selectedKeys());
                }
                
                long currentTime = System.currentTimeMillis();
                if( (currentTime - this.lastTimeoutCheck) >= TIMEOUT_CHECK_INTERVAL) {
                    this.lastTimeoutCheck = currentTime;
                    Set keys = this.selector.keys();
                    if (keys != null) {
                        processTimeouts(keys);
                    }
                }
                
                processClosedSessions();
                
            }
        } finally {
            closeSessions();
        }
    }
    
    private void processEvents(final Set selectedKeys) {
        for (Iterator it = selectedKeys.iterator(); it.hasNext(); ) {
            
            SelectionKey key = (SelectionKey) it.next();
            processEvent(key);
            
        }
        selectedKeys.clear();
    }

    private void processEvent(final SelectionKey key) {
        try {
            if (key.isAcceptable()) {
                acceptable(key);
            }
            if (key.isConnectable()) {
                connectable(key);
            }
            if (key.isReadable()) {
                readable(key);
            }
            if (key.isWritable()) {
                writable(key);
            }
        } catch (CancelledKeyException ex) {
            Object attachment = key.attachment();
            if (attachment instanceof SessionHandle) {
                SessionHandle handle = (SessionHandle) attachment;
                IOSession session = handle.getSession();
                this.closedSessions.push(session);
                key.attach(null);
            }
        }
    }

    private void processTimeouts(final Set keys) {
        long now = System.currentTimeMillis();
        for (Iterator it = keys.iterator(); it.hasNext();) {
            SelectionKey key = (SelectionKey) it.next();
            timeoutCheck(key, now);
        }
    }

    private void processClosedSessions() {
        IOSession session;
        while ((session = this.closedSessions.pop()) != null) {
            if (this.sessions.remove(session)) {
                this.eventDispatch.disconnected(session);
            }
        }
    }

    private void closeSessions() {
        synchronized (this.sessions) {
            for (Iterator it = this.sessions.iterator(); it.hasNext(); ) {
                IOSession session = (IOSession) it.next();
                if (!session.isClosed()) {    

                    session.close();
                    this.eventDispatch.disconnected(session);
                }
            }
            this.sessions.clear();
        }
    }
    
    public void shutdown() throws IOException {
        if (this.closed) {
            return;
        }
        this.closed = true;
        // Stop dispatching I/O events
        this.selector.close();
    }
        
}

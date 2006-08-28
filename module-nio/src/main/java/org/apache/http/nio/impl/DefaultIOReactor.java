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

package org.apache.http.nio.impl;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

import org.apache.http.nio.IOSession;
import org.apache.http.nio.IOEventDispatch;
import org.apache.http.nio.IOReactor;

public class DefaultIOReactor implements IOReactor {

    public static int TIMEOUT_CHECK_INTERVAL = 1000;
    
    private volatile boolean closed = false;
    private final ServerSocketChannel serverChannel;
    private final Selector selector;
    private final SessionSet sessions;
    private final SessionQueue closedSessions;
    private long lastTimeoutCheck;
    
    private IOEventDispatch eventDispatch = null;
    
    public DefaultIOReactor(final SocketAddress address) throws IOException {
        super();
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.configureBlocking(false);
        this.serverChannel.socket().bind(address);
        this.selector = Selector.open();
        this.sessions = new SessionSet();
        this.closedSessions = new SessionQueue();
        this.lastTimeoutCheck = System.currentTimeMillis();
    }
    
    public synchronized void execute(final IOEventDispatch eventDispatch) throws IOException {
        if (eventDispatch == null) {
            throw new IllegalArgumentException("Event dispatcher may not be null");
        }
        this.eventDispatch = eventDispatch;
        this.serverChannel.register(this.selector, SelectionKey.OP_ACCEPT);
        try {
            for (;;) {
                int readyCount = this.selector.select(TIMEOUT_CHECK_INTERVAL);
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
                        processSessionTimeouts(keys);
                    }
                }
                
                processClosedSessions();
                
            }
        } finally {
            closeSessions();
            this.eventDispatch = null;
        }
    }
    
    private void processEvents(final Set selectedKeys) throws IOException {
        for (Iterator it = selectedKeys.iterator(); it.hasNext(); ) {
            
            SelectionKey key = (SelectionKey) it.next();
            processEvent(key);
            
        }
        selectedKeys.clear();
    }

    private void processEvent(final SelectionKey key) throws IOException {
        try {
            if (key.isAcceptable()) {
                
                SocketChannel socketChannel = this.serverChannel.accept();
                if (socketChannel != null) {
                    
                    // Register new channel with the selector
                    socketChannel.configureBlocking(false);
                    SelectionKey newkey = socketChannel.register(this.selector, 0);
                    
                    // Set up new session
                    IOSession session = new DefaultIOSession(newkey, new SessionClosedCallback() {

                        public void sessionClosed(IOSession session) {
                            closedSessions.push(session);
                        }
                        
                    });
                    this.sessions.add(session);
                    
                    // Attach session handle to the selection key
                    SessionHandle handle = new SessionHandle(session); 
                    newkey.attach(handle);
                    
                    this.eventDispatch.connected(session);
                }
            }
            
            if (key.isReadable()) {
                SessionHandle handle = (SessionHandle) key.attachment();
                IOSession session = handle.getSession();
                handle.resetLastRead();

                this.eventDispatch.inputReady(session);
            }
            
            if (key.isWritable()) {
                SessionHandle handle = (SessionHandle) key.attachment();
                IOSession session = handle.getSession();
                handle.resetLastWrite();
                
                this.eventDispatch.outputReady(session);
            }
            
        } catch (CancelledKeyException ex) {
            SessionHandle handle = (SessionHandle) key.attachment();
            if (handle != null) {
                key.attach(null);
                IOSession session = handle.getSession();
                this.closedSessions.push(session);
            }
        }
    }
    
    private void processSessionTimeouts(final Set keys) {
        long now = System.currentTimeMillis();
        for (Iterator it = keys.iterator(); it.hasNext();) {
            SelectionKey key = (SelectionKey) it.next();
            SessionHandle handle = (SessionHandle) key.attachment();
            if (handle != null) {
                IOSession session = handle.getSession();
                int timeout = session.getSocketTimeout();
                if (timeout > 0) {
                    if (handle.getLastRead() + timeout < now) {
                        this.eventDispatch.timeout(session);
                    }
                }
            }
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
        for (Iterator it = this.sessions.iterator(); it.hasNext(); ) {
            IOSession session = (IOSession) it.next();
            if (!session.isClosed()) {    

                session.close();
                this.eventDispatch.disconnected(session);
            }
        }
        this.sessions.clear();
    }
    
    public void shutdown() throws IOException {
        if (this.closed) {
            return;
        }
        this.closed = true;
        // Do not accept new connections
        this.serverChannel.close();
        // Stop dispatching I/O events
        this.selector.close();
    }
    
}

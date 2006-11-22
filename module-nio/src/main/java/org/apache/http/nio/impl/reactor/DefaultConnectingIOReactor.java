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
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.SessionRequest;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

public class DefaultConnectingIOReactor extends AbstractMultiworkerIOReactor 
        implements ConnectingIOReactor {

    public static int TIMEOUT_CHECK_INTERVAL = 1000;
    
    private volatile boolean closed = false;
    
    private final HttpParams params;
    private final Selector selector;
    
    private long lastTimeoutCheck;
    
    public DefaultConnectingIOReactor(int workerCount, final HttpParams params) 
            throws IOException {
        super(TIMEOUT_CHECK_INTERVAL, workerCount);
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.params = params;
        this.selector = Selector.open();
        this.lastTimeoutCheck = System.currentTimeMillis();
    }

    public void execute(final IOEventDispatch eventDispatch) throws IOException {
        if (eventDispatch == null) {
            throw new IllegalArgumentException("Event dispatcher may not be null");
        }
        startWorkers(eventDispatch);
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
                    processTimeouts(keys);
                }
            }
            verifyWorkers();
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
            
            if (key.isConnectable()) {

                SocketChannel channel = (SocketChannel) key.channel();
                // Get request handle
                SessionRequestHandle requestHandle = (SessionRequestHandle) key.attachment();
                SessionRequestImpl sessionRequest = requestHandle.getSessionRequest();
                
                // Finish connection process
                try {
                    channel.finishConnect();
                } catch (IOException ex) {
                    sessionRequest.failed(ex);
                }
                key.cancel();
                if (channel.isConnected()) {
                    prepareSocket(channel.socket());
                    Object attachment = sessionRequest.getAttachment();
                    ChannelEntry entry = new ChannelEntry(channel, attachment); 
                    addChannel(entry);
                }
            }
                        
        } catch (CancelledKeyException ex) {
            key.attach(null);
        }
    }

    protected void prepareSocket(final Socket socket) throws IOException {
        socket.setTcpNoDelay(HttpConnectionParams.getTcpNoDelay(this.params));
        socket.setSoTimeout(HttpConnectionParams.getSoTimeout(this.params));
        int linger = HttpConnectionParams.getLinger(this.params);
        if (linger >= 0) {
            socket.setSoLinger(linger > 0, linger);
        }
    }
    
    private void processTimeouts(final Set keys) {
        long now = System.currentTimeMillis();
        for (Iterator it = keys.iterator(); it.hasNext();) {
            SelectionKey key = (SelectionKey) it.next();
            Object attachment = key.attachment();
            
            if (attachment instanceof SessionRequestHandle) {
                SessionRequestHandle handle = (SessionRequestHandle) key.attachment();
                SessionRequestImpl sessionRequest = handle.getSessionRequest();
                int timeout = sessionRequest.getConnectTimeout();
                if (timeout > 0) {
                    if (handle.getRequestTime() + timeout < now) {
                        sessionRequest.timeout();
                    }
                }
            }
            
        }
    }

    public SessionRequest connect(
            final SocketAddress remoteAddress, 
            final SocketAddress localAddress,
            final Object attachment) throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        if (localAddress != null) {
            socketChannel.socket().bind(localAddress);
        }
        socketChannel.connect(remoteAddress);
        SelectionKey key = socketChannel.register(this.selector, SelectionKey.OP_CONNECT);
        
        SessionRequestImpl sessionRequest = new SessionRequestImpl(
                remoteAddress, localAddress, attachment, key);
        sessionRequest.setConnectTimeout(HttpConnectionParams.getConnectionTimeout(this.params));

        SessionRequestHandle requestHandle = new SessionRequestHandle(sessionRequest); 
        key.attach(requestHandle);
        return sessionRequest;
    }

    public void shutdown() throws IOException {
        if (this.closed) {
            return;
        }
        this.closed = true;
        // Stop dispatching I/O events
        this.selector.close();
        // Stop the workers
        stopWorkers(500);
    }
        
}

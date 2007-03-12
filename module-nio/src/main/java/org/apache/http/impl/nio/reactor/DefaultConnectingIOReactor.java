/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.impl.nio.reactor;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

import org.apache.http.util.concurrent.ThreadFactory;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.nio.reactor.SessionRequest;
import org.apache.http.nio.reactor.SessionRequestCallback;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

public class DefaultConnectingIOReactor extends AbstractMultiworkerIOReactor 
        implements ConnectingIOReactor {

    public static int TIMEOUT_CHECK_INTERVAL = 1000;
    
    private volatile boolean closed = false;
    
    private final HttpParams params;
    private final Selector selector;
    private final SessionRequestQueue requestQueue;
    
    private long lastTimeoutCheck;
    
    public DefaultConnectingIOReactor(
            int workerCount, 
            final ThreadFactory threadFactory,
            final HttpParams params) throws IOReactorException {
        super(TIMEOUT_CHECK_INTERVAL, workerCount, threadFactory);
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.params = params;
        this.requestQueue = new SessionRequestQueue();
        this.lastTimeoutCheck = System.currentTimeMillis();
        try {
            this.selector = Selector.open();
        } catch (IOException ex) {
            throw new IOReactorException("Failure opening selector", ex);
        }
    }

    public DefaultConnectingIOReactor(
            int workerCount, 
            final HttpParams params) throws IOReactorException {
        this(workerCount, null, params);
    }
    
    public void execute(final IOEventDispatch eventDispatch) 
            throws InterruptedIOException, IOReactorException {
        if (eventDispatch == null) {
            throw new IllegalArgumentException("Event dispatcher may not be null");
        }
        startWorkers(eventDispatch);
        for (;;) {
            int readyCount;
            try {
                readyCount = this.selector.select(TIMEOUT_CHECK_INTERVAL);
            } catch (InterruptedIOException ex) {
                throw ex;
            } catch (IOException ex) {
                throw new IOReactorException("Unexpected selector failure", ex);
            }

            if (this.closed) {
                break;
            }
            
            processSessionRequests();
            
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
    
    private void processEvents(final Set selectedKeys) {
        for (Iterator it = selectedKeys.iterator(); it.hasNext(); ) {
            
            SelectionKey key = (SelectionKey) it.next();
            processEvent(key);
            
        }
        selectedKeys.clear();
    }

    private void processEvent(final SelectionKey key) {
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
                    try {
                        prepareSocket(channel.socket());
                        ChannelEntry entry = new ChannelEntry(channel, sessionRequest); 
                        addChannel(entry);
                    } catch (IOException ex) {
                        sessionRequest.failed(ex);
                    }
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
            final Object attachment,
            final SessionRequestCallback callback) {

        SessionRequestImpl sessionRequest = new SessionRequestImpl(
                remoteAddress, localAddress, attachment, callback);
        sessionRequest.setConnectTimeout(HttpConnectionParams.getConnectionTimeout(this.params));
        
        this.requestQueue.push(sessionRequest);
        this.selector.wakeup();
        
        return sessionRequest;
    }
    
    private void validateAddress(final SocketAddress address) throws UnknownHostException {
        if (address == null) {
            return;
        }
        if (address instanceof InetSocketAddress) {
            InetSocketAddress endpoint = (InetSocketAddress) address;
            if (endpoint.isUnresolved()) {
                throw new UnknownHostException(endpoint.getHostName());
            }
        }
    }
    
    private void processSessionRequests() throws IOReactorException {
        SessionRequestImpl request;
        while ((request = this.requestQueue.pop()) != null) {
            if (request.isCompleted()) {
                continue;
            }
            SocketChannel socketChannel;
            try {
                socketChannel = SocketChannel.open();
                socketChannel.configureBlocking(false);
            } catch (IOException ex) {
                throw new IOReactorException("Failure opening socket", ex);
            }
            try {
                validateAddress(request.getLocalAddress());
                validateAddress(request.getRemoteAddress());
                
                if (request.getLocalAddress() != null) {
                    socketChannel.socket().bind(request.getLocalAddress());
                }
                socketChannel.connect(request.getRemoteAddress());
            } catch (IOException ex) {
                request.failed(ex);
                return;
            }
            
            SelectionKey key;
            try {
                key = socketChannel.register(this.selector, 0);
                request.setKey(key);
            } catch (IOException ex) {
                throw new IOReactorException("Failure registering channel " +
                        "with the selector", ex);
            }

            SessionRequestHandle requestHandle = new SessionRequestHandle(request); 
            key.attach(requestHandle);
            key.interestOps(SelectionKey.OP_CONNECT);
        }
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

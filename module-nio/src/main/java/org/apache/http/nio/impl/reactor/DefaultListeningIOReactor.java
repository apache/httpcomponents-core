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
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.ListeningIOReactor;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

public class DefaultListeningIOReactor extends AbstractMultiworkerIOReactor 
        implements ListeningIOReactor {

    public static int TIMEOUT_CHECK_INTERVAL = 1000;
    
    private volatile boolean closed = false;
    
    private final HttpParams params;
    private final Selector selector;
    
    public DefaultListeningIOReactor(int workerCount, final HttpParams params) 
            throws IOException {
        super(workerCount);
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.params = params;
        this.selector = Selector.open();
    }

    public void execute(final IOEventDispatch eventDispatch) 
            throws IOException {
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
            
            if (key.isAcceptable()) {
                
                ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
                SocketChannel socketChannel = serverChannel.accept();
                if (socketChannel != null) {
                    prepareSocket(socketChannel.socket());
                    ChannelEntry entry = new ChannelEntry(socketChannel); 
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

    public void listen(
            final SocketAddress address) throws IOException {
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(address);
        SelectionKey key = serverChannel.register(this.selector, SelectionKey.OP_ACCEPT);
        key.attach(null);
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

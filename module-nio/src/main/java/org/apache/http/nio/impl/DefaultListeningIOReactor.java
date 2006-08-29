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
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;

import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

public class DefaultListeningIOReactor extends AbstractIOReactor {

    private final ServerSocketChannel serverChannel;
    private final HttpParams params;
    
    public DefaultListeningIOReactor(final SocketAddress address, final HttpParams params) 
            throws IOException {
        super();
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.params = params;
        this.serverChannel = ServerSocketChannel.open();
        this.serverChannel.configureBlocking(false);
        this.serverChannel.socket().bind(address);
    }

    protected void onExecute() throws IOException {
        this.serverChannel.register(getSelector(), SelectionKey.OP_ACCEPT);
    }

    protected void onNewSocket(final Socket socket) throws IOException {
        socket.setTcpNoDelay(HttpConnectionParams.getTcpNoDelay(this.params));
        socket.setSoTimeout(HttpConnectionParams.getSoTimeout(this.params));
        int linger = HttpConnectionParams.getLinger(this.params);
        if (linger >= 0) {
            socket.setSoLinger(linger > 0, linger);
        }
    }

    protected void onShutdown() throws IOException {
        this.serverChannel.close();
    }
    
}

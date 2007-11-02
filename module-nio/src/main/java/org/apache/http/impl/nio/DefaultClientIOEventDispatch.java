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

package org.apache.http.impl.nio;

import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.nio.NHttpClientIOTarget;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.params.HttpParams;

public class DefaultClientIOEventDispatch implements IOEventDispatch {

    private static final String NHTTP_CONN = "NHTTP_CONN";
    
    private final ByteBufferAllocator allocator;
    private final NHttpClientHandler handler;
    private final HttpParams params;

    public DefaultClientIOEventDispatch(
            final NHttpClientHandler handler, 
            final HttpParams params) {
        super();
        if (handler == null) {
            throw new IllegalArgumentException("HTTP client handler may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.allocator = createByteBufferAllocator();
        this.handler = handler;
        this.params = params;
    }
    
    protected ByteBufferAllocator createByteBufferAllocator() {
        return new HeapByteBufferAllocator(); 
    }
        
    protected NHttpClientIOTarget createConnection(final IOSession session) {
        return new DefaultNHttpClientConnection(
                session, 
                new DefaultHttpResponseFactory(),
                this.allocator,
                this.params); 
    }
        
    public void connected(final IOSession session) {
        NHttpClientIOTarget conn = createConnection(session);
        Object attachment = session.getAttribute(IOSession.ATTACHMENT_KEY);
//        session.setAttribute(NHTTP_CONN, conn);
        this.handler.connected(conn, attachment);
    }

    public void disconnected(final IOSession session) {
        NHttpClientIOTarget conn = 
            (NHttpClientIOTarget) session.getAttribute(NHTTP_CONN);
        this.handler.closed(conn);
    }

    public void inputReady(final IOSession session) {
        NHttpClientIOTarget conn = 
            (NHttpClientIOTarget) session.getAttribute(NHTTP_CONN);
        conn.consumeInput(this.handler);
    }

    public void outputReady(final IOSession session) {
        NHttpClientIOTarget conn = 
            (NHttpClientIOTarget) session.getAttribute(NHTTP_CONN);
        conn.produceOutput(this.handler);
    }

    public void timeout(final IOSession session) {
        NHttpClientIOTarget conn = 
            (NHttpClientIOTarget) session.getAttribute(NHTTP_CONN);
        this.handler.timeout(conn);
    }

}

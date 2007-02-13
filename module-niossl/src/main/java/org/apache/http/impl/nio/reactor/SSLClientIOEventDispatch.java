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

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;

import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.nio.DefaultNHttpClientConnection;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.params.HttpParams;

public class SSLClientIOEventDispatch implements IOEventDispatch {

    private static final String NHTTP_CONN = "NHTTP_CONN";
    private static final String SSL_SESSION = "SSL_SESSION";
    
    private final NHttpClientHandler handler;
    private final HttpParams params;
    private final SSLContext sslcontext;
    
    public SSLClientIOEventDispatch(
            final NHttpClientHandler handler,
            final SSLContext sslcontext,
            final HttpParams params) {
        super();
        if (handler == null) {
            throw new IllegalArgumentException("HTTP client handler may not be null");
        }
        if (sslcontext == null) {
            throw new IllegalArgumentException("SSL context may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.handler = handler;
        this.params = params;
        this.sslcontext = sslcontext;
    }
    
    public void connected(final IOSession session) {

        SSLIOSession sslSession = new SSLIOSession(
                session, 
                this.sslcontext); 
        
        DefaultNHttpClientConnection conn = new DefaultNHttpClientConnection(
                sslSession, 
                new DefaultHttpResponseFactory(),
                this.params); 
        
        session.setAttribute(NHTTP_CONN, conn);
        session.setAttribute(SSL_SESSION, sslSession);
        
        Object attachment = session.getAttribute(IOSession.ATTACHMENT_KEY);
        this.handler.connected(conn, attachment);

        try {
            sslSession.initialize(true);
        } catch (SSLException ex) {
            this.handler.exception(conn, ex);
            sslSession.shutdown();
        }
    }

    public void disconnected(final IOSession session) {
        DefaultNHttpClientConnection conn = (DefaultNHttpClientConnection) session.getAttribute(
                NHTTP_CONN);
        
        this.handler.closed(conn);
    }

    public void inputReady(final IOSession session) {
        DefaultNHttpClientConnection conn = (DefaultNHttpClientConnection) session.getAttribute(
                NHTTP_CONN);
        SSLIOSession sslSession = (SSLIOSession) session.getAttribute(
                SSL_SESSION);
        try {
            synchronized (sslSession) {
                while (sslSession.isAppInputReady()) {
                    conn.consumeInput(this.handler);
                }
                sslSession.inboundTransport();
            }
        } catch (IOException ex) {
            this.handler.exception(conn, ex);
            sslSession.shutdown();
        }
    }

    public void outputReady(final IOSession session) {
        DefaultNHttpClientConnection conn = (DefaultNHttpClientConnection) session.getAttribute(
                NHTTP_CONN);
        SSLIOSession sslSession = (SSLIOSession) session.getAttribute(
                SSL_SESSION);
        try {
            synchronized (sslSession) {
                if (sslSession.isAppOutputReady()) {
                    conn.produceOutput(this.handler);
                }
                sslSession.outboundTransport();
            }
        } catch (IOException ex) {
            this.handler.exception(conn, ex);
            sslSession.shutdown();
        }
    }

    public void timeout(final IOSession session) {
        DefaultNHttpClientConnection conn = (DefaultNHttpClientConnection) session.getAttribute(
                NHTTP_CONN);
        SSLIOSession sslSession = (SSLIOSession) session.getAttribute(
                SSL_SESSION);
        this.handler.timeout(conn);
        synchronized (sslSession) {
            if (sslSession.isOutboundDone() && !sslSession.isInboundDone()) {
                // The session failed to terminate cleanly 
                sslSession.shutdown();
            }
        }
    }

}

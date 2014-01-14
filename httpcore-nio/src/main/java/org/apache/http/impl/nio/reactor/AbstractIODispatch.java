/*
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

import org.apache.http.annotation.Immutable;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.ssl.SSLIOSession;
import org.apache.http.util.Asserts;

/**
 * Abstract {@link IOEventDispatch} implementation that supports both plain (non-encrypted)
 * and SSL encrypted HTTP connections.
 *
 * @param <T> the connection type.
 * @since 4.2
 */
@Immutable // provided injected dependencies are immutable
public abstract class AbstractIODispatch<T> implements IOEventDispatch {

    protected abstract T createConnection(IOSession session);

    protected abstract void onConnected(T conn);

    protected abstract void onClosed(T conn);

    protected abstract void onException(T conn, IOException ex);

    protected abstract void onInputReady(T conn);

    protected abstract void onOutputReady(T conn);

    protected abstract void onTimeout(T conn);

    private void ensureNotNull(final T conn) {
        Asserts.notNull(conn, "HTTP connection");
    }

    @Override
    public void connected(final IOSession session) {
        @SuppressWarnings("unchecked")
        T conn = (T) session.getAttribute(IOEventDispatch.CONNECTION_KEY);
        try {
            if (conn == null) {
                conn = createConnection(session);
                session.setAttribute(IOEventDispatch.CONNECTION_KEY, conn);
            }
            onConnected(conn);
            final SSLIOSession ssliosession = (SSLIOSession) session.getAttribute(
                    SSLIOSession.SESSION_KEY);
            if (ssliosession != null) {
                try {
                    synchronized (ssliosession) {
                        if (!ssliosession.isInitialized()) {
                            ssliosession.initialize();
                        }
                    }
                } catch (final IOException ex) {
                    onException(conn, ex);
                    ssliosession.shutdown();
                }
            }
        } catch (final RuntimeException ex) {
            session.shutdown();
            throw ex;
        }
    }

    @Override
    public void disconnected(final IOSession session) {
        @SuppressWarnings("unchecked")
        final
        T conn = (T) session.getAttribute(IOEventDispatch.CONNECTION_KEY);
        if (conn != null) {
            onClosed(conn);
        }
    }

    @Override
    public void inputReady(final IOSession session) {
        @SuppressWarnings("unchecked")
        final
        T conn = (T) session.getAttribute(IOEventDispatch.CONNECTION_KEY);
        try {
            ensureNotNull(conn);
            final SSLIOSession ssliosession = (SSLIOSession) session.getAttribute(
                    SSLIOSession.SESSION_KEY);
            if (ssliosession == null) {
                onInputReady(conn);
            } else {
                try {
                    if (!ssliosession.isInitialized()) {
                        ssliosession.initialize();
                    }
                    if (ssliosession.isAppInputReady()) {
                        onInputReady(conn);
                    }
                    ssliosession.inboundTransport();
                } catch (final IOException ex) {
                    onException(conn, ex);
                    ssliosession.shutdown();
                }
            }
        } catch (final RuntimeException ex) {
            session.shutdown();
            throw ex;
        }
    }

    @Override
    public void outputReady(final IOSession session) {
        @SuppressWarnings("unchecked")
        final
        T conn = (T) session.getAttribute(IOEventDispatch.CONNECTION_KEY);
        try {
            ensureNotNull(conn);
            final SSLIOSession ssliosession = (SSLIOSession) session.getAttribute(
                    SSLIOSession.SESSION_KEY);
            if (ssliosession == null) {
                onOutputReady(conn);
            } else {
                try {
                    if (!ssliosession.isInitialized()) {
                        ssliosession.initialize();
                    }
                    if (ssliosession.isAppOutputReady()) {
                        onOutputReady(conn);
                    }
                    ssliosession.outboundTransport();
                } catch (final IOException ex) {
                    onException(conn, ex);
                    ssliosession.shutdown();
                }
            }
        } catch (final RuntimeException ex) {
            session.shutdown();
            throw ex;
        }
    }

    @Override
    public void timeout(final IOSession session) {
        @SuppressWarnings("unchecked")
        final
        T conn = (T) session.getAttribute(IOEventDispatch.CONNECTION_KEY);
        try {
            final SSLIOSession ssliosession = (SSLIOSession) session.getAttribute(
                    SSLIOSession.SESSION_KEY);
            ensureNotNull(conn);
            onTimeout(conn);
            if (ssliosession != null) {
                synchronized (ssliosession) {
                    if (ssliosession.isOutboundDone() && !ssliosession.isInboundDone()) {
                        // The session failed to terminate cleanly
                        ssliosession.shutdown();
                    }
                }
            }
        } catch (final RuntimeException ex) {
            session.shutdown();
            throw ex;
        }
    }

}

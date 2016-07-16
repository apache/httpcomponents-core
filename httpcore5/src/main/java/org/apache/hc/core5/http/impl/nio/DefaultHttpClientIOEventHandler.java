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

package org.apache.hc.core5.http.impl.nio;

import java.io.IOException;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.nio.NHttpClientEventHandler;
import org.apache.hc.core5.reactor.AbstractIOEventHandler;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Args;

/**
 * Default {@link org.apache.hc.core5.reactor.IOEventHandler} implementation
 * that supports both plain (non-encrypted) and SSL encrypted client side HTTP
 * connections.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class DefaultHttpClientIOEventHandler
                    extends AbstractIOEventHandler<DefaultNHttpClientConnection> {

    private final NHttpClientEventHandler handler;

    /**
     * Creates a new instance of this class to be used for dispatching I/O event
     * notifications to the given protocol handler.
     *
     * @param handler the client protocol handler.
     */
    public DefaultHttpClientIOEventHandler(final NHttpClientEventHandler handler) {
        super();
        this.handler = Args.notNull(handler, "HTTP client handler");
    }

    @Override
    protected void onConnected(final DefaultNHttpClientConnection conn) {
        final Object attachment = conn.getContext().getAttribute(IOSession.ATTACHMENT_KEY);
        try {
            this.handler.connected(conn, attachment);
        } catch (final Exception ex) {
            this.handler.exception(conn, ex);
        }
    }

    @Override
    protected void onClosed(final DefaultNHttpClientConnection conn) {
        this.handler.closed(conn);
    }

    @Override
    protected void onException(final DefaultNHttpClientConnection conn, final IOException ex) {
        this.handler.exception(conn, ex);
    }

    @Override
    protected void onInputReady(final DefaultNHttpClientConnection conn) {
        conn.consumeInput(this.handler);
    }

    @Override
    protected void onOutputReady(final DefaultNHttpClientConnection conn) {
        conn.produceOutput(this.handler);
    }

    @Override
    protected void onTimeout(final DefaultNHttpClientConnection conn) {
        try {
            this.handler.timeout(conn);
        } catch (final Exception ex) {
            this.handler.exception(conn, ex);
        }
    }

}

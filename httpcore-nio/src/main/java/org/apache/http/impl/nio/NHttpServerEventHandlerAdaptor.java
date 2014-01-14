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

package org.apache.http.impl.nio;

import java.io.IOException;

import org.apache.http.HttpException;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.NHttpServerEventHandler;
import org.apache.http.nio.NHttpServiceHandler;

/**
 * @deprecated (4.2)
 */
@Deprecated
class NHttpServerEventHandlerAdaptor implements NHttpServerEventHandler {

    private final NHttpServiceHandler handler;

    public NHttpServerEventHandlerAdaptor(final NHttpServiceHandler handler) {
        super();
        this.handler = handler;
    }

    @Override
    public void connected(final NHttpServerConnection conn) {
        this.handler.connected(conn);
    }

    @Override
    public void responseReady(
            final NHttpServerConnection conn) throws IOException, HttpException {
        this.handler.responseReady(conn);
    }

    @Override
    public void requestReceived(
            final NHttpServerConnection conn) throws IOException, HttpException {
        this.handler.requestReceived(conn);
    }

    @Override
    public void inputReady(
            final NHttpServerConnection conn,
            final ContentDecoder decoder) throws IOException, HttpException {
        this.handler.inputReady(conn, decoder);
    }

    @Override
    public void outputReady(
            final NHttpServerConnection conn,
            final ContentEncoder encoder) throws IOException, HttpException {
        this.handler.outputReady(conn, encoder);
    }

    @Override
    public void exception(final NHttpServerConnection conn, final Exception ex) {
        if (ex instanceof HttpException) {
            this.handler.exception(conn, (HttpException) ex);
        } else if (ex instanceof IOException) {
            this.handler.exception(conn, (IOException) ex);
        } else {
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            } else {
                throw new Error("Unexpected exception: ", ex);
            }
        }
    }

    @Override
    public void endOfInput(final NHttpServerConnection conn) throws IOException {
        conn.close();
    }

    @Override
    public void timeout(final NHttpServerConnection conn) {
        this.handler.timeout(conn);
    }

    @Override
    public void closed(final NHttpServerConnection conn) {
        this.handler.closed(conn);
    }

}

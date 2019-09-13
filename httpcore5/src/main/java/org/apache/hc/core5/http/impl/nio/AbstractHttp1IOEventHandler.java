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
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import javax.net.ssl.SSLSession;

import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;

class AbstractHttp1IOEventHandler implements HttpConnectionEventHandler {

    final AbstractHttp1StreamDuplexer<?, ?> streamDuplexer;

    AbstractHttp1IOEventHandler(final AbstractHttp1StreamDuplexer<?, ?> streamDuplexer) {
        this.streamDuplexer = Args.notNull(streamDuplexer, "Stream multiplexer");
    }

    @Override
    public void connected(final IOSession session) throws IOException {
        try {
            streamDuplexer.onConnect();
        } catch (final HttpException ex) {
            streamDuplexer.onException(ex);
        }
    }

    @Override
    public void inputReady(final IOSession session, final ByteBuffer src) throws IOException {
        try {
            streamDuplexer.onInput(src);
        } catch (final HttpException ex) {
            streamDuplexer.onException(ex);
        }
    }

    @Override
    public void outputReady(final IOSession session) throws IOException {
        try {
            streamDuplexer.onOutput();
        } catch (final HttpException ex) {
            streamDuplexer.onException(ex);
        }
    }

    @Override
    public void timeout(final IOSession session, final Timeout timeout) throws IOException {
        try {
            streamDuplexer.onTimeout(timeout);
        } catch (final HttpException ex) {
            streamDuplexer.onException(ex);
        }
    }

    @Override
    public void exception(final IOSession session, final Exception cause) {
        streamDuplexer.onException(cause);
    }

    @Override
    public void disconnected(final IOSession session) {
        streamDuplexer.onDisconnect();
    }

    @Override
    public void close() throws IOException {
        streamDuplexer.close();
    }

    @Override
    public void close(final CloseMode closeMode) {
        streamDuplexer.close(closeMode);
    }

    @Override
    public boolean isOpen() {
        return streamDuplexer.isOpen();
    }

    @Override
    public void setSocketTimeout(final Timeout timeout) {
        streamDuplexer.setSocketTimeout(timeout);
    }

    @Override
    public SSLSession getSSLSession() {
        return streamDuplexer.getSSLSession();
    }

    @Override
    public EndpointDetails getEndpointDetails() {
        return streamDuplexer.getEndpointDetails();
    }

    @Override
    public Timeout getSocketTimeout() {
        return streamDuplexer.getSocketTimeout();
    }

    @Override
    public ProtocolVersion getProtocolVersion() {
        return streamDuplexer.getProtocolVersion();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return streamDuplexer.getRemoteAddress();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return streamDuplexer.getLocalAddress();
    }

}

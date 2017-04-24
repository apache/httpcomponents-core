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

import javax.net.ssl.SSLSession;

import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Args;

class AbstractHttp1IOEventHandler implements HttpConnectionEventHandler {

    private final AbstractHttp1StreamDuplexer<?, ?> streamDuplexer;

    AbstractHttp1IOEventHandler(final AbstractHttp1StreamDuplexer<?, ?> streamDuplexer) {
        this.streamDuplexer = Args.notNull(streamDuplexer, "Stream multiplexer");
    }

    @Override
    public void connected(final IOSession session) {
        try {
            streamDuplexer.onConnect(null);
        } catch (final Exception ex) {
            streamDuplexer.onException(ex);
        }
    }

    @Override
    public void inputReady(final IOSession session) {
        try {
            streamDuplexer.onInput();
        } catch (final Exception ex) {
            streamDuplexer.onException(ex);
        }
    }

    @Override
    public void outputReady(final IOSession session) {
        try {
            streamDuplexer.onOutput();
        } catch (final Exception ex) {
            streamDuplexer.onException(ex);
        }
    }

    @Override
    public void timeout(final IOSession session) {
        try {
            streamDuplexer.onTimeout();
        } catch (final Exception ex) {
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
    public void shutdown(final ShutdownType shutdownType) {
        streamDuplexer.shutdown(shutdownType);
    }

    @Override
    public boolean isOpen() {
        return streamDuplexer.isOpen();
    }

    @Override
    public void setSocketTimeout(final int timeout) {
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
    public int getSocketTimeout() {
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

    @Override
    public String toString() {
        return streamDuplexer.toString();
    }

}

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

package org.apache.hc.core5.http2.impl.nio;

import java.io.IOException;
import java.net.SocketAddress;

import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.impl.nio.HttpConnectionEventHandler;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Args;

class AbstractHttp2IOEventHandler implements HttpConnectionEventHandler {

    private final AbstractHttp2StreamMultiplexer streamMultiplexer;

    AbstractHttp2IOEventHandler(final AbstractHttp2StreamMultiplexer streamMultiplexer) {
        this.streamMultiplexer = Args.notNull(streamMultiplexer, "Stream multiplexer");
    }

    @Override
    public void connected(final IOSession session) {
        try {
            streamMultiplexer.onConnect(null);
        } catch (final Exception ex) {
            streamMultiplexer.onException(ex);
        }
    }

    @Override
    public void inputReady(final IOSession session) {
        try {
            streamMultiplexer.onInput();
        } catch (final Exception ex) {
            streamMultiplexer.onException(ex);
        }
    }

    @Override
    public void outputReady(final IOSession session) {
        try {
            streamMultiplexer.onOutput();
        } catch (final Exception ex) {
            streamMultiplexer.onException(ex);
        }
    }

    @Override
    public void timeout(final IOSession session) {
        try {
            streamMultiplexer.onTimeout();
        } catch (final Exception ex) {
            streamMultiplexer.onException(ex);
        }
    }

    @Override
    public void exception(final IOSession session, final Exception cause) {
        streamMultiplexer.onException(cause);
    }

    @Override
    public void disconnected(final IOSession session) {
        streamMultiplexer.onDisconnect();
    }

    @Override
    public void close() throws IOException {
        streamMultiplexer.close();
    }

    @Override
    public void shutdown(final ShutdownType shutdownType) {
        streamMultiplexer.shutdown(shutdownType);
    }

    @Override
    public boolean isOpen() {
        return streamMultiplexer.isOpen();
    }

    @Override
    public void setSocketTimeout(final int timeout) {
        streamMultiplexer.setSocketTimeout(timeout);
    }

    @Override
    public EndpointDetails getEndpointDetails() {
        return streamMultiplexer.getEndpointDetails();
    }

    @Override
    public int getSocketTimeout() {
        return streamMultiplexer.getSocketTimeout();
    }

    @Override
    public ProtocolVersion getProtocolVersion() {
        return streamMultiplexer.getProtocolVersion();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return streamMultiplexer.getRemoteAddress();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return streamMultiplexer.getLocalAddress();
    }

    @Override
    public String toString() {
        return streamMultiplexer.toString();
    }
}

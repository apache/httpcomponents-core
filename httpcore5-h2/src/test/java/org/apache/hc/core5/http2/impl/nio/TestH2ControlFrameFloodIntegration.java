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

import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.nio.support.ImmediateResponseExchangeHandler;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionListener;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Drives a real HTTP/2 server with a raw-socket peer that floods PING frames without reading the
 * socket. The server-side termination reason is observed through an {@link IOSessionListener} so
 * the test distinguishes the control-frame bound from any other cause of connection failure.
 */
class TestH2ControlFrameFloodIntegration {

    private static final int FLOOD_SIZE = 50_000;

    private HttpAsyncServer server;

    @AfterEach
    void cleanup() {
        if (server != null) {
            server.close(CloseMode.IMMEDIATE);
        }
    }

    private int startServer(final int maxControlFrames, final AtomicReference<Exception> firstException,
                            final CountDownLatch exceptionLatch) throws Exception {
        server = H2ServerBootstrap.bootstrap()
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                .setIOReactorConfig(IOReactorConfig.custom().setSndBufSize(8 * 1024).build())
                .setH2Config(H2Config.custom().setPushEnabled(false).setMaxControlFrames(maxControlFrames).build())
                .setIOSessionListener(new IOSessionListener() {

                    @Override
                    public void connected(final IOSession session) {
                    }

                    @Override
                    public void startTls(final IOSession session) {
                    }

                    @Override
                    public void inputReady(final IOSession session) {
                    }

                    @Override
                    public void outputReady(final IOSession session) {
                    }

                    @Override
                    public void timeout(final IOSession session) {
                    }

                    @Override
                    public void exception(final IOSession session, final Exception ex) {
                        if (firstException.compareAndSet(null, ex)) {
                            exceptionLatch.countDown();
                        }
                    }

                    @Override
                    public void disconnected(final IOSession session) {
                    }

                })
                .register("*", () -> new ImmediateResponseExchangeHandler(200, "OK"))
                .create();
        server.start();
        final ListenerEndpoint endpoint = server.listen(new InetSocketAddress("127.0.0.1", 0)).get();
        return ((InetSocketAddress) endpoint.getAddress()).getPort();
    }

    @Test
    @Timeout(30)
    void testPingFloodTerminatedWithEnhanceYourCalm() throws Exception {
        final AtomicReference<Exception> firstException = new AtomicReference<>();
        final CountDownLatch exceptionLatch = new CountDownLatch(1);
        final int port = startServer(1000, firstException, exceptionLatch);

        try (Socket socket = new Socket()) {
            socket.setReceiveBufferSize(4096);
            socket.connect(new InetSocketAddress("127.0.0.1", port));
            socket.setTcpNoDelay(true);

            final OutputStream out = new BufferedOutputStream(socket.getOutputStream(), 1 << 16);
            out.write("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            writeFrame(out, 4, 0, 0, null); // empty SETTINGS
            out.flush();

            // Flood PING frames and never read the socket: the acknowledgements cannot be flushed.
            final byte[] pingPayload = new byte[8];
            try {
                for (int i = 0; i < FLOOD_SIZE; i++) {
                    writeFrame(out, 6, 0, 0, pingPayload);
                    if ((i & 0x3FF) == 0) {
                        out.flush();
                    }
                }
                out.flush();
            } catch (final java.io.IOException ignore) {
                // The server closing the connection manifests as a write failure for the flooder.
            }

            // The connection is terminated for exactly one reason: the control-frame bound.
            Assertions.assertTrue(exceptionLatch.await(20, TimeUnit.SECONDS),
                    "server did not raise a connection exception for the flood");
            final Exception ex = firstException.get();
            Assertions.assertInstanceOf(H2ConnectionException.class, ex,
                    "unexpected server-side failure: " + ex);
            Assertions.assertEquals(H2Error.ENHANCE_YOUR_CALM.getCode(), ((H2ConnectionException) ex).getCode(),
                    "connection closed for a reason other than the control-frame limit: " + ex.getMessage());
        }
    }

    @Test
    @Timeout(30)
    void testFloodNotTerminatedWhenLimitDisabled() throws Exception {
        final AtomicReference<Exception> firstException = new AtomicReference<>();
        final CountDownLatch exceptionLatch = new CountDownLatch(1);
        final int port = startServer(0, firstException, exceptionLatch);

        try (Socket socket = new Socket()) {
            socket.setReceiveBufferSize(4096);
            socket.connect(new InetSocketAddress("127.0.0.1", port));
            socket.setTcpNoDelay(true);

            final OutputStream out = new BufferedOutputStream(socket.getOutputStream(), 1 << 16);
            out.write("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            writeFrame(out, 4, 0, 0, null); // empty SETTINGS
            out.flush();

            // Negative control: the same flood against a disabled limit must not trip ENHANCE_YOUR_CALM.
            final byte[] pingPayload = new byte[8];
            try {
                for (int i = 0; i < FLOOD_SIZE; i++) {
                    writeFrame(out, 6, 0, 0, pingPayload);
                    if ((i & 0x3FF) == 0) {
                        out.flush();
                    }
                }
                out.flush();
            } catch (final java.io.IOException ignore) {
            }

            // No control-frame exception is raised within a bounded observation window.
            final boolean raised = exceptionLatch.await(2, TimeUnit.SECONDS);
            if (raised) {
                final Exception ex = firstException.get();
                final boolean enhanceYourCalm = ex instanceof H2ConnectionException
                        && ((H2ConnectionException) ex).getCode() == H2Error.ENHANCE_YOUR_CALM.getCode();
                Assertions.assertFalse(enhanceYourCalm,
                        "the flood was terminated with ENHANCE_YOUR_CALM although the limit was disabled");
            }
        }
    }

    private static void writeFrame(final OutputStream out, final int type, final int flags,
                                   final int streamId, final byte[] payload) throws Exception {
        final int len = payload != null ? payload.length : 0;
        out.write((len >>> 16) & 0xff);
        out.write((len >>> 8) & 0xff);
        out.write(len & 0xff);
        out.write(type & 0xff);
        out.write(flags & 0xff);
        out.write((streamId >>> 24) & 0xff);
        out.write((streamId >>> 16) & 0xff);
        out.write((streamId >>> 8) & 0xff);
        out.write(streamId & 0xff);
        if (payload != null) {
            out.write(payload);
        }
    }

}

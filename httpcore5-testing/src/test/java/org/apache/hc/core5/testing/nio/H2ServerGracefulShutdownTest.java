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

package org.apache.hc.core5.testing.nio;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.http2.frame.DefaultFrameFactory;
import org.apache.hc.core5.http2.frame.FrameConsts;
import org.apache.hc.core5.http2.frame.FrameFactory;
import org.apache.hc.core5.http2.frame.FrameFlag;
import org.apache.hc.core5.http2.frame.FrameType;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.hpack.HPackEncoder;
import org.apache.hc.core5.http2.impl.io.FrameInputBuffer;
import org.apache.hc.core5.http2.impl.io.FrameOutputBuffer;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.testing.extension.nio.H2AsyncServerResource;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Two-phase graceful shutdown of an HTTP/2 server as seen by a frame level client.
 * Unlike a regular HTTP/2 endpoint the client acknowledges PING frames only when told to,
 * which makes it possible to play a peer that goes silent.
 */
class H2ServerGracefulShutdownTest {

    private static final FrameFactory FRAME_FACTORY = DefaultFrameFactory.INSTANCE;
    private static final byte[] PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII);

    // Far longer than the GOAWAY probe, so that only the probe can account for a timely shutdown.
    private static final Timeout SERVER_SOCKET_TIMEOUT = Timeout.ofSeconds(30);
    // Shorter than the server socket timeout. A GOAWAY caused by inactivity fails the read instead.
    private static final Timeout CLIENT_SOCKET_TIMEOUT = Timeout.ofSeconds(15);
    // A silent peer must be given time to acknowledge the PING.
    private static final long MIN_PROBE_MILLIS = 500;
    private static final long MAX_PROBE_MILLIS = 10000;
    // I/O reactors terminate the remaining sessions five seconds into a graceful close.
    private static final long REACTOR_GRACE_MILLIS = 5000;

    @RegisterExtension
    private final H2AsyncServerResource serverResource;

    H2ServerGracefulShutdownTest() {
        this.serverResource = new H2AsyncServerResource();
        this.serverResource.configure(bootstrap -> bootstrap
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(SERVER_SOCKET_TIMEOUT)
                        .build())
                .register("*", () -> new SingleLineResponseHandler("OK")));
    }

    @Test
    void testSilentPeerShutdown() throws Exception {
        final HttpAsyncServer server = serverResource.start();
        try (final RawH2Client client = new RawH2Client(listen(server))) {
            client.handshake();
            client.sendRequest(1, "GET", true);
            client.awaitResponse(1);

            final long startNanos = System.nanoTime();
            server.initiateShutdown();

            final List<ReceivedFrame> frames = new ArrayList<>();
            client.readUntilClosed(frames);
            assertSilentPeerShutdown(frames, startNanos, 1, MAX_PROBE_MILLIS);

            server.awaitShutdown(TimeValue.ofSeconds(60));
            final long shutdownMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            Assertions.assertTrue(shutdownMillis < MAX_PROBE_MILLIS, "Shutdown took " + shutdownMillis + " ms");
        }
    }

    @Test
    void testSilentPeerGracefulClose() throws Exception {
        final HttpAsyncServer server = serverResource.start();
        try (final RawH2Client client = new RawH2Client(listen(server))) {
            client.handshake();
            client.sendRequest(1, "GET", true);
            client.awaitResponse(1);

            final long startNanos = System.nanoTime();
            final Thread closer = new Thread(() -> server.close(CloseMode.GRACEFUL), "server-close");
            closer.start();

            final List<ReceivedFrame> frames = new ArrayList<>();
            client.readUntilClosed(frames);
            closer.join(MAX_PROBE_MILLIS);
            Assertions.assertFalse(closer.isAlive());
            // The final GOAWAY must beat the forced termination of the session.
            assertSilentPeerShutdown(frames, startNanos, 1, REACTOR_GRACE_MILLIS);
        }
    }

    @Test
    void testSilentPeerProbeTimeoutKeepsActiveStream() throws Exception {
        final HttpAsyncServer server = serverResource.start();
        try (final RawH2Client client = new RawH2Client(listen(server))) {
            client.handshake();
            // The message body is still pending, which keeps the stream open.
            client.sendRequest(1, "POST", false);
            client.sync();

            final long startNanos = System.nanoTime();
            server.initiateShutdown();

            final List<ReceivedFrame> frames = new ArrayList<>();
            client.readUntil(FrameType.GOAWAY, frames);
            client.readUntil(FrameType.GOAWAY, frames);
            assertSilentPeerShutdown(frames, startNanos, 1, MAX_PROBE_MILLIS);

            // Probe expiry must not have failed the stream accepted before the shutdown.
            client.write(FRAME_FACTORY.createData(1, ByteBuffer.wrap(new byte[] {'x'}), true));
            frames.clear();
            client.readUntilClosed(frames);
            assertStreamCompleted(frames, 1);
            for (final ReceivedFrame frame : frames) {
                Assertions.assertFalse(frame.isType(FrameType.GOAWAY), "Unexpected GOAWAY");
            }
            server.awaitShutdown(TimeValue.ofSeconds(60));
        }
    }

    @Test
    void testRequestInFlightDuringShutdown() throws Exception {
        final HttpAsyncServer server = serverResource.start();
        try (final RawH2Client client = new RawH2Client(listen(server))) {
            client.handshake();
            client.sendRequest(1, "GET", true);
            client.awaitResponse(1);

            server.initiateShutdown();

            final List<ReceivedFrame> frames = new ArrayList<>();
            final ReceivedFrame initialGoAway = client.readUntil(FrameType.GOAWAY, frames);
            Assertions.assertEquals(Integer.MAX_VALUE, initialGoAway.goAwayLastStreamId());
            final ReceivedFrame ping = client.readUntil(FrameType.PING, frames);
            Assertions.assertFalse(ping.isFlagSet(FrameFlag.ACK));

            // A request that was on its way when the initial GOAWAY was sent, then the PING ACK.
            client.sendRequest(3, "GET", true);
            client.write(FRAME_FACTORY.createPingAck(ByteBuffer.wrap(ping.payload)));

            frames.clear();
            client.readUntilClosed(frames);
            assertStreamCompleted(frames, 3);
            final List<ReceivedFrame> goAways = new ArrayList<>();
            for (final ReceivedFrame frame : frames) {
                if (frame.isType(FrameType.GOAWAY)) {
                    goAways.add(frame);
                }
            }
            Assertions.assertEquals(1, goAways.size());
            assertFinalGoAway(goAways.get(0), 3);
            server.awaitShutdown(TimeValue.ofSeconds(60));
        }
    }

    private static InetSocketAddress listen(final HttpAsyncServer server) throws Exception {
        final Future<ListenerEndpoint> future = server.listen(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), URIScheme.HTTP);
        return (InetSocketAddress) future.get().getAddress();
    }

    private static void assertSilentPeerShutdown(
            final List<ReceivedFrame> frames,
            final long startNanos,
            final int lastStreamId,
            final long maxProbeMillis) {
        final List<ReceivedFrame> control = new ArrayList<>();
        for (final ReceivedFrame frame : frames) {
            if (frame.isType(FrameType.GOAWAY) || frame.isType(FrameType.PING)) {
                control.add(frame);
            }
        }
        Assertions.assertEquals(3, control.size(), "Expected GOAWAY, PING, GOAWAY");
        final ReceivedFrame initialGoAway = control.get(0);
        Assertions.assertTrue(initialGoAway.isType(FrameType.GOAWAY));
        Assertions.assertEquals(Integer.MAX_VALUE, initialGoAway.goAwayLastStreamId());
        Assertions.assertEquals(H2Error.NO_ERROR.getCode(), initialGoAway.goAwayErrorCode());
        final ReceivedFrame ping = control.get(1);
        Assertions.assertTrue(ping.isType(FrameType.PING));
        Assertions.assertFalse(ping.isFlagSet(FrameFlag.ACK));
        final ReceivedFrame finalGoAway = control.get(2);
        assertFinalGoAway(finalGoAway, lastStreamId);
        final long probeMillis = TimeUnit.NANOSECONDS.toMillis(finalGoAway.timeNanos - startNanos);
        Assertions.assertTrue(probeMillis >= MIN_PROBE_MILLIS, "Final GOAWAY after " + probeMillis + " ms");
        Assertions.assertTrue(probeMillis < maxProbeMillis, "Final GOAWAY after " + probeMillis + " ms");
    }

    private static void assertFinalGoAway(final ReceivedFrame goAway, final int lastStreamId) {
        Assertions.assertTrue(goAway.isType(FrameType.GOAWAY));
        Assertions.assertEquals(lastStreamId, goAway.goAwayLastStreamId());
        Assertions.assertEquals(H2Error.NO_ERROR.getCode(), goAway.goAwayErrorCode());
        // An inactivity timeout reports NO_ERROR as well. Only the debug data tells them apart.
        Assertions.assertEquals("Graceful shutdown", goAway.goAwayDebugData());
    }

    private static void assertStreamCompleted(final List<ReceivedFrame> frames, final int streamId) {
        boolean completed = false;
        for (final ReceivedFrame frame : frames) {
            if (frame.streamId == streamId) {
                Assertions.assertFalse(frame.isType(FrameType.RST_STREAM), "Stream " + streamId + " has been reset");
                if (frame.isFlagSet(FrameFlag.END_STREAM)
                        && (frame.isType(FrameType.HEADERS) || frame.isType(FrameType.DATA))) {
                    completed = true;
                }
            }
        }
        Assertions.assertTrue(completed, "Stream " + streamId + " has not been completed");
    }

    private static final class ReceivedFrame {

        final int type;
        final int flags;
        final int streamId;
        final byte[] payload;
        final long timeNanos;

        ReceivedFrame(final int type, final int flags, final int streamId, final byte[] payload, final long timeNanos) {
            this.type = type;
            this.flags = flags;
            this.streamId = streamId;
            this.payload = payload;
            this.timeNanos = timeNanos;
        }

        boolean isType(final FrameType frameType) {
            return type == frameType.getValue();
        }

        boolean isFlagSet(final FrameFlag flag) {
            return (flags & flag.getValue()) != 0;
        }

        int goAwayLastStreamId() {
            return ByteBuffer.wrap(payload).getInt() & Integer.MAX_VALUE;
        }

        int goAwayErrorCode() {
            return ByteBuffer.wrap(payload).getInt(4);
        }

        String goAwayDebugData() {
            return new String(payload, 8, payload.length - 8, StandardCharsets.US_ASCII);
        }

    }

    private static final class RawH2Client implements Closeable {

        private final Socket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private final FrameInputBuffer inputBuffer;
        private final FrameOutputBuffer outputBuffer;
        private final HPackEncoder hPackEncoder;

        RawH2Client(final InetSocketAddress address) throws IOException {
            this.socket = new Socket();
            this.socket.setSoTimeout(CLIENT_SOCKET_TIMEOUT.toMillisecondsIntBound());
            this.socket.setTcpNoDelay(true);
            this.socket.connect(address, CLIENT_SOCKET_TIMEOUT.toMillisecondsIntBound());
            this.inputStream = socket.getInputStream();
            this.outputStream = socket.getOutputStream();
            this.inputBuffer = new FrameInputBuffer(FrameConsts.MIN_FRAME_SIZE);
            this.outputBuffer = new FrameOutputBuffer(FrameConsts.MIN_FRAME_SIZE);
            this.hPackEncoder = new HPackEncoder(H2Config.INIT.getHeaderTableSize(), StandardCharsets.US_ASCII);
        }

        void write(final RawFrame frame) throws IOException {
            outputBuffer.write(frame, outputStream);
            outputStream.flush();
        }

        ReceivedFrame read() throws IOException {
            final RawFrame frame = inputBuffer.read(inputStream);
            // The payload is a view of the input buffer and does not survive the next read.
            final ByteBuffer payload = frame.getPayload();
            final byte[] content = new byte[payload != null ? payload.remaining() : 0];
            if (payload != null) {
                payload.get(content);
            }
            return new ReceivedFrame(frame.getType(), frame.getFlags(), frame.getStreamId(), content, System.nanoTime());
        }

        void handshake() throws IOException {
            outputStream.write(PREFACE);
            write(FRAME_FACTORY.createSettings());
            boolean settingsReceived = false;
            boolean settingsAcknowledged = false;
            while (!settingsReceived || !settingsAcknowledged) {
                final ReceivedFrame frame = read();
                if (frame.isType(FrameType.SETTINGS)) {
                    if (frame.isFlagSet(FrameFlag.ACK)) {
                        settingsAcknowledged = true;
                    } else {
                        write(FRAME_FACTORY.createSettingsAck());
                        settingsReceived = true;
                    }
                }
            }
        }

        void sendRequest(final int streamId, final String method, final boolean endStream) throws IOException {
            final ByteArrayBuffer buffer = new ByteArrayBuffer(128);
            hPackEncoder.encodeHeaders(buffer, Arrays.asList(
                    new BasicHeader(":method", method),
                    new BasicHeader(":scheme", "http"),
                    new BasicHeader(":authority", "localhost:" + socket.getPort()),
                    new BasicHeader(":path", "/")), false);
            write(FRAME_FACTORY.createHeaders(
                    streamId, ByteBuffer.wrap(buffer.array(), 0, buffer.length()), true, endStream));
        }

        void awaitResponse(final int streamId) throws IOException {
            for (;;) {
                final ReceivedFrame frame = read();
                if (frame.streamId == streamId && frame.isFlagSet(FrameFlag.END_STREAM)
                        && (frame.isType(FrameType.HEADERS) || frame.isType(FrameType.DATA))) {
                    return;
                }
            }
        }

        // A PING round trip. Its ACK proves the server has processed everything sent before it.
        void sync() throws IOException {
            write(FRAME_FACTORY.createPing(ByteBuffer.wrap(new byte[8])));
            for (;;) {
                final ReceivedFrame frame = read();
                if (frame.isType(FrameType.PING) && frame.isFlagSet(FrameFlag.ACK)) {
                    return;
                }
            }
        }

        ReceivedFrame readUntil(final FrameType frameType, final List<ReceivedFrame> frames) throws IOException {
            for (;;) {
                final ReceivedFrame frame = read();
                frames.add(frame);
                if (frame.isType(frameType)) {
                    return frame;
                }
            }
        }

        void readUntilClosed(final List<ReceivedFrame> frames) throws IOException {
            for (;;) {
                final ReceivedFrame frame;
                try {
                    frame = read();
                } catch (final ConnectionClosedException | SocketException ex) {
                    return;
                }
                frames.add(frame);
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }

    }

}

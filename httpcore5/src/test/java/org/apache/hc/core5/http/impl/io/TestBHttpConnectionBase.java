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
package org.apache.hc.core5.http.impl.io;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestBHttpConnectionBase {

    @Mock
    Socket socket;

    BHttpConnectionBase conn;

    @BeforeEach
    void prepareMocks() {
        conn = new BHttpConnectionBase(Http1Config.DEFAULT, null, null);
    }

    @Test
    void testBasics() {
        Assertions.assertThat(conn.isOpen()).isFalse();
        Assertions.assertThat(conn.getLocalAddress()).isNull();
        Assertions.assertThat(conn.getRemoteAddress()).isNull();
        Assertions.assertThat(conn.toString()).isEqualTo("[Not bound]");
    }

    @Test
    void testSocketBind() throws Exception {
        final InetAddress localAddress = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        final int localPort = 8888;
        final InetAddress remoteAddress = InetAddress.getByAddress(new byte[] {10, 0, 0, 2});
        final int remotePort = 80;
        final InetSocketAddress localSockAddress = new InetSocketAddress(localAddress, localPort);
        final InetSocketAddress remoteSockAddress = new InetSocketAddress(remoteAddress, remotePort);
        Mockito.when(socket.getLocalSocketAddress()).thenReturn(localSockAddress);
        Mockito.when(socket.getRemoteSocketAddress()).thenReturn(remoteSockAddress);
        conn.bind(socket);

        Assertions.assertThat(conn.toString()).isEqualTo("127.0.0.1:8888<->10.0.0.2:80");
        Assertions.assertThat(conn.isOpen()).isTrue();

        Assertions.assertThat(conn.getLocalAddress()).isEqualTo(new InetSocketAddress(
                InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), 8888));
        Assertions.assertThat(conn.getRemoteAddress()).isEqualTo(new InetSocketAddress(
                InetAddress.getByAddress(new byte[]{10, 0, 0, 2}), 80));
    }

    @Test
    void testConnectionClose() throws Exception {
        final OutputStream outStream = Mockito.mock(OutputStream.class);
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);
        conn.ensureOpen();
        conn.outbuffer.write(0, outStream);

        Assertions.assertThat(conn.isOpen()).isTrue();

        conn.close();

        Assertions.assertThat(conn.isOpen()).isFalse();

        Mockito.verify(outStream, Mockito.times(1)).write(
                ArgumentMatchers.any(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt());
        Mockito.verify(socket, Mockito.times(1)).close();

        conn.close();
        Mockito.verify(socket, Mockito.times(1)).close();
        Mockito.verify(outStream, Mockito.times(1)).write(
                ArgumentMatchers.any(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt());
    }

    @Test
    void testConnectionShutdown() throws Exception {
        final OutputStream outStream = Mockito.mock(OutputStream.class);

        conn.bind(socket);
        conn.ensureOpen();
        conn.outbuffer.write(0, outStream);

        Assertions.assertThat(conn.isOpen()).isTrue();

        conn.close(CloseMode.GRACEFUL);

        Assertions.assertThat(conn.isOpen()).isFalse();

        Mockito.verify(outStream, Mockito.never()).write(
                ArgumentMatchers.any(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt());
        Mockito.verify(socket, Mockito.never()).shutdownInput();
        Mockito.verify(socket, Mockito.never()).shutdownOutput();
        Mockito.verify(socket, Mockito.times(1)).close();

        conn.close();
        Mockito.verify(socket, Mockito.times(1)).close();

        conn.close(CloseMode.GRACEFUL);
        Mockito.verify(socket, Mockito.times(1)).close();
    }

    @Test
    void testCreateEntityLengthDelimited() throws Exception {
        final InputStream inStream = Mockito.mock(InputStream.class);
        final ClassicHttpResponse message = new BasicClassicHttpResponse(200, "OK");
        message.addHeader("Content-Length", "10");
        message.addHeader("Content-Type", "stuff");
        message.addHeader("Content-Encoding", "chunked");
        final HttpEntity entity = conn.createIncomingEntity(message, conn.inBuffer, inStream, 10);
        Assertions.assertThat(entity).isNotNull();
        Assertions.assertThat(entity.isChunked()).isFalse();
        Assertions.assertThat(entity.getContentLength()).isEqualTo(10);
        Assertions.assertThat(entity.getContentType()).isEqualTo("stuff");
        Assertions.assertThat(entity.getContentEncoding()).isEqualTo("chunked");
        final InputStream content = entity.getContent();
        Assertions.assertThat(content).isNotNull();
        Assertions.assertThat(content).isInstanceOf(ContentLengthInputStream.class);
    }

    @Test
    void testCreateEntityInputChunked() throws Exception {
        final InputStream inStream = Mockito.mock(InputStream.class);
        final ClassicHttpResponse message = new BasicClassicHttpResponse(200, "OK");
        final HttpEntity entity = conn.createIncomingEntity(message, conn.inBuffer, inStream, ContentLengthStrategy.CHUNKED);
        Assertions.assertThat(entity).isNotNull();
        Assertions.assertThat(entity.isChunked()).isTrue();
        Assertions.assertThat(entity.getContentLength()).isEqualTo(-1);
        final InputStream content = entity.getContent();
        Assertions.assertThat(content).isNotNull();
        Assertions.assertThat(content).isInstanceOf(ChunkedInputStream.class);
    }

    @Test
    void testCreateEntityInputUndefined() throws Exception {
        final InputStream inStream = Mockito.mock(InputStream.class);
        final ClassicHttpResponse message = new BasicClassicHttpResponse(200, "OK");
        final HttpEntity entity = conn.createIncomingEntity(message, conn.inBuffer, inStream, ContentLengthStrategy.UNDEFINED);
        Assertions.assertThat(entity).isNotNull();
        Assertions.assertThat(entity.isChunked()).isFalse();
        Assertions.assertThat(entity.getContentLength()).isEqualTo(-1);
        final InputStream content = entity.getContent();
        Assertions.assertThat(content).isNotNull();
        Assertions.assertThat(content).isInstanceOf(IdentityInputStream.class);
    }

    @Test
    void testSetSocketTimeout() throws Exception {
        conn.bind(socket);

        conn.setSocketTimeout(Timeout.ofMilliseconds(123));

        Mockito.verify(socket, Mockito.times(1)).setSoTimeout(123);
    }

    @Test
    void testSetSocketTimeoutException() throws Exception {
        conn.bind(socket);

        Mockito.doThrow(new SocketException()).when(socket).setSoTimeout(ArgumentMatchers.anyInt());

        conn.setSocketTimeout(Timeout.ofMilliseconds(123));

        Mockito.verify(socket, Mockito.times(1)).setSoTimeout(123);
    }

    @Test
    void testGetSocketTimeout() throws Exception {
        Assertions.assertThat(conn.getSocketTimeout()).isEqualTo(Timeout.INFINITE);

        Mockito.when(socket.getSoTimeout()).thenReturn(345);
        conn.bind(socket);

        Assertions.assertThat(conn.getSocketTimeout()).isEqualTo(Timeout.ofMilliseconds(345));
    }

    @Test
    void testGetSocketTimeoutException() throws Exception {
        Assertions.assertThat(conn.getSocketTimeout()).isEqualTo(Timeout.INFINITE);

        Mockito.when(socket.getSoTimeout()).thenThrow(new SocketException());
        conn.bind(socket);

        Assertions.assertThat(conn.getSocketTimeout()).isEqualTo(Timeout.INFINITE);
    }

    @Test
    void testAwaitInputInBuffer() throws Exception {
        final ByteArrayInputStream inStream = new ByteArrayInputStream(
                new byte[] {1, 2, 3, 4, 5});
        conn.bind(socket);
        conn.ensureOpen();
        conn.inBuffer.read(inStream);

        Assertions.assertThat(conn.awaitInput(Timeout.ofMilliseconds(432))).isTrue();

        Mockito.verify(socket, Mockito.never()).setSoTimeout(ArgumentMatchers.anyInt());
    }

    @Test
    void testAwaitInputInSocket() throws Exception {
        final ByteArrayInputStream inStream = new ByteArrayInputStream(
                new byte[] {1, 2, 3, 4, 5});
        Mockito.when(socket.getInputStream()).thenReturn(inStream);
        Mockito.when(socket.getSoTimeout()).thenReturn(345);

        conn.bind(socket);
        conn.ensureOpen();

        Assertions.assertThat(conn.awaitInput(Timeout.ofMilliseconds(432))).isTrue();

        Mockito.verify(socket, Mockito.times(1)).setSoTimeout(432);
        Mockito.verify(socket, Mockito.times(1)).setSoTimeout(345);
        }

    @Test
    void testAwaitInputNoData() throws Exception {
        final InputStream inStream = Mockito.mock(InputStream.class);
        Mockito.when(socket.getInputStream()).thenReturn(inStream);
        Mockito.when(inStream.read(ArgumentMatchers.any(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt()))
            .thenReturn(-1);

        conn.bind(socket);
        conn.ensureOpen();

        Assertions.assertThat(conn.awaitInput(Timeout.ofMilliseconds(432))).isFalse();
    }

    @Test
    void testStaleWhenClosed() throws Exception {
        final OutputStream outStream = Mockito.mock(OutputStream.class);

        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);
        conn.ensureOpen();
        conn.close();
        Assertions.assertThat(conn.isStale()).isTrue();
    }

    @Test
    void testNotStaleWhenHasData() throws Exception {
        final ByteArrayInputStream inStream = new ByteArrayInputStream(
                new byte[] {1, 2, 3, 4, 5});
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);
        conn.ensureOpen();

        Assertions.assertThat(conn.isStale()).isFalse();
    }

    @Test
    void testStaleWhenEndOfStream() throws Exception {
        final InputStream inStream = Mockito.mock(InputStream.class);
        Mockito.when(socket.getInputStream()).thenReturn(inStream);
        Mockito.when(inStream.read(ArgumentMatchers.any(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt()))
            .thenReturn(-1);

        conn.bind(socket);
        conn.ensureOpen();

        Assertions.assertThat(conn.isStale()).isTrue();
    }

    @Test
    void testNotStaleWhenTimeout() throws Exception {
        final InputStream inStream = Mockito.mock(InputStream.class);
        Mockito.when(socket.getInputStream()).thenReturn(inStream);
        Mockito.when(inStream.read(ArgumentMatchers.any(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt()))
            .thenThrow(new SocketTimeoutException());

        conn.bind(socket);
        conn.ensureOpen();

        Assertions.assertThat(conn.isStale()).isFalse();
    }

    @Test
    void testStaleWhenIOError() throws Exception {
        final InputStream inStream = Mockito.mock(InputStream.class);
        Mockito.when(socket.getInputStream()).thenReturn(inStream);
        Mockito.when(inStream.read(ArgumentMatchers.any(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt()))
            .thenThrow(new SocketException());

        conn.bind(socket);
        conn.ensureOpen();

        Assertions.assertThat(conn.isStale()).isTrue();
    }

}

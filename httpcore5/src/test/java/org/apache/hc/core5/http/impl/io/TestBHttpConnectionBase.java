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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TestBHttpConnectionBase {

    @Mock
    private Socket socket;

    private BHttpConnectionBase conn;

    @Before
    public void prepareMocks() {
        conn = new BHttpConnectionBase(Http1Config.DEFAULT, null, null);
    }

    @Test
    public void testBasics() throws Exception {
        Assert.assertFalse(conn.isOpen());
        Assert.assertNull(conn.getLocalAddress());
        Assert.assertNull(conn.getRemoteAddress());
        Assert.assertEquals("[Not bound]", conn.toString());
    }

    @Test
    public void testSocketBind() throws Exception {
        final InetAddress localAddress = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        final int localPort = 8888;
        final InetAddress remoteAddress = InetAddress.getByAddress(new byte[] {10, 0, 0, 2});
        final int remotePort = 80;
        final InetSocketAddress localSockAddress = new InetSocketAddress(localAddress, localPort);
        final InetSocketAddress remoteSockAddress = new InetSocketAddress(remoteAddress, remotePort);
        Mockito.when(socket.getLocalSocketAddress()).thenReturn(localSockAddress);
        Mockito.when(socket.getRemoteSocketAddress()).thenReturn(remoteSockAddress);
        conn.bind(socket);

        Assert.assertEquals("127.0.0.1:8888<->10.0.0.2:80", conn.toString());
        Assert.assertTrue(conn.isOpen());

        Assert.assertEquals(new InetSocketAddress(
                InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 8888), conn.getLocalAddress());
        Assert.assertEquals(new InetSocketAddress(
                InetAddress.getByAddress(new byte[] {10, 0, 0, 2}), 80), conn.getRemoteAddress());
    }

    @Test
    public void testConnectionClose() throws Exception {
        final OutputStream outStream = Mockito.mock(OutputStream.class);
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);
        conn.ensureOpen();
        conn.outbuffer.write(0, outStream);

        Assert.assertTrue(conn.isOpen());

        conn.close();

        Assert.assertFalse(conn.isOpen());

        Mockito.verify(outStream, Mockito.times(1)).write(
                ArgumentMatchers.any(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt());
        Mockito.verify(socket, Mockito.times(1)).close();

        conn.close();
        Mockito.verify(socket, Mockito.times(1)).close();
        Mockito.verify(outStream, Mockito.times(1)).write(
                ArgumentMatchers.any(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt());
    }

    @Test
    public void testConnectionShutdown() throws Exception {
        final OutputStream outStream = Mockito.mock(OutputStream.class);

        conn.bind(socket);
        conn.ensureOpen();
        conn.outbuffer.write(0, outStream);

        Assert.assertTrue(conn.isOpen());

        conn.close(CloseMode.GRACEFUL);

        Assert.assertFalse(conn.isOpen());

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
    public void testCreateEntityLengthDelimited() throws Exception {
        final InputStream inStream = Mockito.mock(InputStream.class);
        final ClassicHttpResponse message = new BasicClassicHttpResponse(200, "OK");
        message.addHeader("Content-Length", "10");
        message.addHeader("Content-Type", "stuff");
        message.addHeader("Content-Encoding", "chunked");
        final HttpEntity entity = conn.createIncomingEntity(message, conn.inBuffer, inStream, 10);
        Assert.assertNotNull(entity);
        Assert.assertFalse(entity.isChunked());
        Assert.assertEquals(10, entity.getContentLength());
        Assert.assertEquals("stuff", entity.getContentType());
        Assert.assertEquals("chunked", entity.getContentEncoding());
        final InputStream content = entity.getContent();
        Assert.assertNotNull(content);
        Assert.assertTrue((content instanceof ContentLengthInputStream));
    }

    @Test
    public void testCreateEntityInputChunked() throws Exception {
        final InputStream inStream = Mockito.mock(InputStream.class);
        final ClassicHttpResponse message = new BasicClassicHttpResponse(200, "OK");
        final HttpEntity entity = conn.createIncomingEntity(message, conn.inBuffer, inStream, ContentLengthStrategy.CHUNKED);
        Assert.assertNotNull(entity);
        Assert.assertTrue(entity.isChunked());
        Assert.assertEquals(-1, entity.getContentLength());
        final InputStream content = entity.getContent();
        Assert.assertNotNull(content);
        Assert.assertTrue((content instanceof ChunkedInputStream));
    }

    @Test
    public void testCreateEntityInputUndefined() throws Exception {
        final InputStream inStream = Mockito.mock(InputStream.class);
        final ClassicHttpResponse message = new BasicClassicHttpResponse(200, "OK");
        final HttpEntity entity = conn.createIncomingEntity(message, conn.inBuffer, inStream, ContentLengthStrategy.UNDEFINED);
        Assert.assertNotNull(entity);
        Assert.assertFalse(entity.isChunked());
        Assert.assertEquals(-1, entity.getContentLength());
        final InputStream content = entity.getContent();
        Assert.assertNotNull(content);
        Assert.assertTrue((content instanceof IdentityInputStream));
    }

    @Test
    public void testSetSocketTimeout() throws Exception {
        conn.bind(socket);

        conn.setSocketTimeout(Timeout.ofMilliseconds(123));

        Mockito.verify(socket, Mockito.times(1)).setSoTimeout(123);
    }

    @Test
    public void testSetSocketTimeoutException() throws Exception {
        conn.bind(socket);

        Mockito.doThrow(new SocketException()).when(socket).setSoTimeout(ArgumentMatchers.anyInt());

        conn.setSocketTimeout(Timeout.ofMilliseconds(123));

        Mockito.verify(socket, Mockito.times(1)).setSoTimeout(123);
    }

    @Test
    public void testGetSocketTimeout() throws Exception {
        Assert.assertEquals(Timeout.DISABLED, conn.getSocketTimeout());

        Mockito.when(socket.getSoTimeout()).thenReturn(345);
        conn.bind(socket);

        Assert.assertEquals(Timeout.ofMilliseconds(345), conn.getSocketTimeout());
    }

    @Test
    public void testGetSocketTimeoutException() throws Exception {
        Assert.assertEquals(Timeout.DISABLED, conn.getSocketTimeout());

        Mockito.when(socket.getSoTimeout()).thenThrow(new SocketException());
        conn.bind(socket);

        Assert.assertEquals(Timeout.DISABLED, conn.getSocketTimeout());
    }

    @Test
    public void testAwaitInputInBuffer() throws Exception {
        final ByteArrayInputStream inStream = new ByteArrayInputStream(
                new byte[] {1, 2, 3, 4, 5});
        conn.bind(socket);
        conn.ensureOpen();
        conn.inBuffer.read(inStream);

        Assert.assertTrue(conn.awaitInput(Timeout.ofMilliseconds(432)));

        Mockito.verify(socket, Mockito.never()).setSoTimeout(ArgumentMatchers.anyInt());
    }

    @Test
    public void testAwaitInputInSocket() throws Exception {
        final ByteArrayInputStream inStream = new ByteArrayInputStream(
                new byte[] {1, 2, 3, 4, 5});
        Mockito.when(socket.getInputStream()).thenReturn(inStream);
        Mockito.when(socket.getSoTimeout()).thenReturn(345);

        conn.bind(socket);
        conn.ensureOpen();

        Assert.assertTrue(conn.awaitInput(Timeout.ofMilliseconds(432)));

        Mockito.verify(socket, Mockito.times(1)).setSoTimeout(432);
        Mockito.verify(socket, Mockito.times(1)).setSoTimeout(345);
        }

    @Test
    public void testAwaitInputNoData() throws Exception {
        final InputStream inStream = Mockito.mock(InputStream.class);
        Mockito.when(socket.getInputStream()).thenReturn(inStream);
        Mockito.when(inStream.read(ArgumentMatchers.any(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt()))
            .thenReturn(-1);

        conn.bind(socket);
        conn.ensureOpen();

        Assert.assertFalse(conn.awaitInput(Timeout.ofMilliseconds(432)));
    }

    @Test
    public void testStaleWhenClosed() throws Exception {
        final OutputStream outStream = Mockito.mock(OutputStream.class);

        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);
        conn.ensureOpen();
        conn.close();
        Assert.assertTrue(conn.isStale());
    }

    @Test
    public void testNotStaleWhenHasData() throws Exception {
        final ByteArrayInputStream inStream = new ByteArrayInputStream(
                new byte[] {1, 2, 3, 4, 5});
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);
        conn.ensureOpen();

        Assert.assertFalse(conn.isStale());
    }

    @Test
    public void testStaleWhenEndOfStream() throws Exception {
        final InputStream inStream = Mockito.mock(InputStream.class);
        Mockito.when(socket.getInputStream()).thenReturn(inStream);
        Mockito.when(inStream.read(ArgumentMatchers.any(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt()))
            .thenReturn(-1);

        conn.bind(socket);
        conn.ensureOpen();

        Assert.assertTrue(conn.isStale());
    }

    @Test
    public void testNotStaleWhenTimeout() throws Exception {
        final InputStream inStream = Mockito.mock(InputStream.class);
        Mockito.when(socket.getInputStream()).thenReturn(inStream);
        Mockito.when(inStream.read(ArgumentMatchers.any(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt()))
            .thenThrow(new SocketTimeoutException());

        conn.bind(socket);
        conn.ensureOpen();

        Assert.assertFalse(conn.isStale());
    }

    @Test
    public void testStaleWhenIOError() throws Exception {
        final InputStream inStream = Mockito.mock(InputStream.class);
        Mockito.when(socket.getInputStream()).thenReturn(inStream);
        Mockito.when(inStream.read(ArgumentMatchers.any(), ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt()))
            .thenThrow(new SocketException());

        conn.bind(socket);
        conn.ensureOpen();

        Assert.assertTrue(conn.isStale());
    }

}

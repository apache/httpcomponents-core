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
package org.apache.http.impl;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.config.MessageConstraints;
import org.apache.http.impl.entity.LaxContentLengthStrategy;
import org.apache.http.impl.entity.StrictContentLengthStrategy;
import org.apache.http.impl.io.ChunkedInputStream;
import org.apache.http.impl.io.ChunkedOutputStream;
import org.apache.http.impl.io.ContentLengthInputStream;
import org.apache.http.impl.io.ContentLengthOutputStream;
import org.apache.http.impl.io.IdentityInputStream;
import org.apache.http.impl.io.IdentityOutputStream;
import org.apache.http.message.BasicHttpResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class TestBHttpConnectionBase {

    @Mock
    private Socket socket;

    private BHttpConnectionBase conn;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        conn = new BHttpConnectionBase(1024, 1024,
            null, null,
            MessageConstraints.DEFAULT,
            LaxContentLengthStrategy.INSTANCE,
            StrictContentLengthStrategy.INSTANCE);
    }

    @Test
    public void testBasics() throws Exception {
        Assert.assertFalse(conn.isOpen());
        Assert.assertEquals(-1, conn.getLocalPort());
        Assert.assertEquals(-1, conn.getRemotePort());
        Assert.assertEquals(null, conn.getLocalAddress());
        Assert.assertEquals(null, conn.getRemoteAddress());
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
        Mockito.when(socket.getLocalAddress()).thenReturn(localAddress);
        Mockito.when(socket.getLocalPort()).thenReturn(localPort);
        Mockito.when(socket.getInetAddress()).thenReturn(remoteAddress);
        Mockito.when(socket.getPort()).thenReturn(remotePort);
        conn.bind(socket);

        Assert.assertEquals("127.0.0.1:8888<->10.0.0.2:80", conn.toString());
        Assert.assertTrue(conn.isOpen());
        Assert.assertEquals(8888, conn.getLocalPort());
        Assert.assertEquals(80, conn.getRemotePort());
        Assert.assertEquals(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), conn.getLocalAddress());
        Assert.assertEquals(InetAddress.getByAddress(new byte[] {10, 0, 0, 2}), conn.getRemoteAddress());
    }

    @Test
    public void testConnectionClose() throws Exception {
        final InputStream instream = Mockito.mock(InputStream.class);
        final OutputStream outstream = Mockito.mock(OutputStream.class);

        Mockito.when(socket.getInputStream()).thenReturn(instream);
        Mockito.when(socket.getOutputStream()).thenReturn(outstream);

        conn.bind(socket);
        conn.ensureOpen();
        conn.getSessionOutputBuffer().write(0);

        Assert.assertTrue(conn.isOpen());

        conn.close();

        Assert.assertFalse(conn.isOpen());

        Mockito.verify(outstream, Mockito.times(1)).write(
                Mockito.<byte []>any(), Mockito.anyInt(), Mockito.anyInt());
        Mockito.verify(socket, Mockito.times(1)).shutdownInput();
        Mockito.verify(socket, Mockito.times(1)).shutdownOutput();
        Mockito.verify(socket, Mockito.times(1)).close();

        conn.close();
        Mockito.verify(socket, Mockito.times(1)).close();
        Mockito.verify(outstream, Mockito.times(1)).write(
                Mockito.<byte []>any(), Mockito.anyInt(), Mockito.anyInt());
    }

    @Test
    public void testConnectionShutdown() throws Exception {
        final InputStream instream = Mockito.mock(InputStream.class);
        final OutputStream outstream = Mockito.mock(OutputStream.class);
        Mockito.when(socket.getInputStream()).thenReturn(instream);
        Mockito.when(socket.getOutputStream()).thenReturn(outstream);

        conn.bind(socket);
        conn.ensureOpen();
        conn.getSessionOutputBuffer().write(0);

        Assert.assertTrue(conn.isOpen());

        conn.shutdown();

        Assert.assertFalse(conn.isOpen());

        Mockito.verify(outstream, Mockito.never()).write(
                Mockito.<byte []>any(), Mockito.anyInt(), Mockito.anyInt());
        Mockito.verify(socket, Mockito.never()).shutdownInput();
        Mockito.verify(socket, Mockito.never()).shutdownOutput();
        Mockito.verify(socket, Mockito.times(1)).close();

        conn.close();
        Mockito.verify(socket, Mockito.times(1)).close();

        conn.shutdown();
        Mockito.verify(socket, Mockito.times(1)).close();
    }

    @Test
    public void testPrepareInputLengthDelimited() throws Exception {
        final HttpResponse message = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        message.addHeader("Content-Length", "10");
        message.addHeader("Content-Type", "stuff");
        message.addHeader("Content-Encoding", "identity");
        final HttpEntity entity = conn.prepareInput(message);
        Assert.assertNotNull(entity);
        Assert.assertFalse(entity.isChunked());
        Assert.assertEquals(10, entity.getContentLength());
        final Header ct = entity.getContentType();
        Assert.assertNotNull(ct);
        Assert.assertEquals("stuff", ct.getValue());
        final Header ce = entity.getContentEncoding();
        Assert.assertNotNull(ce);
        Assert.assertEquals("identity", ce.getValue());
        final InputStream instream = entity.getContent();
        Assert.assertNotNull(instream);
        Assert.assertTrue((instream instanceof ContentLengthInputStream));
    }

    @Test
    public void testPrepareInputChunked() throws Exception {
        final HttpResponse message = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        message.addHeader("Transfer-Encoding", "chunked");
        final HttpEntity entity = conn.prepareInput(message);
        Assert.assertNotNull(entity);
        Assert.assertTrue(entity.isChunked());
        Assert.assertEquals(-1, entity.getContentLength());
        final InputStream instream = entity.getContent();
        Assert.assertNotNull(instream);
        Assert.assertTrue((instream instanceof ChunkedInputStream));
    }

    @Test
    public void testPrepareInputIdentity() throws Exception {
        final HttpResponse message = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        final HttpEntity entity = conn.prepareInput(message);
        Assert.assertNotNull(entity);
        Assert.assertFalse(entity.isChunked());
        Assert.assertEquals(-1, entity.getContentLength());
        final InputStream instream = entity.getContent();
        Assert.assertNotNull(instream);
        Assert.assertTrue((instream instanceof IdentityInputStream));
    }

    @Test
    public void testPrepareOutputLengthDelimited() throws Exception {
        final HttpResponse message = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        message.addHeader("Content-Length", "10");
        final OutputStream outstream = conn.prepareOutput(message);
        Assert.assertNotNull(outstream);
        Assert.assertTrue((outstream instanceof ContentLengthOutputStream));
    }

    @Test
    public void testPrepareOutputChunked() throws Exception {
        final HttpResponse message = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        message.addHeader("Transfer-Encoding", "chunked");
        final OutputStream outstream = conn.prepareOutput(message);
        Assert.assertNotNull(outstream);
        Assert.assertTrue((outstream instanceof ChunkedOutputStream));
    }

    @Test
    public void testPrepareOutputIdentity() throws Exception {
        final HttpResponse message = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        final OutputStream outstream = conn.prepareOutput(message);
        Assert.assertNotNull(outstream);
        Assert.assertTrue((outstream instanceof IdentityOutputStream));
    }

    @Test
    public void testSetSocketTimeout() throws Exception {
        conn.bind(socket);

        conn.setSocketTimeout(123);

        Mockito.verify(socket, Mockito.times(1)).setSoTimeout(123);
    }

    @Test
    public void testSetSocketTimeoutException() throws Exception {
        conn.bind(socket);

        Mockito.doThrow(new SocketException()).when(socket).setSoTimeout(Mockito.anyInt());

        conn.setSocketTimeout(123);

        Mockito.verify(socket, Mockito.times(1)).setSoTimeout(123);
    }

    @Test
    public void testGetSocketTimeout() throws Exception {
        Assert.assertEquals(-1, conn.getSocketTimeout());

        Mockito.when(socket.getSoTimeout()).thenReturn(345);
        conn.bind(socket);

        Assert.assertEquals(345, conn.getSocketTimeout());
    }

    @Test
    public void testGetSocketTimeoutException() throws Exception {
        Assert.assertEquals(-1, conn.getSocketTimeout());

        Mockito.when(socket.getSoTimeout()).thenThrow(new SocketException());
        conn.bind(socket);

        Assert.assertEquals(-1, conn.getSocketTimeout());
    }

    @Test
    public void testAwaitInputInBuffer() throws Exception {
        final ByteArrayInputStream instream = Mockito.spy(new ByteArrayInputStream(
                new byte[] {1, 2, 3, 4, 5}));
        Mockito.when(socket.getInputStream()).thenReturn(instream);

        conn.bind(socket);
        conn.ensureOpen();
        conn.getSessionInputBuffer().read();

        Assert.assertTrue(conn.awaitInput(432));

        Mockito.verify(socket, Mockito.never()).setSoTimeout(Mockito.anyInt());
        Mockito.verify(instream, Mockito.times(1)).read(
                Mockito.<byte []>any(), Mockito.anyInt(), Mockito.anyInt());
    }

    @Test
    public void testAwaitInputInSocket() throws Exception {
        final ByteArrayInputStream instream = Mockito.spy(new ByteArrayInputStream(
                new byte[] {1, 2, 3, 4, 5}));
        Mockito.when(socket.getInputStream()).thenReturn(instream);
        Mockito.when(socket.getSoTimeout()).thenReturn(345);

        conn.bind(socket);
        conn.ensureOpen();

        Assert.assertTrue(conn.awaitInput(432));

        Mockito.verify(socket, Mockito.times(1)).setSoTimeout(432);
        Mockito.verify(socket, Mockito.times(1)).setSoTimeout(345);
        Mockito.verify(instream, Mockito.times(1)).read(
                Mockito.<byte []>any(), Mockito.anyInt(), Mockito.anyInt());
    }

    @Test
    public void testAwaitInputNoData() throws Exception {
        final InputStream instream = Mockito.mock(InputStream.class);
        Mockito.when(socket.getInputStream()).thenReturn(instream);
        Mockito.when(instream.read(Mockito.<byte []>any(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(-1);

        conn.bind(socket);
        conn.ensureOpen();

        Assert.assertFalse(conn.awaitInput(432));
    }

    @Test
    public void testStaleWhenClosed() throws Exception {
        conn.bind(socket);
        conn.ensureOpen();
        conn.close();
        Assert.assertTrue(conn.isStale());
    }

    @Test
    public void testNotStaleWhenHasData() throws Exception {
        final ByteArrayInputStream instream = Mockito.spy(new ByteArrayInputStream(
                new byte[] {1, 2, 3, 4, 5}));
        Mockito.when(socket.getInputStream()).thenReturn(instream);

        conn.bind(socket);
        conn.ensureOpen();

        Assert.assertFalse(conn.isStale());
    }

    @Test
    public void testStaleWhenEndOfStream() throws Exception {
        final InputStream instream = Mockito.mock(InputStream.class);
        Mockito.when(socket.getInputStream()).thenReturn(instream);
        Mockito.when(instream.read(Mockito.<byte []>any(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(-1);

        conn.bind(socket);
        conn.ensureOpen();

        Assert.assertTrue(conn.isStale());
    }

    @Test
    public void testNotStaleWhenTimeout() throws Exception {
        final InputStream instream = Mockito.mock(InputStream.class);
        Mockito.when(socket.getInputStream()).thenReturn(instream);
        Mockito.when(instream.read(Mockito.<byte []>any(), Mockito.anyInt(), Mockito.anyInt()))
            .thenThrow(new SocketTimeoutException());

        conn.bind(socket);
        conn.ensureOpen();

        Assert.assertFalse(conn.isStale());
    }

    @Test
    public void testStaleWhenIOError() throws Exception {
        final InputStream instream = Mockito.mock(InputStream.class);
        Mockito.when(socket.getInputStream()).thenReturn(instream);
        Mockito.when(instream.read(Mockito.<byte []>any(), Mockito.anyInt(), Mockito.anyInt()))
            .thenThrow(new SocketException());

        conn.bind(socket);
        conn.ensureOpen();

        Assert.assertTrue(conn.isStale());
    }

}

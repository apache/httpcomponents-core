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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;

import org.apache.http.HttpEntity;
import org.apache.http.HttpVersion;
import org.apache.http.impl.entity.LaxContentLengthStrategy;
import org.apache.http.impl.entity.StrictContentLengthStrategy;
import org.apache.http.impl.nio.codecs.ChunkDecoder;
import org.apache.http.impl.nio.codecs.ChunkEncoder;
import org.apache.http.impl.nio.codecs.IdentityDecoder;
import org.apache.http.impl.nio.codecs.IdentityEncoder;
import org.apache.http.impl.nio.codecs.LengthDelimitedDecoder;
import org.apache.http.impl.nio.codecs.LengthDelimitedEncoder;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.protocol.HTTP;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class TestNHttpConnectionBase {

    @Mock
    private IOSession session;
    @Mock
    private ByteChannel channel;

    private NHttpConnectionBase conn;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        conn = new NHttpConnectionBase(session, 1024, 1024, HeapByteBufferAllocator.INSTANCE,
            null, null,
            LaxContentLengthStrategy.INSTANCE,
            StrictContentLengthStrategy.INSTANCE);
    }

    @Test
    public void testBasics() throws Exception {
        Assert.assertEquals("[Not bound]", conn.toString());

        Mockito.verify(session).setBufferStatus(conn);
    }

    @Test
    public void testSessionBind() throws Exception {
        final InetSocketAddress local = new InetSocketAddress(
            InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 8888);
        final InetSocketAddress remote = new InetSocketAddress(
            InetAddress.getByAddress(new byte[] {10, 0, 0, 2}), 80);
        Mockito.when(session.getLocalAddress()).thenReturn(local);
        Mockito.when(session.getRemoteAddress()).thenReturn(remote);
        Mockito.when(session.isClosed()).thenReturn(Boolean.FALSE);

        conn.bind(session);

        Mockito.verify(session, Mockito.times(2)).setBufferStatus(conn);

        Assert.assertEquals("127.0.0.1:8888<->10.0.0.2:80", conn.toString());
        Assert.assertTrue(conn.isOpen());
        Assert.assertEquals(8888, conn.getLocalPort());
        Assert.assertEquals(80, conn.getRemotePort());
        Assert.assertEquals(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), conn.getLocalAddress());
        Assert.assertEquals(InetAddress.getByAddress(new byte[] {10, 0, 0, 2}), conn.getRemoteAddress());
    }

    @Test
    public void testClose() throws Exception {
        final InetSocketAddress local = new InetSocketAddress(
            InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 8888);
        final InetSocketAddress remote = new InetSocketAddress(
            InetAddress.getByAddress(new byte[] {10, 0, 0, 2}), 80);
        Mockito.when(session.getLocalAddress()).thenReturn(local);
        Mockito.when(session.getRemoteAddress()).thenReturn(remote);
        Mockito.when(session.isClosed()).thenReturn(Boolean.FALSE);

        conn.close();

        Assert.assertEquals(NHttpConnection.CLOSED, conn.getStatus());
        Assert.assertEquals("127.0.0.1:8888<->10.0.0.2:80", conn.toString());

        Mockito.verify(session).close();
    }

    @Test
    public void testCloseWithBufferedData() throws Exception {
        final InetSocketAddress local = new InetSocketAddress(
            InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 8888);
        final InetSocketAddress remote = new InetSocketAddress(
            InetAddress.getByAddress(new byte[] {10, 0, 0, 2}), 80);
        Mockito.when(session.getLocalAddress()).thenReturn(local);
        Mockito.when(session.getRemoteAddress()).thenReturn(remote);
        Mockito.when(session.isClosed()).thenReturn(Boolean.FALSE);

        conn.outbuf.writeLine("stuff");
        conn.close();

        Assert.assertEquals(NHttpConnection.CLOSING, conn.getStatus());
        conn.close();
        Assert.assertEquals(NHttpConnection.CLOSING, conn.getStatus());
        Assert.assertEquals("127.0.0.1:8888<->10.0.0.2:80", conn.toString());

        Mockito.verify(session).setEvent(SelectionKey.OP_WRITE);
        Mockito.verify(session, Mockito.never()).close();
    }

    @Test
    public void testShutdown() throws Exception {
        final InetSocketAddress local = new InetSocketAddress(
            InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 8888);
        final InetSocketAddress remote = new InetSocketAddress(
            InetAddress.getByAddress(new byte[] {10, 0, 0, 2}), 80);
        Mockito.when(session.getLocalAddress()).thenReturn(local);
        Mockito.when(session.getRemoteAddress()).thenReturn(remote);
        Mockito.when(session.isClosed()).thenReturn(Boolean.FALSE);

        conn.outbuf.writeLine("stuff");
        conn.shutdown();

        Assert.assertEquals(NHttpConnection.CLOSED, conn.getStatus());
        Assert.assertEquals("127.0.0.1:8888<->10.0.0.2:80", conn.toString());

        Mockito.verify(session).shutdown();
    }

    @Test
    public void testContextOperations() throws Exception {
        conn.getContext().getAttribute("stuff");
        conn.getContext().setAttribute("stuff", "blah");
        conn.getContext().removeAttribute("other stuff");

        Mockito.verify(session).getAttribute("stuff");
        Mockito.verify(session).setAttribute("stuff", "blah");
        Mockito.verify(session).removeAttribute("other stuff");
    }

    @Test
    public void testIOOperations() throws Exception {
        conn.suspendInput();
        Mockito.verify(session).clearEvent(SelectionKey.OP_READ);

        conn.suspendOutput();
        Mockito.verify(session).clearEvent(SelectionKey.OP_WRITE);

        conn.requestInput();
        Mockito.verify(session).setEvent(SelectionKey.OP_READ);

        conn.requestOutput();
        Mockito.verify(session).setEvent(SelectionKey.OP_WRITE);
    }

    @Test
    public void testSocketTimeout() throws Exception {
        conn.getSocketTimeout();
        Mockito.verify(session).getSocketTimeout();

        conn.setSocketTimeout(123);
        Mockito.verify(session).setSocketTimeout(123);
    }

    @Test
    public void testPrepareIdentityDecoder() throws Exception {
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(session.channel()).thenReturn(channel);

        final HttpEntity entity = conn.prepareDecoder(response);
        Assert.assertNotNull(entity);
        Assert.assertEquals(-1, entity.getContentLength());
        Assert.assertFalse(entity.isChunked());
        Assert.assertTrue(conn.contentDecoder instanceof IdentityDecoder);
    }

    @Test
    public void testPrepareLengthDelimitedDecoder() throws Exception {
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader(HTTP.CONTENT_LEN, "10");
        response.addHeader(HTTP.CONTENT_TYPE, "stuff");
        response.addHeader(HTTP.CONTENT_ENCODING, "identity");
        Mockito.when(session.channel()).thenReturn(channel);

        final HttpEntity entity = conn.prepareDecoder(response);
        Assert.assertNotNull(entity);
        Assert.assertEquals(10, entity.getContentLength());
        Assert.assertFalse(entity.isChunked());
        Assert.assertNotNull(entity.getContentType());
        Assert.assertEquals("stuff", entity.getContentType().getValue());
        Assert.assertNotNull(entity.getContentEncoding());
        Assert.assertEquals("identity", entity.getContentEncoding().getValue());
        Assert.assertTrue(conn.contentDecoder instanceof LengthDelimitedDecoder);
    }

    @Test
    public void testPrepareChunkDecoder() throws Exception {
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader(HTTP.TRANSFER_ENCODING, "chunked");
        response.addHeader(HTTP.CONTENT_TYPE, "stuff");
        response.addHeader(HTTP.CONTENT_ENCODING, "identity");
        Mockito.when(session.channel()).thenReturn(channel);

        final HttpEntity entity = conn.prepareDecoder(response);
        Assert.assertNotNull(entity);
        Assert.assertEquals(-1, entity.getContentLength());
        Assert.assertTrue(entity.isChunked());
        Assert.assertNotNull(entity.getContentType());
        Assert.assertEquals("stuff", entity.getContentType().getValue());
        Assert.assertNotNull(entity.getContentEncoding());
        Assert.assertEquals("identity", entity.getContentEncoding().getValue());
        Assert.assertTrue(conn.contentDecoder instanceof ChunkDecoder);
    }

    @Test
    public void testPrepareIdentityEncoder() throws Exception {
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(session.channel()).thenReturn(channel);

        conn.prepareEncoder(response);
        Assert.assertTrue(conn.contentEncoder instanceof IdentityEncoder);
    }

    @Test
    public void testPrepareLengthDelimitedEncoder() throws Exception {
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader(HTTP.CONTENT_LEN, "10");
        Mockito.when(session.channel()).thenReturn(channel);

        conn.prepareEncoder(response);
        Assert.assertTrue(conn.contentEncoder instanceof LengthDelimitedEncoder);
    }

    @Test
    public void testPrepareChunkEncoder() throws Exception {
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader(HTTP.TRANSFER_ENCODING, "chunked");
        Mockito.when(session.channel()).thenReturn(channel);

        conn.prepareEncoder(response);
        Assert.assertTrue(conn.contentEncoder instanceof ChunkEncoder);
    }

}

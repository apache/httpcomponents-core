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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;

import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.NHttpConnection;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.apache.hc.core5.util.HeapByteBufferAllocator;
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
        conn = new NHttpConnectionBase(session, 1024, 1024, HeapByteBufferAllocator.INSTANCE, null, null);
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
        Assert.assertEquals(new InetSocketAddress(
                InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 8888), conn.getLocalAddress());
        Assert.assertEquals(new InetSocketAddress(
                InetAddress.getByAddress(new byte[]{10, 0, 0, 2}), 80), conn.getRemoteAddress());
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

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        chbuffer.append("stuff");
        conn.outbuf.writeLine(chbuffer);
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

        final CharArrayBuffer chbuffer = new CharArrayBuffer(16);
        chbuffer.append("stuff");
        conn.outbuf.writeLine(chbuffer);
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
    public void testCreateEntityWithContentLength() throws Exception {
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader(HttpHeaders.CONTENT_LENGTH, "10");
        response.addHeader(HttpHeaders.CONTENT_TYPE, "stuff");
        response.addHeader(HttpHeaders.CONTENT_ENCODING, "blah");

        final HttpEntity entity = conn.createIncomingEntity(response, 10);
        Assert.assertNotNull(entity);
        Assert.assertEquals(10, entity.getContentLength());
        Assert.assertFalse(entity.isChunked());
        Assert.assertEquals("stuff", entity.getContentType());
        Assert.assertEquals("blah", entity.getContentEncoding());
    }

    @Test
    public void testCreateEntityChunkCoded() throws Exception {
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.addHeader(HttpHeaders.TRANSFER_ENCODING, "chunked");
        response.addHeader(HttpHeaders.CONTENT_TYPE, "stuff");
        response.addHeader(HttpHeaders.CONTENT_ENCODING, "blah");

        final HttpEntity entity = conn.createIncomingEntity(response, ContentLengthStrategy.CHUNKED);
        Assert.assertNotNull(entity);
        Assert.assertEquals(-1, entity.getContentLength());
        Assert.assertTrue(entity.isChunked());
        Assert.assertEquals("stuff", entity.getContentType());
        Assert.assertEquals("blah", entity.getContentEncoding());
    }

}

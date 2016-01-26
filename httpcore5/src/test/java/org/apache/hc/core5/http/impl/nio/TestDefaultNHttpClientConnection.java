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

import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;

import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.ContentDecoder;
import org.apache.hc.core5.http.nio.ContentEncoder;
import org.apache.hc.core5.http.nio.IOControl;
import org.apache.hc.core5.http.nio.NHttpClientConnection;
import org.apache.hc.core5.http.nio.NHttpClientEventHandler;
import org.apache.hc.core5.http.nio.NHttpConnection;
import org.apache.hc.core5.http.nio.entity.HttpAsyncContentProducer;
import org.apache.hc.core5.http.nio.entity.NStringEntity;
import org.apache.hc.core5.reactor.IOSession;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class TestDefaultNHttpClientConnection {

    @Mock
    private IOSession session;
    @Mock
    private ByteChannel byteChan;
    @Mock
    private NHttpClientEventHandler handler;

    private DefaultNHttpClientConnection conn;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        conn = new DefaultNHttpClientConnection(session, 32);
    }

    @Test
    public void testSubmitRequest() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
        conn.submitRequest(request);

        Assert.assertNull(conn.getHttpRequest());
        Assert.assertTrue(conn.hasBufferedOutput());

        Mockito.verify(session).setEvent(SelectionKey.OP_WRITE);
    }

    @Test
    public void testSubmitEntityEnclosingRequest() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("POST", "/");
        request.setHeader(HttpHeaders.CONTENT_LENGTH, "5");
        request.setEntity(new StringEntity("stuff"));

        Mockito.when(session.channel()).thenReturn(byteChan);

        Assert.assertEquals(0, conn.getMetrics().getRequestCount());
        conn.submitRequest(request);

        Assert.assertSame(request, conn.getHttpRequest());
        Assert.assertTrue(conn.hasBufferedOutput());
        Assert.assertTrue(conn.isRequestSubmitted());
        Assert.assertNotNull(conn.contentEncoder);
        Assert.assertEquals(1, conn.getMetrics().getRequestCount());

        Mockito.verify(session).setEvent(SelectionKey.OP_WRITE);
    }

    @Test
    public void testOutputReset() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("POST", "/");
        request.setHeader(HttpHeaders.CONTENT_LENGTH, "5");
        request.setEntity(new StringEntity("stuff"));

        Mockito.when(session.channel()).thenReturn(byteChan);

        conn.submitRequest(request);

        Assert.assertNotNull(conn.getHttpRequest());
        Assert.assertNotNull(conn.contentEncoder);

        conn.resetOutput();

        Assert.assertNull(conn.getHttpRequest());
        Assert.assertNull(conn.contentEncoder);
    }

    static class RequestReadyAnswer implements Answer<Void> {

        private final HttpRequest request;

        RequestReadyAnswer(final HttpRequest request) {
            super();
            this.request = request;
        }

        @Override
        public Void answer(final InvocationOnMock invocation) throws Throwable {
            final Object[] args = invocation.getArguments();
            final NHttpClientConnection conn = (NHttpClientConnection) args[0];
            conn.submitRequest(request);
            return null;
        }
    }

    static class ProduceContentAnswer implements Answer<Void> {

        private final HttpAsyncContentProducer contentProducer;

        ProduceContentAnswer(final HttpAsyncContentProducer contentProducer) {
            super();
            this.contentProducer = contentProducer;
        }

        @Override
        public Void answer(final InvocationOnMock invocation) throws Throwable {
            final Object[] args = invocation.getArguments();
            final IOControl ioctrl = (IOControl) args[0];
            final ContentEncoder encoder = (ContentEncoder) args[1];
            contentProducer.produceContent(encoder, ioctrl);
            return null;
        }
    }

    @Test
    public void testProduceOutputShortMessageAfterSubmit() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("POST", "/");
        request.setHeader(HttpHeaders.CONTENT_LENGTH, "5");
        final NStringEntity entity = new NStringEntity("stuff");
        request.setEntity(entity);

        final WritableByteChannelMock wchannel = Mockito.spy(new WritableByteChannelMock(64));
        final ByteChannelMock channel = new ByteChannelMock(null, wchannel);
        Mockito.when(session.channel()).thenReturn(channel);

        conn.submitRequest(request);
        Assert.assertTrue(conn.outbuf.hasData());

        Mockito.doAnswer(new ProduceContentAnswer(entity)).when(
            handler).outputReady(Matchers.<NHttpClientConnection>any(), Matchers.<ContentEncoder>any());

        conn.produceOutput(handler);

        Assert.assertNull(conn.getHttpRequest());
        Assert.assertNull(conn.contentEncoder);
        Assert.assertEquals("POST / HTTP/1.1\r\nContent-Length: 5\r\n\r\nstuff", wchannel.dump(StandardCharsets.US_ASCII));

        Mockito.verify(wchannel, Mockito.times(2)).write(Matchers.<ByteBuffer>any());
    }

    @Test
    public void testProduceOutputLongMessageAfterSubmit() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("POST", "/");
        request.setHeader(HttpHeaders.CONTENT_LENGTH, "22");
        final NStringEntity entity = new NStringEntity("a lot of various stuff");
        request.setEntity(entity);

        final WritableByteChannelMock wchannel = Mockito.spy(new WritableByteChannelMock(64));
        final ByteChannelMock channel = new ByteChannelMock(null, wchannel);
        Mockito.when(session.channel()).thenReturn(channel);

        conn.submitRequest(request);
        Assert.assertEquals(39, conn.outbuf.length());

        Mockito.doAnswer(new ProduceContentAnswer(entity)).when(
            handler).outputReady(Matchers.<NHttpClientConnection>any(), Matchers.<ContentEncoder>any());

        conn.produceOutput(handler);

        Assert.assertNull(conn.getHttpRequest());
        Assert.assertNull(conn.contentEncoder);
        Assert.assertEquals("POST / HTTP/1.1\r\nContent-Length: 22\r\n\r\na lot of various stuff", wchannel.dump(StandardCharsets.US_ASCII));

        Mockito.verify(wchannel, Mockito.times(2)).write(Matchers.<ByteBuffer>any());
    }

    @Test
    public void testProduceOutputShortMessage() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("POST", "/");
        request.setHeader(HttpHeaders.CONTENT_LENGTH, "5");
        final NStringEntity entity = new NStringEntity("stuff");
        request.setEntity(entity);

        final WritableByteChannelMock wchannel = Mockito.spy(new WritableByteChannelMock(64));
        final ByteChannelMock channel = new ByteChannelMock(null, wchannel);
        Mockito.when(session.channel()).thenReturn(channel);

        Mockito.doAnswer(new RequestReadyAnswer(request)).when(
            handler).requestReady(Matchers.<NHttpClientConnection>any());

        Mockito.doAnswer(new ProduceContentAnswer(entity)).when(
            handler).outputReady(Matchers.<NHttpClientConnection>any(), Matchers.<ContentEncoder>any());

        conn.produceOutput(handler);

        Assert.assertNull(conn.getHttpRequest());
        Assert.assertNull(conn.contentEncoder);
        Assert.assertEquals("POST / HTTP/1.1\r\nContent-Length: 5\r\n\r\nstuff", wchannel.dump(StandardCharsets.US_ASCII));

        Mockito.verify(wchannel, Mockito.times(2)).write(Matchers.<ByteBuffer>any());
    }

    @Test
    public void testProduceOutputLongMessage() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("POST", "/");
        request.setHeader(HttpHeaders.CONTENT_LENGTH, "22");
        final NStringEntity entity = new NStringEntity("a lot of various stuff");
        request.setEntity(entity);

        final WritableByteChannelMock wchannel = Mockito.spy(new WritableByteChannelMock(64));
        final ByteChannelMock channel = new ByteChannelMock(null, wchannel);
        Mockito.when(session.channel()).thenReturn(channel);

        Mockito.doAnswer(new RequestReadyAnswer(request)).when(
            handler).requestReady(Matchers.<NHttpClientConnection>any());

        Mockito.doAnswer(new ProduceContentAnswer(entity)).when(
            handler).outputReady(Matchers.<NHttpClientConnection>any(), Matchers.<ContentEncoder>any());

        conn.produceOutput(handler);

        Assert.assertNull(conn.getHttpRequest());
        Assert.assertNull(conn.contentEncoder);
        Assert.assertEquals("POST / HTTP/1.1\r\nContent-Length: 22\r\n\r\na lot of various stuff",
                wchannel.dump(StandardCharsets.US_ASCII));

        Mockito.verify(wchannel, Mockito.times(2)).write(Matchers.<ByteBuffer>any());
    }

    @Test
    public void testProduceOutputLongMessageSaturatedChannel() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("POST", "/");
        request.setHeader(HttpHeaders.CONTENT_LENGTH, "22");
        final NStringEntity entity = new NStringEntity("a lot of various stuff");
        request.setEntity(entity);

        final WritableByteChannelMock wchannel = Mockito.spy(new WritableByteChannelMock(64, 48));
        final ByteChannelMock channel = new ByteChannelMock(null, wchannel);
        Mockito.when(session.channel()).thenReturn(channel);

        Mockito.doAnswer(new RequestReadyAnswer(request)).when(
            handler).requestReady(Matchers.<NHttpClientConnection>any());

        Mockito.doAnswer(new ProduceContentAnswer(entity)).when(
            handler).outputReady(Matchers.<NHttpClientConnection>any(), Matchers.<ContentEncoder>any());

        conn.produceOutput(handler);

        Assert.assertNull(conn.getHttpRequest());
        Assert.assertNull(conn.contentEncoder);
        Assert.assertTrue(conn.outbuf.hasData());

        Mockito.verify(session, Mockito.never()).clearEvent(SelectionKey.OP_WRITE);
        Mockito.verify(wchannel, Mockito.times(2)).write(Matchers.<ByteBuffer>any());
    }

    @Test
    public void testProduceOutputLongMessageSaturatedChannel2() throws Exception {
        conn = new DefaultNHttpClientConnection(session, 24);
        final BasicHttpRequest request = new BasicHttpRequest("POST", "/");
        request.setHeader(HttpHeaders.CONTENT_LENGTH, "45");
        final NStringEntity entity = new NStringEntity("a loooooooooooooooooooooooot of various stuff");
        request.setEntity(entity);

        final WritableByteChannelMock wchannel = Mockito.spy(new WritableByteChannelMock(64, 48));
        final ByteChannelMock channel = new ByteChannelMock(null, wchannel);
        Mockito.when(session.channel()).thenReturn(channel);

        Mockito.doAnswer(new RequestReadyAnswer(request)).when(
            handler).requestReady(Matchers.<NHttpClientConnection>any());

        Mockito.doAnswer(new ProduceContentAnswer(entity)).when(
            handler).outputReady(Matchers.<NHttpClientConnection>any(), Matchers.<ContentEncoder>any());

        conn.produceOutput(handler);

        Assert.assertNotNull(conn.getHttpRequest());
        Assert.assertNotNull(conn.contentEncoder);
        Assert.assertFalse(conn.outbuf.hasData());

        Mockito.verify(session, Mockito.never()).clearEvent(SelectionKey.OP_WRITE);
        Mockito.verify(wchannel, Mockito.times(3)).write(Matchers.<ByteBuffer>any());
    }

    @Test
    public void testProduceOutputLongChunkedMessage() throws Exception {
        conn = new DefaultNHttpClientConnection(session, 64);

        final BasicHttpRequest request = new BasicHttpRequest("POST", "/");
        request.setHeader(HttpHeaders.TRANSFER_ENCODING, HeaderElements.CHUNKED_ENCODING);
        final NStringEntity entity = new NStringEntity("a lot of various stuff");
        entity.setChunked(true);
        request.setEntity(entity);

        final WritableByteChannelMock wchannel = Mockito.spy(new WritableByteChannelMock(64));
        final ByteChannelMock channel = new ByteChannelMock(null, wchannel);
        Mockito.when(session.channel()).thenReturn(channel);

        Mockito.doAnswer(new RequestReadyAnswer(request)).when(
            handler).requestReady(Matchers.<NHttpClientConnection>any());

        Mockito.doAnswer(new ProduceContentAnswer(entity)).when(
            handler).outputReady(Matchers.<NHttpClientConnection>any(), Matchers.<ContentEncoder>any());

        conn.produceOutput(handler);

        Assert.assertNull(conn.getHttpRequest());
        Assert.assertNull(conn.contentEncoder);
        Assert.assertEquals("POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n" +
                "5\r\na lot\r\n11\r\n of various stuff\r\n0\r\n\r\n", wchannel.dump(StandardCharsets.US_ASCII));

        Mockito.verify(wchannel, Mockito.times(2)).write(Matchers.<ByteBuffer>any());
    }

    @Test
    public void testProduceOutputLongChunkedMessageSaturatedChannel() throws Exception {
        conn = new DefaultNHttpClientConnection(session, 64);

        final BasicHttpRequest request = new BasicHttpRequest("POST", "/");
        request.setHeader(HttpHeaders.TRANSFER_ENCODING, HeaderElements.CHUNKED_ENCODING);
        final NStringEntity entity = new NStringEntity("a lot of various stuff");
        entity.setChunked(true);
        request.setEntity(entity);

        final WritableByteChannelMock wchannel = Mockito.spy(new WritableByteChannelMock(64, 64));
        final ByteChannelMock channel = new ByteChannelMock(null, wchannel);
        Mockito.when(session.channel()).thenReturn(channel);

        Mockito.doAnswer(new RequestReadyAnswer(request)).when(
            handler).requestReady(Matchers.<NHttpClientConnection>any());

        Mockito.doAnswer(new ProduceContentAnswer(entity)).when(
            handler).outputReady(Matchers.<NHttpClientConnection>any(), Matchers.<ContentEncoder>any());

        conn.produceOutput(handler);

        Assert.assertNull(conn.getHttpRequest());
        Assert.assertNull(conn.contentEncoder);
        Assert.assertEquals("POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n" +
                "5\r\na lot\r\n11\r\n of", wchannel.dump(StandardCharsets.US_ASCII));
        Assert.assertEquals(21, conn.outbuf.length());

        Mockito.verify(session, Mockito.never()).clearEvent(SelectionKey.OP_WRITE);
        Mockito.verify(wchannel, Mockito.times(2)).write(Matchers.<ByteBuffer>any());
    }

    @Test
    public void testProduceOutputClosingConnection() throws Exception {
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/");

        final WritableByteChannelMock wchannel = Mockito.spy(new WritableByteChannelMock(64));
        final ByteChannelMock channel = new ByteChannelMock(null, wchannel);
        Mockito.when(session.channel()).thenReturn(channel);

        conn.submitRequest(request);
        conn.close();

        Assert.assertEquals(NHttpConnection.CLOSING, conn.getStatus());

        conn.produceOutput(handler);

        Assert.assertEquals(NHttpConnection.CLOSED, conn.getStatus());

        Mockito.verify(wchannel, Mockito.times(1)).write(Matchers.<ByteBuffer>any());
        Mockito.verify(session, Mockito.times(1)).close();
        Mockito.verify(session, Mockito.never()).clearEvent(SelectionKey.OP_WRITE);
        Mockito.verify(handler, Mockito.never()).requestReady(
            Matchers.<NHttpClientConnection>any());
        Mockito.verify(handler, Mockito.never()).outputReady(
            Matchers.<NHttpClientConnection>any(), Matchers.<ContentEncoder>any());
    }

    static class ResponseCapturingAnswer implements Answer<Void> {

        private final LinkedList<HttpResponse> responses;

        ResponseCapturingAnswer(final LinkedList<HttpResponse> responses) {
            super();
            this.responses = responses;
        }

        @Override
        public Void answer(final InvocationOnMock invocation) throws Throwable {
            final Object[] args = invocation.getArguments();
            final NHttpClientConnection conn = (NHttpClientConnection) args[0];
            if (conn != null) {
                final HttpResponse response = conn.getHttpResponse();
                if (response != null) {
                    responses.add(response);
                }
            }
            return null;
        }

    }

    static class ConsumeContentAnswer implements Answer<Void> {

        private final SimpleInputBuffer buf;

        ConsumeContentAnswer(final SimpleInputBuffer buf) {
            super();
            this.buf = buf;
        }

        @Override
        public Void answer(final InvocationOnMock invocation) throws Throwable {
            final Object[] args = invocation.getArguments();
            final ContentDecoder decoder = (ContentDecoder) args[1];
            buf.consumeContent(decoder);
            return null;
        }

    }

    @Test
    public void testConsumeInputShortMessage() throws Exception {
        final ReadableByteChannelMock rchannel = Mockito.spy(new ReadableByteChannelMock(
            new String[] {"HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nstuff"}, StandardCharsets.US_ASCII));
        final ByteChannelMock channel = new ByteChannelMock(rchannel, null);
        Mockito.when(session.channel()).thenReturn(channel);
        Mockito.when(session.getEventMask()).thenReturn(SelectionKey.OP_READ);

        final LinkedList<HttpResponse> responses = new LinkedList<>();

        Mockito.doAnswer(new ResponseCapturingAnswer(responses)).when(
            handler).responseReceived(Matchers.<NHttpClientConnection>any());
        Mockito.doAnswer(new ConsumeContentAnswer(new SimpleInputBuffer(64))).when(
            handler).inputReady(Matchers.<NHttpClientConnection>any(), Matchers.<ContentDecoder>any());

        Assert.assertEquals(0, conn.getMetrics().getResponseCount());

        conn.consumeInput(handler);

        Assert.assertNull(conn.getHttpResponse());
        Assert.assertNull(conn.contentDecoder);
        Assert.assertEquals(1, conn.getMetrics().getResponseCount());
        Assert.assertEquals(43, conn.getMetrics().getReceivedBytesCount());

        Mockito.verify(handler, Mockito.times(1)).responseReceived(
            Matchers.<NHttpClientConnection>any());
        Mockito.verify(handler, Mockito.times(1)).inputReady(
            Matchers.<NHttpClientConnection>any(), Matchers.<LengthDelimitedDecoder>any());
        Mockito.verify(rchannel, Mockito.times(2)).read(Matchers.<ByteBuffer>any());
        Mockito.verify(handler, Mockito.never()).exception(
            Matchers.<NHttpClientConnection>any(), Matchers.<Exception>any());

        Assert.assertFalse(responses.isEmpty());
        final HttpResponse response = responses.getFirst();
        Assert.assertNotNull(response);
        Assert.assertEquals(HttpVersion.HTTP_1_1, response.getStatusLine().getProtocolVersion());
        Assert.assertEquals(200, response.getCode());
        Assert.assertEquals("OK", response.getStatusLine().getReasonPhrase());
        final HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertEquals(5, entity.getContentLength());
    }

    @Test
    public void testConsumeInputLongMessage() throws Exception {
        conn = new DefaultNHttpClientConnection(session, 1024);
        final ReadableByteChannelMock rchannel = Mockito.spy(new ReadableByteChannelMock(
            new String[] {"HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\na lot of stuff",
                "", ""}, StandardCharsets.US_ASCII));
        final ByteChannelMock channel = new ByteChannelMock(rchannel, null);
        Mockito.when(session.channel()).thenReturn(channel);
        Mockito.when(session.getEventMask()).thenReturn(SelectionKey.OP_READ);

        final LinkedList<HttpResponse> responses = new LinkedList<>();

        Mockito.doAnswer(new ResponseCapturingAnswer(responses)).when(
            handler).responseReceived(Matchers.<NHttpClientConnection>any());
        Mockito.doAnswer(new ConsumeContentAnswer(new SimpleInputBuffer(64))).when(
            handler).inputReady(Matchers.<NHttpClientConnection>any(), Matchers.<ContentDecoder>any());

        Assert.assertEquals(0, conn.getMetrics().getResponseCount());

        conn.consumeInput(handler);

        Assert.assertNotNull(conn.getHttpResponse());
        Assert.assertNotNull(conn.contentDecoder);
        Assert.assertEquals(1, conn.getMetrics().getResponseCount());
        Assert.assertEquals(54, conn.getMetrics().getReceivedBytesCount());

        Mockito.verify(handler, Mockito.times(1)).responseReceived(
            Matchers.<NHttpClientConnection>any());
        Mockito.verify(handler, Mockito.times(1)).inputReady(
            Matchers.<NHttpClientConnection>any(), Matchers.<LengthDelimitedDecoder>any());
        Mockito.verify(rchannel, Mockito.times(2)).read(Matchers.<ByteBuffer>any());
        Mockito.verify(handler, Mockito.never()).exception(
            Matchers.<NHttpClientConnection>any(), Matchers.<Exception>any());

        Assert.assertFalse(responses.isEmpty());
        final HttpResponse response = responses.getFirst();
        Assert.assertNotNull(response);
        Assert.assertEquals(HttpVersion.HTTP_1_1, response.getStatusLine().getProtocolVersion());
        Assert.assertEquals(200, response.getCode());
        Assert.assertEquals("OK", response.getStatusLine().getReasonPhrase());
        final HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertEquals(100, entity.getContentLength());

        conn.consumeInput(handler);

        Assert.assertEquals(1, conn.getMetrics().getResponseCount());
        Assert.assertEquals(54, conn.getMetrics().getReceivedBytesCount());

        Mockito.verify(rchannel, Mockito.times(3)).read(Matchers.<ByteBuffer>any());
        Mockito.verify(handler, Mockito.never()).exception(
            Matchers.<NHttpClientConnection>any(), Matchers.<Exception>any());
    }

    @Test
    public void testConsumeInputBasicMessageNoEntity() throws Exception {
        final ReadableByteChannelMock rchannel = Mockito.spy(new ReadableByteChannelMock(
            new String[] {"HTTP/1.1 100 Continue\r\n\r\n"}, StandardCharsets.US_ASCII));
        final ByteChannelMock channel = new ByteChannelMock(rchannel, null);
        Mockito.when(session.channel()).thenReturn(channel);
        Mockito.when(session.getEventMask()).thenReturn(SelectionKey.OP_READ);

        final LinkedList<HttpResponse> responses = new LinkedList<>();

        Mockito.doAnswer(new ResponseCapturingAnswer(responses)).when(
            handler).responseReceived(Matchers.<NHttpClientConnection>any());
        Mockito.doAnswer(new ConsumeContentAnswer(new SimpleInputBuffer(64))).when(
            handler).inputReady(Matchers.<NHttpClientConnection>any(), Matchers.<ContentDecoder>any());

        conn.consumeInput(handler);

        Assert.assertNull(conn.getHttpResponse());
        Assert.assertNull(conn.contentDecoder);

        Mockito.verify(handler, Mockito.times(1)).responseReceived(
            Matchers.<NHttpClientConnection>any());
        Mockito.verify(handler, Mockito.never()).inputReady(
            Matchers.<NHttpClientConnection>any(), Matchers.<LengthDelimitedDecoder>any());
        Mockito.verify(rchannel, Mockito.times(1)).read(Matchers.<ByteBuffer>any());
        Mockito.verify(handler, Mockito.never()).exception(
            Matchers.<NHttpClientConnection>any(), Matchers.<Exception>any());

        Assert.assertFalse(responses.isEmpty());
        final HttpResponse response = responses.getFirst();
        Assert.assertNotNull(response);
        Assert.assertEquals(HttpVersion.HTTP_1_1, response.getStatusLine().getProtocolVersion());
        Assert.assertEquals(100, response.getCode());
        final HttpEntity entity = response.getEntity();
        Assert.assertNull(entity);
    }

    @Test
    public void testConsumeInputNoData() throws Exception {
        conn = new DefaultNHttpClientConnection(session, 1024);
        final ReadableByteChannelMock rchannel = Mockito.spy(new ReadableByteChannelMock(
            new String[] {"", ""}, StandardCharsets.US_ASCII));
        final ByteChannelMock channel = new ByteChannelMock(rchannel, null);
        Mockito.when(session.channel()).thenReturn(channel);
        Mockito.when(session.getEventMask()).thenReturn(SelectionKey.OP_READ);

        final LinkedList<HttpResponse> responses = new LinkedList<>();

        Mockito.doAnswer(new ResponseCapturingAnswer(responses)).when(
            handler).responseReceived(Matchers.<NHttpClientConnection>any());
        Mockito.doAnswer(new ConsumeContentAnswer(new SimpleInputBuffer(64))).when(
            handler).inputReady(Matchers.<NHttpClientConnection>any(), Matchers.<ContentDecoder>any());

        Assert.assertEquals(0, conn.getMetrics().getResponseCount());

        conn.consumeInput(handler);

        Assert.assertNull(conn.getHttpResponse());
        Assert.assertNull(conn.contentDecoder);
        Assert.assertEquals(0, conn.getMetrics().getResponseCount());
        Assert.assertEquals(0, conn.getMetrics().getReceivedBytesCount());

        Mockito.verify(handler, Mockito.never()).responseReceived(
            Matchers.<NHttpClientConnection>any());
        Mockito.verify(handler, Mockito.never()).inputReady(
            Matchers.<NHttpClientConnection>any(), Matchers.<LengthDelimitedDecoder>any());
        Mockito.verify(rchannel, Mockito.times(1)).read(Matchers.<ByteBuffer>any());
        Mockito.verify(handler, Mockito.never()).exception(
            Matchers.<NHttpClientConnection>any(), Matchers.<Exception>any());

        conn.consumeInput(handler);

        Assert.assertNull(conn.getHttpResponse());
        Assert.assertNull(conn.contentDecoder);
        Assert.assertEquals(0, conn.getMetrics().getResponseCount());
        Assert.assertEquals(0, conn.getMetrics().getReceivedBytesCount());

        Mockito.verify(handler, Mockito.never()).responseReceived(
            Matchers.<NHttpClientConnection>any());
        Mockito.verify(handler, Mockito.never()).inputReady(
            Matchers.<NHttpClientConnection>any(), Matchers.<LengthDelimitedDecoder>any());
        Mockito.verify(rchannel, Mockito.times(2)).read(Matchers.<ByteBuffer>any());
        Mockito.verify(handler, Mockito.never()).exception(
            Matchers.<NHttpClientConnection>any(), Matchers.<Exception>any());

        conn.consumeInput(handler);
        Assert.assertNull(conn.getHttpResponse());
        Assert.assertNull(conn.contentDecoder);
        Assert.assertEquals(0, conn.getMetrics().getResponseCount());
        Assert.assertEquals(0, conn.getMetrics().getReceivedBytesCount());

        Mockito.verify(handler, Mockito.never()).responseReceived(
            Matchers.<NHttpClientConnection>any());
        Mockito.verify(handler, Mockito.never()).inputReady(
            Matchers.<NHttpClientConnection>any(), Matchers.<LengthDelimitedDecoder>any());
        Mockito.verify(rchannel, Mockito.times(3)).read(Matchers.<ByteBuffer>any());
        Mockito.verify(handler, Mockito.times(1)).endOfInput(
            Matchers.<NHttpClientConnection>any());
        Mockito.verify(handler, Mockito.never()).exception(
            Matchers.<NHttpClientConnection>any(), Matchers.<Exception>any());

    }

    @Test
    public void testConsumeInputConnectionClosed() throws Exception {
        conn = new DefaultNHttpClientConnection(session, 1024);
        final ReadableByteChannelMock rchannel = Mockito.spy(new ReadableByteChannelMock(
            new String[] {"", ""}, StandardCharsets.US_ASCII));
        final ByteChannelMock channel = new ByteChannelMock(rchannel, null);
        Mockito.when(session.channel()).thenReturn(channel);
        Mockito.when(session.getEventMask()).thenReturn(SelectionKey.OP_READ);

        conn.close();
        conn.consumeInput(handler);
        Mockito.verify(rchannel, Mockito.never()).read(Matchers.<ByteBuffer>any());
        Mockito.verify(session, Mockito.times(1)).clearEvent(SelectionKey.OP_READ);
    }

}

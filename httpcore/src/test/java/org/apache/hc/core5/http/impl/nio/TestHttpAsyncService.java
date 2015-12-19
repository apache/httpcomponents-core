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
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Queue;

import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseFactory;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.DefaultHttpResponseFactory;
import org.apache.hc.core5.http.impl.nio.HttpAsyncService.Incoming;
import org.apache.hc.core5.http.impl.nio.HttpAsyncService.Outgoing;
import org.apache.hc.core5.http.impl.nio.HttpAsyncService.PipelineEntry;
import org.apache.hc.core5.http.impl.nio.HttpAsyncService.State;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.ContentDecoder;
import org.apache.hc.core5.http.nio.ContentEncoder;
import org.apache.hc.core5.http.nio.HttpAsyncExchange;
import org.apache.hc.core5.http.nio.HttpAsyncExpectationVerifier;
import org.apache.hc.core5.http.nio.HttpAsyncRequestConsumer;
import org.apache.hc.core5.http.nio.HttpAsyncRequestHandler;
import org.apache.hc.core5.http.nio.HttpAsyncResponseProducer;
import org.apache.hc.core5.http.nio.NHttpConnection;
import org.apache.hc.core5.http.nio.NHttpServerConnection;
import org.apache.hc.core5.http.nio.entity.NStringEntity;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.reactor.SessionBufferStatus;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Matchers;
import org.mockito.Mockito;

public class TestHttpAsyncService {

    private UriHttpAsyncRequestHandlerMapper handlerResolver;
    private HttpAsyncService protocolHandler;
    private HttpProcessor httpProcessor;
    private ConnectionReuseStrategy reuseStrategy;
    private HttpResponseFactory responseFactory;
    private HttpContext connContext;
    private NHttpServerConnection conn;
    private HttpAsyncRequestHandler<Object> requestHandler;
    private HttpAsyncRequestConsumer<Object> requestConsumer;
    private HttpAsyncResponseProducer responseProducer;
    private ContentEncoder encoder;
    private ContentDecoder decoder;
    private Cancellable cancellable;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        this.requestHandler = Mockito.mock(HttpAsyncRequestHandler.class);
        this.requestConsumer = Mockito.mock(HttpAsyncRequestConsumer.class);
        this.responseProducer = Mockito.mock(HttpAsyncResponseProducer.class);
        this.handlerResolver = new UriHttpAsyncRequestHandlerMapper();
        this.handlerResolver.register("/", this.requestHandler);
        this.httpProcessor = Mockito.mock(HttpProcessor.class);
        this.reuseStrategy = Mockito.mock(ConnectionReuseStrategy.class);
        this.responseFactory = DefaultHttpResponseFactory.INSTANCE;
        this.protocolHandler = new HttpAsyncService(
                this.httpProcessor, this.reuseStrategy, this.responseFactory, this.handlerResolver, null);
        this.connContext = new BasicHttpContext();
        this.conn = Mockito.mock(NHttpServerConnection.class);
        this.encoder = Mockito.mock(ContentEncoder.class);
        this.decoder = Mockito.mock(ContentDecoder.class);
        this.cancellable = Mockito.mock(Cancellable.class);

        Mockito.when(this.conn.getContext()).thenReturn(this.connContext);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test(expected=IllegalArgumentException.class)
    public void testInvalidConstruction() throws Exception {
        new HttpAsyncService(null, this.reuseStrategy, this.responseFactory, this.handlerResolver, null);
    }

    @Test
    public void testConnected() throws Exception {
        this.protocolHandler.connected(this.conn);

        final State state = (State) this.connContext.getAttribute(
                HttpAsyncService.HTTP_EXCHANGE_STATE);
        Assert.assertNotNull(state);
        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
        Assert.assertEquals("[incoming READY; outgoing READY]", state.toString());
    }

    @Test
    public void testClosed() throws Exception {
        final State state = new State();
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseState(MessageState.COMPLETED);
        final HttpContext exchangeContext = new BasicHttpContext();

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final Incoming incoming = new Incoming(request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        final Outgoing outgoing = new Outgoing(request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        state.setCancellable(this.cancellable);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        this.protocolHandler.closed(this.conn);

        Mockito.verify(this.requestConsumer).close();
        Mockito.verify(this.responseProducer).close();
        Mockito.verify(this.cancellable).cancel();
    }

    @Test
    public void testHttpExceptionHandling() throws Exception {
        final State state = new State();
        state.setRequestState(MessageState.READY);
        state.setResponseState(MessageState.READY);
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final Incoming incoming = new Incoming(request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        state.setCancellable(this.cancellable);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpException httpex = new HttpException();
        this.protocolHandler.exception(this.conn, httpex);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.BODY_STREAM, state.getResponseState());
        final Outgoing outgoing = state.getOutgoing();
        Assert.assertNotNull(outgoing);
        Assert.assertNotNull(outgoing.getProducer());
        Assert.assertNotNull(outgoing.getResponse());
        Assert.assertEquals(500, outgoing.getResponse().getCode());

        Mockito.verify(this.requestConsumer).failed(httpex);
        Mockito.verify(this.requestConsumer).close();
        Mockito.verify(this.cancellable).cancel();
        Mockito.verify(this.conn, Mockito.never()).shutdown();
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testExceptionHandlingNoState() throws Exception {
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, null);

        final Exception ex = new Exception("Oopsie");
        this.protocolHandler.exception(conn, ex);

        Mockito.verify(conn).getContext();
        Mockito.verify(conn).shutdown();
        Mockito.verifyNoMoreInteractions(conn);
    }

    @Test
    public void testExceptionHandlingRuntimeException() throws Exception {
        final State state = new State();
        state.setRequestState(MessageState.READY);
        state.setResponseState(MessageState.READY);
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final Incoming incoming = new Incoming(request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        final Outgoing outgoing = new Outgoing(request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        state.setCancellable(this.cancellable);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        Mockito.doThrow(new RuntimeException()).when(this.httpProcessor).process(
                Matchers.any(HttpResponse.class), Matchers.any(HttpContext.class));
        final HttpException httpex = new HttpException();
        try {
            this.protocolHandler.exception(this.conn, httpex);
            Assert.fail("RuntimeException expected");
        } catch (final RuntimeException ex) {
            Mockito.verify(this.conn).shutdown();
            Mockito.verify(this.requestConsumer).failed(httpex);
            Mockito.verify(this.requestConsumer, Mockito.atLeastOnce()).close();
            Mockito.verify(this.responseProducer).failed(httpex);
            Mockito.verify(this.responseProducer, Mockito.atLeastOnce()).close();
            Mockito.verify(this.cancellable).cancel();
        }
    }

    @Test
    public void testHttpExceptionHandlingIOException() throws Exception {
        final State state = new State();
        state.setRequestState(MessageState.READY);
        state.setResponseState(MessageState.READY);
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final Incoming incoming = new Incoming(request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        final Outgoing outgoing = new Outgoing(request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        state.setCancellable(this.cancellable);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        Mockito.doThrow(new IOException()).when(this.httpProcessor).process(
                Matchers.any(HttpResponse.class), Matchers.any(HttpContext.class));
        final HttpException httpex = new HttpException();

        this.protocolHandler.exception(this.conn, httpex);

        Mockito.verify(this.conn).shutdown();
        Mockito.verify(this.requestConsumer).failed(httpex);
        Mockito.verify(this.requestConsumer, Mockito.atLeastOnce()).close();
        Mockito.verify(this.responseProducer).failed(httpex);
        Mockito.verify(this.responseProducer, Mockito.atLeastOnce()).close();
        Mockito.verify(this.cancellable).cancel();
    }

    @Test
    public void testHttpExceptionHandlingResponseSubmitted() throws Exception {
        final State state = new State();
        state.setRequestState(MessageState.READY);
        state.setResponseState(MessageState.READY);
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final Incoming incoming = new Incoming(request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        final Outgoing outgoing = new Outgoing(request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);
        Mockito.when(this.conn.isResponseSubmitted()).thenReturn(Boolean.TRUE);

        final HttpException httpex = new HttpException();
        this.protocolHandler.exception(this.conn, httpex);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
        Mockito.verify(this.conn).shutdown();
        Mockito.verify(this.requestConsumer).failed(httpex);
        Mockito.verify(this.requestConsumer).close();
        Mockito.verify(this.responseProducer).failed(httpex);
        Mockito.verify(this.responseProducer).close();
    }

    @Test
    public void testBasicRequest() throws Exception {
        final State state = new State();
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        Mockito.when(this.conn.getHttpRequest()).thenReturn(request);
        Mockito.when(this.requestHandler.processRequest(
                Matchers.eq(request), Matchers.any(HttpContext.class))).thenReturn(this.requestConsumer);
        Mockito.when(this.requestConsumer.getException()).thenReturn(null);
        final Object data = new Object();
        Mockito.when(this.requestConsumer.getResult()).thenReturn(data);

        this.protocolHandler.requestReceived(this.conn);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());

        final Incoming incoming = state.getIncoming();
        Assert.assertNull(incoming);

        final ArgumentCaptor<HttpContext> argumentCaptor = ArgumentCaptor.forClass(HttpContext.class);
        Mockito.verify(this.httpProcessor).process(Matchers.eq(request), argumentCaptor.capture());
        final HttpContext exchangeContext = argumentCaptor.getValue();
        Assert.assertNotNull(exchangeContext);

        Assert.assertSame(request, exchangeContext.getAttribute(HttpCoreContext.HTTP_REQUEST));
        Assert.assertSame(this.conn, exchangeContext.getAttribute(HttpCoreContext.HTTP_CONNECTION));

        Mockito.verify(this.requestConsumer).requestReceived(request);
        Mockito.verify(this.requestConsumer).requestCompleted(exchangeContext);
        Mockito.verify(this.conn).requestOutput();

        final PipelineEntry entry = state.getRequestQueue().poll();
        Assert.assertNotNull(entry);
        Assert.assertSame(request, entry.getRequest());
        Assert.assertSame(requestHandler, entry.getHandler());
        Assert.assertNotNull(entry.getResult());
        Assert.assertNull(entry.getException());
    }

    @Test
    public void testRequestPipelineIfResponseInitiated() throws Exception {
        final State state = new State();
        state.setRequestState(MessageState.READY);
        state.setResponseState(MessageState.INIT);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        Mockito.when(this.conn.getHttpRequest()).thenReturn(request);
        Mockito.when(this.requestHandler.processRequest(
                Matchers.eq(request), Matchers.any(HttpContext.class))).thenReturn(this.requestConsumer);
        Mockito.when(this.requestConsumer.getException()).thenReturn(null);
        final Object data = new Object();
        Mockito.when(this.requestConsumer.getResult()).thenReturn(data);

        this.protocolHandler.requestReceived(this.conn);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.INIT, state.getResponseState());

        final Incoming incoming = state.getIncoming();
        Assert.assertNull(incoming);

        Mockito.verify(this.requestConsumer).requestReceived(request);
        Mockito.verify(this.requestConsumer).requestCompleted(Matchers.<HttpContext>any());
        Mockito.verify(this.requestHandler, Mockito.never()).handle(
                Matchers.any(),
                Matchers.any(HttpAsyncExchange.class),
                Matchers.any(HttpContext.class));

        Assert.assertFalse(state.getRequestQueue().isEmpty());
        final PipelineEntry entry = state.getRequestQueue().remove();
        Assert.assertSame(request, entry.getRequest());
        Assert.assertSame(data, entry.getResult());
    }

    @Test
    public void testRequestPipelineIfPipelineNotEmpty() throws Exception {
        final State state = new State();
        state.setRequestState(MessageState.READY);
        state.setResponseState(MessageState.READY);

        final Queue<PipelineEntry> pipeline = state.getRequestQueue();

        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest pipelinedRequest = new BasicHttpRequest("GET", "/");
        final PipelineEntry entry = new PipelineEntry(pipelinedRequest, pipelinedRequest,
                null, requestHandler, exchangeContext);
        pipeline.add(entry);

        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        Mockito.when(this.conn.getHttpRequest()).thenReturn(request);
        Mockito.when(this.requestHandler.processRequest(
                Matchers.eq(request), Matchers.any(HttpContext.class))).thenReturn(this.requestConsumer);
        Mockito.when(this.requestConsumer.getException()).thenReturn(null);
        final Object data = new Object();
        Mockito.when(this.requestConsumer.getResult()).thenReturn(data);

        this.protocolHandler.requestReceived(this.conn);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());

        final Incoming incoming = state.getIncoming();
        Assert.assertNull(incoming);

        Mockito.verify(this.requestConsumer).requestReceived(request);
        Mockito.verify(this.requestConsumer).requestCompleted(Matchers.<HttpContext>any());
        Mockito.verify(this.requestHandler, Mockito.never()).handle(
                Matchers.any(),
                Matchers.any(HttpAsyncExchange.class),
                Matchers.any(HttpContext.class));

        Assert.assertFalse(state.getRequestQueue().isEmpty());
        final PipelineEntry entry1 = state.getRequestQueue().remove();
        Assert.assertSame(entry, entry1);
        final PipelineEntry entry2 = state.getRequestQueue().remove();
        Assert.assertSame(request, entry2.getRequest());
        Assert.assertSame(data, entry2.getResult());
    }

    @Test
    public void testRequestNoMatchingHandler() throws Exception {
        final State state = new State();
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpRequest request = new BasicHttpRequest("POST", "/stuff");
        request.setHeader(HttpHeaders.CONTENT_LENGTH, "5");
        final NStringEntity entity = new NStringEntity("stuff");
        request.setEntity(entity);
        Mockito.when(this.conn.getHttpRequest()).thenReturn(request);
        Mockito.when(this.requestHandler.processRequest(
                Matchers.eq(request), Matchers.any(HttpContext.class))).thenReturn(this.requestConsumer);

        this.protocolHandler.requestReceived(this.conn);

        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());

        final Incoming incoming = state.getIncoming();
        Assert.assertNotNull(incoming);
        Assert.assertSame(request, incoming.getRequest());
        Assert.assertTrue(incoming.getHandler() instanceof NullRequestHandler);
    }

    @Test
    public void testEntityEnclosingRequest() throws Exception {
        final State state = new State();
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpRequest request = new BasicHttpRequest("POST", "/");
        request.setHeader(HttpHeaders.CONTENT_LENGTH, "5");
        final NStringEntity entity = new NStringEntity("stuff");
        request.setEntity(entity);
        Mockito.when(this.conn.getHttpRequest()).thenReturn(request);
        Mockito.when(this.requestHandler.processRequest(
                Matchers.eq(request), Matchers.any(HttpContext.class))).thenReturn(this.requestConsumer);

        this.protocolHandler.requestReceived(this.conn);

        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());

        final Incoming incoming = state.getIncoming();
        Assert.assertNotNull(incoming);
        Assert.assertSame(request, incoming.getRequest());
        Assert.assertSame(this.requestHandler, incoming.getHandler());
        Assert.assertSame(this.requestConsumer, incoming.getConsumer());

        final HttpContext exchangeContext = incoming.getContext();
        Assert.assertNotNull(exchangeContext);

        Assert.assertSame(request, exchangeContext.getAttribute(HttpCoreContext.HTTP_REQUEST));
        Assert.assertSame(this.conn, exchangeContext.getAttribute(HttpCoreContext.HTTP_CONNECTION));

        Mockito.verify(this.httpProcessor).process(request, exchangeContext);
        Mockito.verify(this.requestConsumer).requestReceived(request);
        Mockito.verify(this.conn, Mockito.never()).suspendInput();
    }

    @Test
    public void testEntityEnclosingRequestContinueWithoutVerification() throws Exception {
        final State state = new State();
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpRequest request = new BasicHttpRequest("POST", "/");
        request.setHeader(HttpHeaders.CONTENT_LENGTH, "5");
        request.setHeader(HttpHeaders.EXPECT, HeaderElements.CONTINUE);
        final NStringEntity entity = new NStringEntity("stuff");
        request.setEntity(entity);
        Mockito.when(this.conn.getHttpRequest()).thenReturn(request);
        Mockito.when(this.requestHandler.processRequest(
                Matchers.eq(request), Matchers.any(HttpContext.class))).thenReturn(this.requestConsumer);

        this.protocolHandler.requestReceived(this.conn);

        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());

        final Incoming incoming = state.getIncoming();
        Assert.assertNotNull(incoming);
        Assert.assertSame(request, incoming.getRequest());
        Assert.assertSame(this.requestHandler, incoming.getHandler());
        Assert.assertSame(this.requestConsumer, incoming.getConsumer());

        final HttpContext exchangeContext = incoming.getContext();
        Assert.assertNotNull(exchangeContext);

        Assert.assertSame(request, exchangeContext.getAttribute(HttpCoreContext.HTTP_REQUEST));
        Assert.assertSame(this.conn, exchangeContext.getAttribute(HttpCoreContext.HTTP_CONNECTION));

        Mockito.verify(this.httpProcessor).process(request, exchangeContext);
        Mockito.verify(this.requestConsumer).requestReceived(request);
        Mockito.verify(this.conn, Mockito.never()).suspendInput();
        Mockito.verify(this.conn).submitResponse(Matchers.argThat(new ArgumentMatcher<HttpResponse>() {

            @Override
            public boolean matches(final Object argument) {
                final int status = ((HttpResponse) argument).getCode();
                return status == 100;
            }

        }));
    }

    @Test
    public void testEntityEnclosingRequestExpectationVerification() throws Exception {
        final HttpAsyncExpectationVerifier expectationVerifier = Mockito.mock(HttpAsyncExpectationVerifier.class);
        this.protocolHandler = new HttpAsyncService(
                this.httpProcessor, this.reuseStrategy, this.responseFactory,
                this.handlerResolver, expectationVerifier);

        final State state = new State();
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpRequest request = new BasicHttpRequest("POST", "/");
        request.setHeader(HttpHeaders.CONTENT_LENGTH, "5");
        request.setHeader(HttpHeaders.EXPECT, HeaderElements.CONTINUE);
        final NStringEntity entity = new NStringEntity("stuff");
        request.setEntity(entity);
        Mockito.when(this.conn.getHttpRequest()).thenReturn(request);
        Mockito.when(this.requestHandler.processRequest(
                Matchers.eq(request), Matchers.any(HttpContext.class))).thenReturn(this.requestConsumer);

        this.protocolHandler.requestReceived(this.conn);

        Assert.assertEquals(MessageState.ACK_EXPECTED, state.getRequestState());
        Assert.assertEquals(MessageState.INIT, state.getResponseState());

        final Incoming incoming = state.getIncoming();
        Assert.assertNotNull(incoming);
        Assert.assertSame(request, incoming.getRequest());
        Assert.assertSame(this.requestHandler, incoming.getHandler());
        Assert.assertSame(this.requestConsumer, incoming.getConsumer());

        final HttpContext exchangeContext = incoming.getContext();
        Assert.assertNotNull(exchangeContext);

        Assert.assertSame(request, exchangeContext.getAttribute(HttpCoreContext.HTTP_REQUEST));
        Assert.assertSame(this.conn, exchangeContext.getAttribute(HttpCoreContext.HTTP_CONNECTION));

        Mockito.verify(this.httpProcessor).process(request, exchangeContext);
        Mockito.verify(this.requestConsumer).requestReceived(request);
        Mockito.verify(expectationVerifier).verify(
                Matchers.any(HttpAsyncExchange.class),
                Matchers.eq(exchangeContext));
    }

    @Test
    public void testRequestExpectationFailed() throws Exception {
        final State state = new State();
        state.setRequestState(MessageState.ACK_EXPECTED);

        final HttpContext exchangeContext = new BasicHttpContext();

        final HttpRequest request = new BasicHttpRequest("POST", "/");
        request.setHeader(HttpHeaders.CONTENT_LENGTH, "5");
        final NStringEntity entity = new NStringEntity("stuff");
        request.setEntity(entity);
        final Incoming incoming = new Incoming(request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);

        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpAsyncExchange httpexchanage = this.protocolHandler.new HttpAsyncExchangeImpl(
                incoming,
                new BasicHttpRequest("GET", "/"),
                new BasicHttpResponse(100, "Continue"),
                state, this.conn, exchangeContext);
        Assert.assertFalse(httpexchanage.isCompleted());

        Mockito.when(this.responseProducer.generateResponse()).thenReturn(new BasicHttpResponse(407, "Oppsie"));
        httpexchanage.submitResponse(this.responseProducer);
        Assert.assertTrue(httpexchanage.isCompleted());
        Assert.assertTrue(incoming.isEarlyResponse());

        Assert.assertEquals(MessageState.ACK_EXPECTED, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
        final Outgoing outgoing = state.getOutgoing();
        Assert.assertNotNull(outgoing);
        Assert.assertSame(this.responseProducer, outgoing.getProducer());

        Mockito.verify(this.conn).requestOutput();

        try {
            httpexchanage.submitResponse();
            Assert.fail("IllegalStateException expected");
        } catch (final IllegalStateException ex) {
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testRequestExpectationFailedInvalidResponseProducer() throws Exception {
        final State state = new State();
        state.setRequestState(MessageState.ACK_EXPECTED);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpAsyncExchange httpexchanage = protocolHandler.new HttpAsyncExchangeImpl(
                new BasicHttpRequest("GET", "/"),
                new BasicHttpResponse(200, "OK"),
                state, this.conn, exchangeContext);
        httpexchanage.submitResponse(null);
    }

    @Test
    public void testRequestExpectationNoHandshakeIfResponseInitiated() throws Exception {
        final State state = new State();
        state.setRequestState(MessageState.READY);
        state.setResponseState(MessageState.INIT);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpRequest request = new BasicHttpRequest("POST", "/");
        request.setHeader(HttpHeaders.CONTENT_LENGTH, "5");
        request.setHeader(HttpHeaders.EXPECT, HeaderElements.CONTINUE);
        final NStringEntity entity = new NStringEntity("stuff");
        request.setEntity(entity);

        Mockito.when(this.conn.getHttpRequest()).thenReturn(request);
        Mockito.when(this.requestHandler.processRequest(
                Matchers.eq(request), Matchers.any(HttpContext.class))).thenReturn(this.requestConsumer);

        this.protocolHandler.requestReceived(this.conn);

        Mockito.verify(this.requestConsumer).requestReceived(request);

        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
        Assert.assertEquals(MessageState.INIT, state.getResponseState());
    }

    @Test
    public void testRequestExpectationNoHandshakeIfPipelineNotEmpty() throws Exception {
        final State state = new State();
        state.setRequestState(MessageState.READY);
        state.setResponseState(MessageState.READY);

        final Queue<PipelineEntry> pipeline = state.getRequestQueue();

        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest pipelinedRequest = new BasicHttpRequest("GET", "/");
        final PipelineEntry entry = new PipelineEntry(pipelinedRequest, pipelinedRequest,
                null, requestHandler, exchangeContext);
        pipeline.add(entry);

        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpRequest request = new BasicHttpRequest("POST", "/");
        request.setHeader(HttpHeaders.CONTENT_LENGTH, "5");
        request.setHeader(HttpHeaders.EXPECT, HeaderElements.CONTINUE);
        final NStringEntity entity = new NStringEntity("stuff");
        request.setEntity(entity);

        Mockito.when(this.conn.getHttpRequest()).thenReturn(request);
        Mockito.when(this.requestHandler.processRequest(
                Matchers.eq(request), Matchers.any(HttpContext.class))).thenReturn(this.requestConsumer);

        this.protocolHandler.requestReceived(this.conn);

        Mockito.verify(this.requestConsumer).requestReceived(request);

        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
    }

    @Test
    public void testRequestExpectationNoHandshakeIfMoreInputAvailable() throws Exception {
        final State state = new State();
        state.setRequestState(MessageState.READY);
        state.setResponseState(MessageState.READY);

        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        this.conn = Mockito.mock(NHttpServerConnection.class,
                Mockito.withSettings().extraInterfaces(SessionBufferStatus.class));

        final HttpRequest request = new BasicHttpRequest("POST", "/");
        request.setHeader(HttpHeaders.CONTENT_LENGTH, "5");
        request.setHeader(HttpHeaders.EXPECT, HeaderElements.CONTINUE);
        final NStringEntity entity = new NStringEntity("stuff");
        request.setEntity(entity);

        Mockito.when(this.conn.getContext()).thenReturn(this.connContext);
        Mockito.when(this.conn.getHttpRequest()).thenReturn(request);
        Mockito.when(this.requestHandler.processRequest(
                Matchers.eq(request), Matchers.any(HttpContext.class))).thenReturn(this.requestConsumer);
        Mockito.when(((SessionBufferStatus) this.conn).hasBufferedInput()).thenReturn(Boolean.TRUE);

        this.protocolHandler.requestReceived(this.conn);

        Mockito.verify(this.requestConsumer).requestReceived(request);

        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
    }

    @Test
    public void testRequestContinue() throws Exception {
        final State state = new State();
        state.setRequestState(MessageState.ACK_EXPECTED);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpAsyncExchange httpexchanage = protocolHandler.new HttpAsyncExchangeImpl(
                new BasicHttpRequest("GET", "/"),
                new BasicHttpResponse(100, "Continue"),
                state, this.conn, exchangeContext);
        Assert.assertFalse(httpexchanage.isCompleted());
        httpexchanage.submitResponse();
        Assert.assertTrue(httpexchanage.isCompleted());

        final Outgoing outgoing = state.getOutgoing();
        Assert.assertNotNull(outgoing);
        final HttpAsyncResponseProducer responseProducer = outgoing.getProducer();
        Assert.assertNotNull(responseProducer);
        Assert.assertEquals(MessageState.ACK_EXPECTED, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
        final HttpResponse response = responseProducer.generateResponse();
        Assert.assertEquals(HttpStatus.SC_CONTINUE, response.getCode());

        Mockito.verify(this.conn).requestOutput();

        try {
            httpexchanage.submitResponse(this.responseProducer);
            Assert.fail("IllegalStateException expected");
        } catch (final IllegalStateException ex) {
        }
    }

    @Test
    public void testRequestContent() throws Exception {
        final State state = new State();
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("POST", "/");
        request.setHeader(HttpHeaders.CONTENT_LENGTH, "5");
        final NStringEntity entity = new NStringEntity("stuff");
        request.setEntity(entity);
        state.setRequestState(MessageState.BODY_STREAM);
        final Incoming incoming = new Incoming(request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);
        Mockito.when(this.decoder.isCompleted()).thenReturn(Boolean.FALSE);

        this.protocolHandler.inputReady(conn, this.decoder);

        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Mockito.verify(this.requestConsumer).consumeContent(this.decoder, this.conn);
        Mockito.verify(this.conn, Mockito.never()).suspendInput();
    }

    @Test
    public void testRequestContentCompleted() throws Exception {
        final State state = new State();
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("POST", "/");
        request.setHeader(HttpHeaders.CONTENT_LENGTH, "5");
        final NStringEntity entity = new NStringEntity("stuff");
        request.setEntity(entity);
        state.setRequestState(MessageState.BODY_STREAM);
        final Incoming incoming = new Incoming(request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);
        Mockito.when(this.decoder.isCompleted()).thenReturn(Boolean.TRUE);
        Mockito.when(this.requestConsumer.getException()).thenReturn(null);
        final Object data = new Object();
        Mockito.when(this.requestConsumer.getResult()).thenReturn(data);

        this.protocolHandler.inputReady(conn, this.decoder);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Mockito.verify(this.requestConsumer).consumeContent(this.decoder, this.conn);
        Mockito.verify(this.requestConsumer).requestCompleted(exchangeContext);
        Mockito.verify(this.conn).requestOutput();

        final PipelineEntry entry = state.getRequestQueue().poll();
        Assert.assertNotNull(entry);
        Assert.assertSame(request, entry.getRequest());
        Assert.assertSame(requestHandler, entry.getHandler());
        Assert.assertNotNull(entry.getResult());
        Assert.assertNull(entry.getException());
    }

    @Test
    public void testRequestCompletedWithException() throws Exception {
        final State state = new State();
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("POST", "/");
        request.setHeader(HttpHeaders.CONTENT_LENGTH, "5");
        final NStringEntity entity = new NStringEntity("stuff");
        request.setEntity(entity);
        state.setRequestState(MessageState.BODY_STREAM);
        final Incoming incoming = new Incoming(request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);
        Mockito.when(this.decoder.isCompleted()).thenReturn(Boolean.TRUE);
        Mockito.when(this.requestConsumer.getException()).thenReturn(new HttpException());
        Mockito.when(this.requestConsumer.getResult()).thenReturn(null);

        this.protocolHandler.inputReady(conn, this.decoder);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Mockito.verify(this.requestConsumer).consumeContent(this.decoder, this.conn);
        Mockito.verify(this.requestConsumer).requestCompleted(exchangeContext);
        Mockito.verify(this.conn).requestOutput();

        final PipelineEntry entry = state.getRequestQueue().poll();
        Assert.assertNotNull(entry);
        Assert.assertSame(request, entry.getRequest());
        Assert.assertSame(requestHandler, entry.getHandler());
        Assert.assertNull(entry.getResult());
        Assert.assertNotNull(entry.getException());
    }

    @Test
    public void testRequestContentEarlyResponse() throws Exception {
        final State state = new State();
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("POST", "/");
        request.setHeader(HttpHeaders.CONTENT_LENGTH, "5");
        final NStringEntity entity = new NStringEntity("stuff");
        request.setEntity(entity);
        state.setRequestState(MessageState.BODY_STREAM);
        final Incoming incoming = new Incoming(request, this.requestHandler, this.requestConsumer, exchangeContext);
        incoming.setEarlyResponse(true);
        state.setIncoming(incoming);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);
        Mockito.when(this.decoder.isCompleted()).thenReturn(Boolean.FALSE);

        this.protocolHandler.inputReady(conn, this.decoder);

        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Mockito.verify(this.decoder).read(Mockito.<ByteBuffer>any());
        Mockito.verify(this.requestConsumer, Mockito.never()).consumeContent(this.decoder, this.conn);
        Mockito.verify(this.requestConsumer, Mockito.never()).requestCompleted(exchangeContext);
        Mockito.verify(this.requestConsumer, Mockito.never()).close();
    }

    @Test
    public void testRequestContentCompletedEarlyResponse() throws Exception {
        final State state = new State();
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("POST", "/");
        request.setHeader(HttpHeaders.CONTENT_LENGTH, "5");
        final NStringEntity entity = new NStringEntity("stuff");
        request.setEntity(entity);
        state.setRequestState(MessageState.BODY_STREAM);
        final Incoming incoming = new Incoming(request, this.requestHandler, this.requestConsumer, exchangeContext);
        incoming.setEarlyResponse(true);
        state.setIncoming(incoming);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);
        Mockito.when(this.decoder.isCompleted()).thenReturn(Boolean.TRUE);
        Mockito.when(this.requestConsumer.isDone()).thenReturn(Boolean.TRUE);

        this.protocolHandler.inputReady(conn, this.decoder);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
        Assert.assertEquals(null, state.getIncoming());

        Mockito.verify(this.decoder).read(Mockito.<ByteBuffer>any());
        Mockito.verify(this.requestConsumer, Mockito.never()).consumeContent(this.decoder, this.conn);
        Mockito.verify(this.requestConsumer, Mockito.never()).requestCompleted(exchangeContext);
        Mockito.verify(this.requestConsumer).close();
    }

    @Test
    public void testBasicResponse() throws Exception {

        final State state = new State();
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final Incoming incoming = new Incoming(
                request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        state.setRequestState(MessageState.COMPLETED);
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        final Outgoing outgoing = new Outgoing(
                request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        state.setResponseState(MessageState.INIT);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);
        Mockito.when(this.reuseStrategy.keepAlive(request, response, exchangeContext)).thenReturn(Boolean.TRUE);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Mockito.verify(this.httpProcessor).process(response, exchangeContext);
        Mockito.verify(this.conn).submitResponse(response);
        Mockito.verify(this.responseProducer).responseCompleted(exchangeContext);
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testBasicResponseWithPipelining() throws Exception {
        final State state = new State();
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final Incoming incoming = new Incoming(request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        state.setRequestState(MessageState.COMPLETED);
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        final Outgoing outgoing = new Outgoing(request, response, this.responseProducer, exchangeContext);
        response.setEntity(new NStringEntity("stuff"));
        state.setOutgoing(outgoing);

        final Queue<PipelineEntry> pipeline = state.getRequestQueue();

        final HttpContext exchangeContext2 = new BasicHttpContext();
        final HttpRequest pipelinedRequest = new BasicHttpRequest("GET", "/");
        final PipelineEntry entry = new PipelineEntry(pipelinedRequest, pipelinedRequest,
                null, requestHandler, exchangeContext2);
        pipeline.add(entry);

        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);
        Mockito.when(this.reuseStrategy.keepAlive(request, response, exchangeContext)).thenReturn(Boolean.TRUE);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.BODY_STREAM, state.getResponseState());

        Mockito.verify(this.httpProcessor).process(response, exchangeContext);
        Mockito.verify(this.conn).suspendOutput();
        Mockito.verify(this.conn).submitResponse(response);
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testBasicResponseNoKeepAlive() throws Exception {
        final State state = new State();
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final Incoming incoming = new Incoming(request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        state.setRequestState(MessageState.COMPLETED);
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        final Outgoing outgoing = new Outgoing(request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        state.setResponseState(MessageState.INIT);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);
        Mockito.when(this.reuseStrategy.keepAlive(request, response, exchangeContext)).thenReturn(Boolean.FALSE);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Mockito.verify(this.httpProcessor).process(response, exchangeContext);
        Mockito.verify(this.conn).submitResponse(response);
        Mockito.verify(this.responseProducer).responseCompleted(exchangeContext);
        Mockito.verify(this.conn).close();
    }

    @Test
    public void testEntityEnclosingResponse() throws Exception {
        final State state = new State();
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final Incoming incoming = new Incoming(request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        state.setRequestState(MessageState.COMPLETED);
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        final Outgoing outgoing = new Outgoing(request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        state.setResponseState(MessageState.INIT);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        response.setEntity(new NStringEntity("stuff"));
        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.COMPLETED, state.getRequestState());
        Assert.assertEquals(MessageState.BODY_STREAM, state.getResponseState());
        Assert.assertEquals("[incoming COMPLETED GET / HTTP/1.1; outgoing BODY_STREAM HTTP/1.1 200 OK]",
                state.toString());

        Mockito.verify(this.httpProcessor).process(response, exchangeContext);
        Mockito.verify(this.conn).submitResponse(response);
        Mockito.verify(this.responseProducer, Mockito.never()).responseCompleted(exchangeContext);
    }

    @Test
    public void testResponseToHead() throws Exception {
        final State state = new State();
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("HEAD", "/");
        final Incoming incoming = new Incoming(request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        state.setRequestState(MessageState.COMPLETED);
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        final Outgoing outgoing = new Outgoing(request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        state.setResponseState(MessageState.INIT);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        response.setEntity(new NStringEntity("stuff"));
        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);
        Mockito.when(this.reuseStrategy.keepAlive(request, response, exchangeContext)).thenReturn(Boolean.TRUE);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Mockito.verify(this.httpProcessor).process(response, exchangeContext);
        Mockito.verify(this.conn).submitResponse(response);
        Mockito.verify(this.responseProducer).responseCompleted(exchangeContext);
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testResponseNotModified() throws Exception {
        final State state = new State();
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("HEAD", "/");
        final Incoming incoming = new Incoming(request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        state.setRequestState(MessageState.COMPLETED);
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_NOT_MODIFIED, "Not modified");
        final Outgoing outgoing = new Outgoing(request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        state.setResponseState(MessageState.INIT);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        response.setEntity(new NStringEntity("stuff"));
        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);
        Mockito.when(this.reuseStrategy.keepAlive(request, response, exchangeContext)).thenReturn(Boolean.TRUE);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Mockito.verify(this.httpProcessor).process(response, exchangeContext);
        Mockito.verify(this.conn).submitResponse(response);
        Mockito.verify(this.responseProducer).responseCompleted(exchangeContext);
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testResponseContinue() throws Exception {
        final State state = new State();
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("POST", "/");
        request.setHeader(HttpHeaders.CONTENT_LENGTH, "5");
        final NStringEntity entity = new NStringEntity("stuff");
        request.setEntity(entity);
        final Incoming incoming = new Incoming(request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        state.setRequestState(MessageState.ACK_EXPECTED);
        final HttpResponse response = new BasicHttpResponse(HttpStatus.SC_CONTINUE, "Continue");
        final Outgoing outgoing = new Outgoing(request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        state.setResponseState(MessageState.INIT);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.ACK_EXPECTED, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
        Assert.assertEquals(null, state.getOutgoing());

        Mockito.verify(this.conn).submitResponse(Matchers.argThat(new ArgumentMatcher<HttpResponse>() {

            @Override
            public boolean matches(final Object argument) {
                final int status = ((HttpResponse) argument).getCode();
                return status == 100;
            }

        }));
    }

    @Test
    public void testResponseFailedExpectation() throws Exception {
        final State state = new State();
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("POST", "/");
        request.setHeader(HttpHeaders.CONTENT_LENGTH, "5");
        final NStringEntity entity = new NStringEntity("stuff");
        request.setEntity(entity);
        final Incoming incoming = new Incoming(request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        state.setRequestState(MessageState.ACK_EXPECTED);
        final HttpResponse response = new BasicHttpResponse(417, "Expectation failed");
        final Outgoing outgoing = new Outgoing(request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        state.setResponseState(MessageState.INIT);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        response.setEntity(new NStringEntity("stuff"));
        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.ACK_EXPECTED, state.getRequestState());
        Assert.assertEquals(MessageState.BODY_STREAM, state.getResponseState());
        Assert.assertSame(outgoing, state.getOutgoing());

        Mockito.verify(this.httpProcessor).process(response, exchangeContext);
        Mockito.verify(this.conn).submitResponse(response);
        Mockito.verify(this.responseProducer, Mockito.never()).responseCompleted(exchangeContext);
    }

    @Test
    public void testResponsePipelinedEmpty() throws Exception {
        final State state = new State();

        state.setRequestState(MessageState.READY);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
        Assert.assertNull(state.getOutgoing());

        Mockito.verify(conn).suspendOutput();
        Mockito.verifyNoMoreInteractions(requestHandler);
    }

    @Test
    public void testResponseHandlePipelinedRequest() throws Exception {
        final State state = new State();
        final Queue<PipelineEntry> pipeline = state.getRequestQueue();

        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final PipelineEntry entry = new PipelineEntry(request, request, null, requestHandler, exchangeContext);
        pipeline.add(entry);

        state.setRequestState(MessageState.READY);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.INIT, state.getResponseState());
        Assert.assertNull(state.getOutgoing());

        final ArgumentCaptor<HttpAsyncExchange> argCaptor = ArgumentCaptor.forClass(HttpAsyncExchange.class);
        Mockito.verify(this.requestHandler).handle(Matchers.same(request),
                argCaptor.capture(), Matchers.same(exchangeContext));
        final HttpAsyncExchange exchange = argCaptor.getValue();

        Assert.assertNotNull(exchange);
        Assert.assertSame(request, exchange.getRequest());
        Assert.assertNotNull(exchange.getResponse());
        Assert.assertEquals(200, exchange.getResponse().getCode());
    }

    @Test
    public void testResponseHandleFailedPipelinedRequest() throws Exception {
        final State state = new State();
        final Queue<PipelineEntry> pipeline = state.getRequestQueue();

        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final Exception ex = new Exception("Opppsie");
        final PipelineEntry entry = new PipelineEntry(request, null, ex, requestHandler, exchangeContext);
        pipeline.add(entry);

        state.setRequestState(MessageState.READY);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.BODY_STREAM, state.getResponseState());

        final Outgoing outgoing = state.getOutgoing();
        Assert.assertNotNull(outgoing.getProducer());
        final HttpResponse response = outgoing.getResponse();
        Assert.assertNotNull(response);
        Assert.assertEquals(500, response.getCode());

        Mockito.verify(this.requestHandler, Mockito.never()).handle(Matchers.<HttpRequest>any(),
                Matchers.<HttpAsyncExchange>any(), Matchers.<HttpContext>any());
        Mockito.verify(this.conn).submitResponse(Matchers.same(response));
    }

    @Test
    public void testResponseTrigger() throws Exception {
        final State state = new State();
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseState(MessageState.READY);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpAsyncExchange httpexchanage = protocolHandler.new HttpAsyncExchangeImpl(
                new BasicHttpRequest("GET", "/"),
                new BasicHttpResponse(200, "OK"),
                state, this.conn, exchangeContext);
        Assert.assertFalse(httpexchanage.isCompleted());

        Mockito.when(this.responseProducer.generateResponse()).thenReturn(new BasicHttpResponse(200, "OK"));

        httpexchanage.submitResponse(this.responseProducer);
        Assert.assertTrue(httpexchanage.isCompleted());

        Assert.assertEquals(MessageState.COMPLETED, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
        final Outgoing outgoing = state.getOutgoing();
        Assert.assertNotNull(outgoing);
        Assert.assertSame(this.responseProducer, outgoing.getProducer());

        Mockito.verify(this.conn).requestOutput();

        try {
            httpexchanage.submitResponse(Mockito.mock(HttpAsyncResponseProducer.class));
            Assert.fail("IllegalStateException expected");
        } catch (final IllegalStateException ex) {
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testResponseTriggerInvalidResponseProducer() throws Exception {
        final State state = new State();
        state.setRequestState(MessageState.ACK_EXPECTED);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpAsyncExchange httpexchanage = protocolHandler.new HttpAsyncExchangeImpl(
                new BasicHttpRequest("GET", "/"),
                new BasicHttpResponse(200, "OK"),
                state, this.conn, exchangeContext);
        httpexchanage.submitResponse(null);
    }

    @Test
    public void testResponseContent() throws Exception {
        final State state = new State();
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseState(MessageState.BODY_STREAM);
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.setEntity(new NStringEntity("stuff"));
        final Outgoing outgoing = new Outgoing(request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);
        Mockito.when(this.encoder.isCompleted()).thenReturn(Boolean.FALSE);

        this.protocolHandler.outputReady(conn, this.encoder);

        Assert.assertEquals(MessageState.COMPLETED, state.getRequestState());
        Assert.assertEquals(MessageState.BODY_STREAM, state.getResponseState());

        Mockito.verify(this.responseProducer).produceContent(this.encoder, this.conn);
        Mockito.verify(this.conn, Mockito.never()).requestInput();
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testResponseContentCompleted() throws Exception {
        final State state = new State();
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseState(MessageState.BODY_STREAM);
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.setEntity(new NStringEntity("stuff"));
        final Outgoing outgoing = new Outgoing(request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);
        Mockito.when(this.encoder.isCompleted()).thenReturn(true);
        Mockito.when(this.reuseStrategy.keepAlive(request, response, exchangeContext)).thenReturn(Boolean.TRUE);

        this.protocolHandler.outputReady(conn, this.encoder);

        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Mockito.verify(this.responseProducer).produceContent(this.encoder, this.conn);
        Mockito.verify(this.responseProducer).responseCompleted(exchangeContext);
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testResponseContentCompletedNoKeepAlive() throws Exception {
        final State state = new State();
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseState(MessageState.BODY_STREAM);
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        response.setEntity(new NStringEntity("stuff"));
        final Outgoing outgoing = new Outgoing(request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);
        Mockito.when(this.encoder.isCompleted()).thenReturn(true);
        Mockito.when(this.reuseStrategy.keepAlive(request, response, exchangeContext)).thenReturn(Boolean.FALSE);

        this.protocolHandler.outputReady(conn, this.encoder);

        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Mockito.verify(this.responseProducer).produceContent(this.encoder, this.conn);
        Mockito.verify(this.responseProducer).responseCompleted(exchangeContext);
        Mockito.verify(this.conn).close();
    }

    @Test
    public void testEndOfInput() throws Exception {

        Mockito.when(this.conn.getSocketTimeout()).thenReturn(1000);

        this.protocolHandler.endOfInput(this.conn);

        Mockito.verify(this.conn, Mockito.never()).setSocketTimeout(Matchers.anyInt());
        Mockito.verify(this.conn).close();
    }

    @Test
    public void testEndOfInputNoTimeout() throws Exception {

        Mockito.when(this.conn.getSocketTimeout()).thenReturn(0);

        this.protocolHandler.endOfInput(this.conn);

        Mockito.verify(this.conn).setSocketTimeout(1000);
        Mockito.verify(this.conn).close();
    }

    @Test
    public void testTimeoutActiveConnection() throws Exception {
        final State state = new State();
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);
        Mockito.when(this.conn.getStatus()).thenReturn(NHttpConnection.ACTIVE, NHttpConnection.CLOSED);

        this.protocolHandler.timeout(this.conn);

        Mockito.verify(this.conn).close();
        Mockito.verify(this.conn, Mockito.never()).setSocketTimeout(Matchers.anyInt());
    }

    @Test
    public void testTimeoutActiveConnectionBufferedData() throws Exception {
        final State state = new State();
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);
        Mockito.when(this.conn.getStatus()).thenReturn(NHttpConnection.ACTIVE, NHttpConnection.CLOSING);

        this.protocolHandler.timeout(this.conn);

        Mockito.verify(this.conn).close();
        Mockito.verify(this.conn).setSocketTimeout(250);
    }

    @Test
    public void testTimeoutClosingConnection() throws Exception {
        final State state = new State();
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final Incoming incoming = new Incoming(request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        final Outgoing outgoing = new Outgoing(request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);
        Mockito.when(this.conn.getStatus()).thenReturn(NHttpConnection.CLOSING);

        this.protocolHandler.timeout(this.conn);

        Mockito.verify(this.conn).shutdown();
        Mockito.verify(this.requestConsumer).failed(Matchers.any(SocketTimeoutException.class));
        Mockito.verify(this.requestConsumer).close();
        Mockito.verify(this.responseProducer).failed(Matchers.any(SocketTimeoutException.class));
        Mockito.verify(this.responseProducer).close();
    }

}

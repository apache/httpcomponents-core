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

package org.apache.http.nio.protocol;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Queue;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.concurrent.Cancellable;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.nio.protocol.HttpAsyncService.Incoming;
import org.apache.http.nio.protocol.HttpAsyncService.Outgoing;
import org.apache.http.nio.protocol.HttpAsyncService.PipelineEntry;
import org.apache.http.nio.protocol.HttpAsyncService.State;
import org.apache.http.nio.reactor.SessionBufferStatus;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpProcessor;
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

        final HttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        final Incoming incoming = new Incoming(
                request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        final Outgoing outgoing = new Outgoing(
                request, response, this.responseProducer, exchangeContext);
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
        final HttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        final Incoming incoming = new Incoming(
                request, this.requestHandler, this.requestConsumer, exchangeContext);
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
        Assert.assertEquals(500, outgoing.getResponse().getStatusLine().getStatusCode());

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
        final HttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        final Incoming incoming = new Incoming(
                request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        final Outgoing outgoing = new Outgoing(
                request, response, this.responseProducer, exchangeContext);
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
        final HttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        final Incoming incoming = new Incoming(
                request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        final Outgoing outgoing = new Outgoing(
                request, response, this.responseProducer, exchangeContext);
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
        final HttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        final Incoming incoming = new Incoming(
                request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        final Outgoing outgoing = new Outgoing(
                request, response, this.responseProducer, exchangeContext);
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

        final HttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
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

        final PipelineEntry entry = state.getPipeline().poll();
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

        final HttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
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

        Assert.assertFalse(state.getPipeline().isEmpty());
        final PipelineEntry entry = state.getPipeline().remove();
        Assert.assertSame(request, entry.getRequest());
        Assert.assertSame(data, entry.getResult());
    }

    @Test
    public void testRequestPipelineIfPipelineNotEmpty() throws Exception {
        final State state = new State();
        state.setRequestState(MessageState.READY);
        state.setResponseState(MessageState.READY);

        final Queue<PipelineEntry> pipeline = state.getPipeline();

        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest pipelinedRequest = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        final PipelineEntry entry = new PipelineEntry(pipelinedRequest, pipelinedRequest,
                null, requestHandler, exchangeContext);
        pipeline.add(entry);

        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
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

        Assert.assertFalse(state.getPipeline().isEmpty());
        final PipelineEntry entry1 = state.getPipeline().remove();
        Assert.assertSame(entry, entry1);
        final PipelineEntry entry2 = state.getPipeline().remove();
        Assert.assertSame(request, entry2.getRequest());
        Assert.assertSame(data, entry2.getResult());
    }

    @Test
    public void testRequestNoMatchingHandler() throws Exception {
        final State state = new State();
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST",
                "/stuff", HttpVersion.HTTP_1_1);
        request.setEntity(new NStringEntity("stuff"));
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

        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
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

        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        request.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);
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
                final int status = ((HttpResponse) argument).getStatusLine().getStatusCode();
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

        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        request.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);
        Mockito.when(this.conn.getHttpRequest()).thenReturn(request);
        Mockito.when(this.requestHandler.processRequest(
                Matchers.eq(request), Matchers.any(HttpContext.class))).thenReturn(this.requestConsumer);

        this.protocolHandler.requestReceived(this.conn);

        Assert.assertEquals(MessageState.ACK_EXPECTED, state.getRequestState());
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
        Mockito.verify(this.conn).suspendInput();
        Mockito.verify(expectationVerifier).verify(
                Matchers.any(HttpAsyncExchange.class),
                Matchers.eq(exchangeContext));
    }

    @Test
    public void testRequestExpectationFailed() throws Exception {
        final State state = new State();
        state.setRequestState(MessageState.ACK_EXPECTED);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpAsyncExchange httpexchanage = protocolHandler.new HttpAsyncExchangeImpl(
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1),
                new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK"),
                state, this.conn, exchangeContext);
        Assert.assertFalse(httpexchanage.isCompleted());
        httpexchanage.submitResponse(this.responseProducer);
        Assert.assertTrue(httpexchanage.isCompleted());

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
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1),
                new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK"),
                state, this.conn, exchangeContext);
        httpexchanage.submitResponse(null);
    }

    @Test
    public void testRequestExpectationNoHandshakeIfResponseInitiated() throws Exception {
        final State state = new State();
        state.setRequestState(MessageState.READY);
        state.setResponseState(MessageState.INIT);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        request.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);

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

        final Queue<PipelineEntry> pipeline = state.getPipeline();

        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest pipelinedRequest = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        final PipelineEntry entry = new PipelineEntry(pipelinedRequest, pipelinedRequest,
                null, requestHandler, exchangeContext);
        pipeline.add(entry);

        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        request.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);

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

        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        request.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);

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
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1),
                new BasicHttpResponse(HttpVersion.HTTP_1_1, 100, "Continue"),
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
        Assert.assertEquals(HttpStatus.SC_CONTINUE, response.getStatusLine().getStatusCode());

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
        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        state.setRequestState(MessageState.BODY_STREAM);
        final Incoming incoming = new Incoming(
                request, this.requestHandler, this.requestConsumer, exchangeContext);
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
        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        state.setRequestState(MessageState.BODY_STREAM);
        final Incoming incoming = new Incoming(
                request, this.requestHandler, this.requestConsumer, exchangeContext);
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

        final PipelineEntry entry = state.getPipeline().poll();
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
        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        state.setRequestState(MessageState.BODY_STREAM);
        final Incoming incoming = new Incoming(
                request, this.requestHandler, this.requestConsumer, exchangeContext);
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

        final PipelineEntry entry = state.getPipeline().poll();
        Assert.assertNotNull(entry);
        Assert.assertSame(request, entry.getRequest());
        Assert.assertSame(requestHandler, entry.getHandler());
        Assert.assertNull(entry.getResult());
        Assert.assertNotNull(entry.getException());
    }

    @Test
    public void testBasicResponse() throws Exception {
        final State state = new State();
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        final Incoming incoming = new Incoming(
                request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        state.setRequestState(MessageState.COMPLETED);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        final Outgoing outgoing = new Outgoing(
                request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        state.setResponseState(MessageState.INIT);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);
        Mockito.when(this.reuseStrategy.keepAlive(response, exchangeContext)).thenReturn(Boolean.TRUE);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Mockito.verify(this.httpProcessor).process(response, exchangeContext);
        Mockito.verify(this.conn).submitResponse(response);
        Mockito.verify(this.responseProducer).responseCompleted(exchangeContext);
        Mockito.verify(this.conn).requestInput();
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testBasicResponseWithPipelining() throws Exception {
        final State state = new State();
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        final Incoming incoming = new Incoming(
                request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        state.setRequestState(MessageState.COMPLETED);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        final Outgoing outgoing = new Outgoing(
                request, response, this.responseProducer, exchangeContext);
        response.setEntity(new NStringEntity("stuff"));
        state.setOutgoing(outgoing);

        final Queue<PipelineEntry> pipeline = state.getPipeline();

        final HttpContext exchangeContext2 = new BasicHttpContext();
        final HttpRequest pipelinedRequest = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        final PipelineEntry entry = new PipelineEntry(pipelinedRequest, pipelinedRequest,
                null, requestHandler, exchangeContext2);
        pipeline.add(entry);

        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);
        Mockito.when(this.reuseStrategy.keepAlive(response, exchangeContext)).thenReturn(Boolean.TRUE);

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
        final HttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        final Incoming incoming = new Incoming(
                request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        state.setRequestState(MessageState.COMPLETED);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        final Outgoing outgoing = new Outgoing(
                request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        state.setResponseState(MessageState.INIT);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);
        Mockito.when(this.reuseStrategy.keepAlive(response, exchangeContext)).thenReturn(Boolean.FALSE);

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
        final HttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        final Incoming incoming = new Incoming(
                request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        state.setRequestState(MessageState.COMPLETED);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        final Outgoing outgoing = new Outgoing(
                request, response, this.responseProducer, exchangeContext);
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
        final HttpRequest request = new BasicHttpRequest("HEAD", "/", HttpVersion.HTTP_1_1);
        final Incoming incoming = new Incoming(
                request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        state.setRequestState(MessageState.COMPLETED);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        final Outgoing outgoing = new Outgoing(
                request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        state.setResponseState(MessageState.INIT);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        response.setEntity(new NStringEntity("stuff"));
        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);
        Mockito.when(this.reuseStrategy.keepAlive(response, exchangeContext)).thenReturn(Boolean.TRUE);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Mockito.verify(this.httpProcessor).process(response, exchangeContext);
        Mockito.verify(this.conn).submitResponse(response);
        Mockito.verify(this.responseProducer).responseCompleted(exchangeContext);
        Mockito.verify(this.conn).requestInput();
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testResponseNotModified() throws Exception {
        final State state = new State();
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("HEAD", "/", HttpVersion.HTTP_1_1);
        final Incoming incoming = new Incoming(
                request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        state.setRequestState(MessageState.COMPLETED);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_NOT_MODIFIED, "Not modified");
        final Outgoing outgoing = new Outgoing(
                request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        state.setResponseState(MessageState.INIT);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        response.setEntity(new NStringEntity("stuff"));
        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);
        Mockito.when(this.reuseStrategy.keepAlive(response, exchangeContext)).thenReturn(Boolean.TRUE);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Mockito.verify(this.httpProcessor).process(response, exchangeContext);
        Mockito.verify(this.conn).submitResponse(response);
        Mockito.verify(this.responseProducer).responseCompleted(exchangeContext);
        Mockito.verify(this.conn).requestInput();
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testResponseContinue() throws Exception {
        final State state = new State();
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        final Incoming incoming = new Incoming(
                request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        state.setRequestState(MessageState.ACK_EXPECTED);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_CONTINUE, "Continue");
        final Outgoing outgoing = new Outgoing(
                request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Mockito.verify(this.conn).requestInput();
        Mockito.verify(this.conn).submitResponse(Matchers.argThat(new ArgumentMatcher<HttpResponse>() {

            @Override
            public boolean matches(final Object argument) {
                final int status = ((HttpResponse) argument).getStatusLine().getStatusCode();
                return status == 100;
            }

        }));
    }

    @Test
    public void testResponseFailedExpectation() throws Exception {
        final State state = new State();
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        final Incoming incoming = new Incoming(
                request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        state.setRequestState(MessageState.ACK_EXPECTED);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                417, "Expectation failed");
        final Outgoing outgoing = new Outgoing(
                request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        response.setEntity(new NStringEntity("stuff"));
        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.BODY_STREAM, state.getResponseState());

        Mockito.verify(this.conn).resetInput();
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
        final Queue<PipelineEntry> pipeline = state.getPipeline();

        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
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
        Assert.assertEquals(200, exchange.getResponse().getStatusLine().getStatusCode());
    }

    @Test
    public void testResponseHandleFailedPipelinedRequest() throws Exception {
        final State state = new State();
        final Queue<PipelineEntry> pipeline = state.getPipeline();

        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
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
        Assert.assertEquals(500, response.getStatusLine().getStatusCode());

        Mockito.verify(this.requestHandler, Mockito.never()).handle(Matchers.<HttpRequest>any(),
                Matchers.<HttpAsyncExchange>any(), Matchers.<HttpContext>any());
        Mockito.verify(this.conn).submitResponse(Matchers.same(response));
    }

    @Test(expected=HttpException.class)
    public void testInvalidResponseStatus() throws Exception {
        final State state = new State();
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        final Incoming incoming = new Incoming(
                request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseState(MessageState.INIT);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 112, "Something stupid");
        final Outgoing outgoing = new Outgoing(
                request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);
        Mockito.when(this.conn.isResponseSubmitted()).thenReturn(Boolean.FALSE);

        this.protocolHandler.responseReady(this.conn);
    }

    @Test(expected=HttpException.class)
    public void testInvalidResponseStatusToExpection() throws Exception {
        final State state = new State();
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        final Incoming incoming = new Incoming(
                request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        state.setRequestState(MessageState.ACK_EXPECTED);
        state.setResponseState(MessageState.READY);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.setEntity(new NStringEntity("stuff"));
        final Outgoing outgoing = new Outgoing(
                request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);
        Mockito.when(this.conn.isResponseSubmitted()).thenReturn(Boolean.FALSE);

        this.protocolHandler.responseReady(this.conn);
    }

    @Test
    public void testResponseTrigger() throws Exception {
        final State state = new State();
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseState(MessageState.READY);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpAsyncExchange httpexchanage = protocolHandler.new HttpAsyncExchangeImpl(
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1),
                new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK"),
                state, this.conn, exchangeContext);
        Assert.assertFalse(httpexchanage.isCompleted());
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
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1),
                new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK"),
                state, this.conn, exchangeContext);
        httpexchanage.submitResponse(null);
    }

    @Test
    public void testResponseContent() throws Exception {
        final State state = new State();
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseState(MessageState.BODY_STREAM);
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.setEntity(new NStringEntity("stuff"));
        final Outgoing outgoing = new Outgoing(
                request, response, this.responseProducer, exchangeContext);
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
        final HttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.setEntity(new NStringEntity("stuff"));
        final Outgoing outgoing = new Outgoing(
                request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);
        Mockito.when(this.encoder.isCompleted()).thenReturn(true);
        Mockito.when(this.reuseStrategy.keepAlive(response, exchangeContext)).thenReturn(Boolean.TRUE);

        this.protocolHandler.outputReady(conn, this.encoder);

        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Mockito.verify(this.responseProducer).produceContent(this.encoder, this.conn);
        Mockito.verify(this.responseProducer).responseCompleted(exchangeContext);
        Mockito.verify(this.conn).requestInput();
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testResponseContentCompletedNoKeepAlive() throws Exception {
        final State state = new State();
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseState(MessageState.BODY_STREAM);
        final HttpContext exchangeContext = new BasicHttpContext();
        final HttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.setEntity(new NStringEntity("stuff"));
        final Outgoing outgoing = new Outgoing(
                request, response, this.responseProducer, exchangeContext);
        state.setOutgoing(outgoing);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);
        Mockito.when(this.encoder.isCompleted()).thenReturn(true);
        Mockito.when(this.reuseStrategy.keepAlive(response, exchangeContext)).thenReturn(Boolean.FALSE);

        this.protocolHandler.outputReady(conn, this.encoder);

        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Mockito.verify(this.responseProducer).produceContent(this.encoder, this.conn);
        Mockito.verify(this.responseProducer).responseCompleted(exchangeContext);
        Mockito.verify(this.conn, Mockito.never()).requestInput();
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
        final HttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        final Incoming incoming = new Incoming(
                request, this.requestHandler, this.requestConsumer, exchangeContext);
        state.setIncoming(incoming);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        final Outgoing outgoing = new Outgoing(
                request, response, this.responseProducer, exchangeContext);
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

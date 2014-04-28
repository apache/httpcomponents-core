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

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpException;
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
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.nio.protocol.HttpAsyncService.State;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpProcessor;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
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
        Assert.assertEquals("request state: READY; request: ; " +
                "response state: READY; response: ;", state.toString());
    }

    @Test
    public void testClosed() throws Exception {
        final State state = new HttpAsyncService.State();
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseState(MessageState.COMPLETED);
        state.setRequestConsumer(this.requestConsumer);
        state.setResponseProducer(this.responseProducer);
        state.setCancellable(this.cancellable);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        this.protocolHandler.closed(this.conn);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
        Mockito.verify(this.requestConsumer).close();
        Mockito.verify(this.responseProducer).close();
        Mockito.verify(this.cancellable).cancel();
    }

    @Test
    public void testExceptionHandling() throws Exception {
        final State state = new HttpAsyncService.State();
        state.setRequestState(MessageState.READY);
        state.setResponseState(MessageState.READY);
        state.setRequestConsumer(this.requestConsumer);
        state.setCancellable(this.cancellable);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpException httpex = new HttpException();
        this.protocolHandler.exception(this.conn, httpex);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.BODY_STREAM, state.getResponseState());
        Assert.assertNotNull(state.getResponseProducer());
        Assert.assertNotNull(state.getResponse());
        Assert.assertEquals(500, state.getResponse().getStatusLine().getStatusCode());

        Mockito.verify(this.requestConsumer).failed(httpex);
        Mockito.verify(this.requestConsumer).close();
        Mockito.verify(this.cancellable).cancel();
        Mockito.verify(this.conn, Mockito.never()).shutdown();
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testExceptionHandlingRuntimeException() throws Exception {
        final State state = new HttpAsyncService.State();
        final HttpContext exchangeContext = state.getContext();
        state.setRequestState(MessageState.READY);
        state.setResponseState(MessageState.READY);
        state.setRequestConsumer(this.requestConsumer);
        state.setResponseProducer(this.responseProducer);
        state.setCancellable(this.cancellable);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        Mockito.doThrow(new RuntimeException()).when(this.httpProcessor).process(
                Mockito.any(HttpResponse.class), Mockito.eq(exchangeContext));
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
        final State state = new HttpAsyncService.State();
        final HttpContext exchangeContext = state.getContext();
        state.setRequestState(MessageState.READY);
        state.setResponseState(MessageState.READY);
        state.setRequestConsumer(this.requestConsumer);
        state.setResponseProducer(this.responseProducer);
        state.setCancellable(this.cancellable);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        Mockito.doThrow(new IOException()).when(this.httpProcessor).process(
                Mockito.any(HttpResponse.class), Mockito.eq(exchangeContext));
        final IOException ioex = new IOException();

        this.protocolHandler.exception(this.conn, ioex);

        Mockito.verify(this.conn).shutdown();
        Mockito.verify(this.requestConsumer).failed(ioex);
        Mockito.verify(this.requestConsumer, Mockito.atLeastOnce()).close();
        Mockito.verify(this.responseProducer).failed(ioex);
        Mockito.verify(this.responseProducer, Mockito.atLeastOnce()).close();
        Mockito.verify(this.cancellable).cancel();
    }

    @Test
    public void testHttpExceptionHandlingResponseSubmitted() throws Exception {
        final State state = new HttpAsyncService.State();
        state.setRequestState(MessageState.READY);
        state.setResponseState(MessageState.READY);
        state.setRequestConsumer(this.requestConsumer);
        state.setResponseProducer(this.responseProducer);
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
        final State state = new HttpAsyncService.State();
        final HttpContext exchangeContext = state.getContext();
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final BasicHttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        Mockito.when(this.conn.getHttpRequest()).thenReturn(request);
        Mockito.when(this.requestHandler.processRequest(
                request, exchangeContext)).thenReturn(this.requestConsumer);
        Mockito.when(this.requestConsumer.getException()).thenReturn(null);
        final Object data = new Object();
        Mockito.when(this.requestConsumer.getResult()).thenReturn(data);

        this.protocolHandler.requestReceived(this.conn);

        Assert.assertEquals(MessageState.COMPLETED, state.getRequestState());
        Assert.assertEquals(MessageState.INIT, state.getResponseState());

        Assert.assertSame(request, state.getRequest());
        Assert.assertSame(this.requestHandler, state.getRequestHandler());
        Assert.assertSame(this.requestConsumer, state.getRequestConsumer());
        Assert.assertSame(request, exchangeContext.getAttribute(HttpCoreContext.HTTP_REQUEST));
        Assert.assertSame(this.conn, exchangeContext.getAttribute(HttpCoreContext.HTTP_CONNECTION));

        Mockito.verify(this.httpProcessor).process(request, exchangeContext);
        Mockito.verify(this.requestConsumer).requestReceived(request);
        Mockito.verify(this.requestConsumer).requestCompleted(exchangeContext);
        Mockito.verify(this.requestHandler).handle(
                Mockito.eq(data),
                Mockito.any(HttpAsyncExchange.class),
                Mockito.eq(exchangeContext));
    }

    @Test
    public void testRequestNoMatchingHandler() throws Exception {
        final State state = new HttpAsyncService.State();
        final HttpContext exchangeContext = state.getContext();
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST",
                "/stuff", HttpVersion.HTTP_1_1);
        request.setEntity(new NStringEntity("stuff"));
        Mockito.when(this.conn.getHttpRequest()).thenReturn(request);
        Mockito.when(this.requestHandler.processRequest(
                request, exchangeContext)).thenReturn(this.requestConsumer);

        this.protocolHandler.requestReceived(this.conn);

        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Assert.assertSame(request, state.getRequest());
        Assert.assertTrue(state.getRequestHandler() instanceof NullRequestHandler);
    }

    @Test
    public void testEntityEnclosingRequest() throws Exception {
        final State state = new HttpAsyncService.State();
        final HttpContext exchangeContext = state.getContext();
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        Mockito.when(this.conn.getHttpRequest()).thenReturn(request);
        Mockito.when(this.requestHandler.processRequest(
                request, exchangeContext)).thenReturn(this.requestConsumer);

        this.protocolHandler.requestReceived(this.conn);

        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Assert.assertSame(request, state.getRequest());
        Assert.assertSame(this.requestHandler, state.getRequestHandler());
        Assert.assertSame(this.requestConsumer, state.getRequestConsumer());
        Assert.assertSame(request, exchangeContext.getAttribute(HttpCoreContext.HTTP_REQUEST));
        Assert.assertSame(this.conn, exchangeContext.getAttribute(HttpCoreContext.HTTP_CONNECTION));

        Mockito.verify(this.httpProcessor).process(request, exchangeContext);
        Mockito.verify(this.requestConsumer).requestReceived(request);
        Mockito.verify(this.conn, Mockito.never()).suspendInput();
    }

    @Test
    public void testEntityEnclosingRequestContinueWithoutVerification() throws Exception {
        final State state = new HttpAsyncService.State();
        final HttpContext exchangeContext = state.getContext();
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        request.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);
        Mockito.when(this.conn.getHttpRequest()).thenReturn(request);
        Mockito.when(this.requestHandler.processRequest(
                request, exchangeContext)).thenReturn(this.requestConsumer);

        this.protocolHandler.requestReceived(this.conn);

        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Assert.assertSame(request, state.getRequest());
        Assert.assertSame(this.requestHandler, state.getRequestHandler());
        Assert.assertSame(this.requestConsumer, state.getRequestConsumer());
        Assert.assertSame(request, exchangeContext.getAttribute(HttpCoreContext.HTTP_REQUEST));
        Assert.assertSame(this.conn, exchangeContext.getAttribute(HttpCoreContext.HTTP_CONNECTION));

        Mockito.verify(this.httpProcessor).process(request, exchangeContext);
        Mockito.verify(this.requestConsumer).requestReceived(request);
        Mockito.verify(this.conn, Mockito.never()).suspendInput();
        Mockito.verify(this.conn).submitResponse(Mockito.argThat(new ArgumentMatcher<HttpResponse>() {

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

        final State state = new HttpAsyncService.State();
        final HttpContext exchangeContext = state.getContext();
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        request.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);
        Mockito.when(this.conn.getHttpRequest()).thenReturn(request);
        Mockito.when(this.requestHandler.processRequest(
                request, exchangeContext)).thenReturn(this.requestConsumer);

        this.protocolHandler.requestReceived(this.conn);

        Assert.assertEquals(MessageState.ACK_EXPECTED, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Assert.assertSame(request, state.getRequest());
        Assert.assertSame(this.requestHandler, state.getRequestHandler());
        Assert.assertSame(this.requestConsumer, state.getRequestConsumer());
        Assert.assertSame(request, exchangeContext.getAttribute(HttpCoreContext.HTTP_REQUEST));
        Assert.assertSame(this.conn, exchangeContext.getAttribute(HttpCoreContext.HTTP_CONNECTION));

        Mockito.verify(this.httpProcessor).process(request, exchangeContext);
        Mockito.verify(this.requestConsumer).requestReceived(request);
        Mockito.verify(this.conn).suspendInput();
        Mockito.verify(expectationVerifier).verify(
                Mockito.any(HttpAsyncExchange.class),
                Mockito.eq(exchangeContext));
    }

    @Test
    public void testRequestExpectationFailed() throws Exception {
        final State state = new HttpAsyncService.State();
        state.setRequestState(MessageState.ACK_EXPECTED);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpAsyncExchange httpexchanage = new HttpAsyncService.Exchange(
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1),
                new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK"),
                state, this.conn);
        Assert.assertFalse(httpexchanage.isCompleted());
        httpexchanage.submitResponse(this.responseProducer);
        Assert.assertTrue(httpexchanage.isCompleted());

        Assert.assertEquals(MessageState.ACK_EXPECTED, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
        Assert.assertSame(this.responseProducer, state.getResponseProducer());

        Mockito.verify(this.conn).requestOutput();

        try {
            httpexchanage.submitResponse();
            Assert.fail("IllegalStateException expected");
        } catch (final IllegalStateException ex) {
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testRequestExpectationFailedInvalidResponseProducer() throws Exception {
        final State state = new HttpAsyncService.State();
        state.setRequestState(MessageState.ACK_EXPECTED);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpAsyncExchange httpexchanage = new HttpAsyncService.Exchange(
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1),
                new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK"),
                state, this.conn);
        httpexchanage.submitResponse(null);
    }

    @Test
    public void testRequestContinue() throws Exception {
        final State state = new HttpAsyncService.State();
        state.setRequestState(MessageState.ACK_EXPECTED);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpAsyncExchange httpexchanage = new HttpAsyncService.Exchange(
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1),
                new BasicHttpResponse(HttpVersion.HTTP_1_1, 100, "Continue"),
                state, this.conn);
        Assert.assertFalse(httpexchanage.isCompleted());
        httpexchanage.submitResponse();
        Assert.assertTrue(httpexchanage.isCompleted());

        final HttpAsyncResponseProducer responseProducer = state.getResponseProducer();
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
        final State state = new HttpAsyncService.State();
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        state.setRequestState(MessageState.BODY_STREAM);
        state.setRequest(request);
        state.setRequestConsumer(this.requestConsumer);
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
        final State state = new HttpAsyncService.State();
        final HttpContext exchangeContext = state.getContext();
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        state.setRequestState(MessageState.BODY_STREAM);
        state.setRequest(request);
        state.setRequestConsumer(this.requestConsumer);
        state.setRequestHandler(this.requestHandler);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);
        Mockito.when(this.decoder.isCompleted()).thenReturn(Boolean.TRUE);
        Mockito.when(this.requestConsumer.getException()).thenReturn(null);
        final Object data = new Object();
        Mockito.when(this.requestConsumer.getResult()).thenReturn(data);

        this.protocolHandler.inputReady(conn, this.decoder);

        Assert.assertEquals(MessageState.COMPLETED, state.getRequestState());
        Assert.assertEquals(MessageState.INIT, state.getResponseState());

        Mockito.verify(this.requestConsumer).consumeContent(this.decoder, this.conn);
        Mockito.verify(this.requestConsumer).requestCompleted(exchangeContext);
        Mockito.verify(this.requestHandler).handle(
                Mockito.eq(data),
                Mockito.any(HttpAsyncExchange.class),
                Mockito.eq(exchangeContext));
    }

    @Test
    public void testRequestCompletedWithException() throws Exception {
        final State state = new HttpAsyncService.State();
        final HttpContext exchangeContext = state.getContext();
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        state.setRequestState(MessageState.BODY_STREAM);
        state.setRequest(request);
        state.setRequestConsumer(this.requestConsumer);
        state.setRequestHandler(this.requestHandler);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);
        Mockito.when(this.decoder.isCompleted()).thenReturn(Boolean.TRUE);
        Mockito.when(this.requestConsumer.getException()).thenReturn(new HttpException());
        Mockito.when(this.requestConsumer.getResult()).thenReturn(null);

        this.protocolHandler.inputReady(conn, this.decoder);

        Assert.assertEquals(MessageState.COMPLETED, state.getRequestState());
        Assert.assertEquals(MessageState.INIT, state.getResponseState());
        Assert.assertNotNull(state.getResponseProducer());

        Mockito.verify(this.requestConsumer).consumeContent(this.decoder, this.conn);
        Mockito.verify(this.requestConsumer).requestCompleted(exchangeContext);
        Mockito.verify(this.conn).requestOutput();
        Mockito.verify(this.requestHandler, Mockito.never()).handle(
                Mockito.any(),
                Mockito.any(HttpAsyncExchange.class),
                Mockito.any(HttpContext.class));
    }

    @Test
    public void testBasicResponse() throws Exception {
        final State state = new HttpAsyncService.State();
        final HttpContext exchangeContext = state.getContext();
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        state.setRequest(request);
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);
        Mockito.when(this.reuseStrategy.keepAlive(response, exchangeContext)).thenReturn(Boolean.TRUE);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Mockito.verify(this.httpProcessor).process(response, exchangeContext);
        Mockito.verify(this.conn).submitResponse(response);
        Mockito.verify(this.responseProducer).responseCompleted(exchangeContext);
        Mockito.verify(this.conn).requestInput();
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testBasicResponseNoKeepAlive() throws Exception {
        final State state = new HttpAsyncService.State();
        final HttpContext exchangeContext = state.getContext();
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        state.setRequest(request);
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);
        Mockito.when(this.reuseStrategy.keepAlive(response, exchangeContext)).thenReturn(Boolean.FALSE);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Mockito.verify(this.httpProcessor).process(response, exchangeContext);
        Mockito.verify(this.conn).submitResponse(response);
        Mockito.verify(this.responseProducer).responseCompleted(exchangeContext);
        Mockito.verify(this.conn).close();
    }

    @Test
    public void testEntityEnclosingResponse() throws Exception {
        final State state = new HttpAsyncService.State();
        final HttpContext exchangeContext = state.getContext();
        final BasicHttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        state.setRequest(request);
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.setEntity(new NStringEntity("stuff"));
        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.COMPLETED, state.getRequestState());
        Assert.assertEquals(MessageState.BODY_STREAM, state.getResponseState());
        Assert.assertEquals("request state: COMPLETED; request: GET / HTTP/1.1; " +
                "response state: BODY_STREAM; response: HTTP/1.1 200 OK;", state.toString());

        Mockito.verify(this.httpProcessor).process(response, exchangeContext);
        Mockito.verify(this.conn).submitResponse(response);
        Mockito.verify(this.responseProducer, Mockito.never()).responseCompleted(exchangeContext);
    }

    @Test
    public void testResponseToHead() throws Exception {
        final State state = new HttpAsyncService.State();
        final HttpContext exchangeContext = state.getContext();
        final BasicHttpRequest request = new BasicHttpRequest("HEAD", "/", HttpVersion.HTTP_1_1);
        state.setRequest(request);
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.setEntity(new NStringEntity("stuff"));
        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);
        Mockito.when(this.reuseStrategy.keepAlive(response, exchangeContext)).thenReturn(Boolean.TRUE);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Mockito.verify(this.httpProcessor).process(response, exchangeContext);
        Mockito.verify(this.conn).submitResponse(response);
        Mockito.verify(this.responseProducer).responseCompleted(exchangeContext);
        Mockito.verify(this.conn).requestInput();
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testResponseNotModified() throws Exception {
        final State state = new HttpAsyncService.State();
        final HttpContext exchangeContext = state.getContext();
        final BasicHttpRequest request = new BasicHttpRequest("HEAD", "/", HttpVersion.HTTP_1_1);
        state.setRequest(request);
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_NOT_MODIFIED, "Not modified");
        response.setEntity(new NStringEntity("stuff"));
        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);
        Mockito.when(this.reuseStrategy.keepAlive(response, exchangeContext)).thenReturn(Boolean.TRUE);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Mockito.verify(this.httpProcessor).process(response, exchangeContext);
        Mockito.verify(this.conn).submitResponse(response);
        Mockito.verify(this.responseProducer).responseCompleted(exchangeContext);
        Mockito.verify(this.conn).requestInput();
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testResponseContinue() throws Exception {
        final State state = new HttpAsyncService.State();
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        state.setRequest(request);
        state.setRequestState(MessageState.ACK_EXPECTED);
        state.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_CONTINUE, "Continue");
        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Mockito.verify(this.conn).requestInput();
        Mockito.verify(this.conn).submitResponse(Mockito.argThat(new ArgumentMatcher<HttpResponse>() {

            @Override
            public boolean matches(final Object argument) {
                final int status = ((HttpResponse) argument).getStatusLine().getStatusCode();
                return status == 100;
            }

        }));
    }

    @Test
    public void testResponseFailedExpectation() throws Exception {
        final State state = new HttpAsyncService.State();
        final HttpContext exchangeContext = state.getContext();
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        state.setRequest(request);
        state.setRequestState(MessageState.ACK_EXPECTED);
        state.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 417, "Expectation failed");
        response.setEntity(new NStringEntity("stuff"));
        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.COMPLETED, state.getRequestState());
        Assert.assertEquals(MessageState.BODY_STREAM, state.getResponseState());

        Mockito.verify(this.conn).resetInput();
        Mockito.verify(this.httpProcessor).process(response, exchangeContext);
        Mockito.verify(this.conn).submitResponse(response);
        Mockito.verify(this.responseProducer, Mockito.never()).responseCompleted(exchangeContext);
    }

    @Test(expected=HttpException.class)
    public void testInvalidResponseStatus() throws Exception {
        final State state = new HttpAsyncService.State();
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        state.setRequest(request);
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseState(MessageState.READY);
        state.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 112, "Something stupid");
        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);
        Mockito.when(this.conn.isResponseSubmitted()).thenReturn(Boolean.FALSE);

        this.protocolHandler.responseReady(this.conn);
    }

    @Test(expected=HttpException.class)
    public void testInvalidResponseStatusToExpection() throws Exception {
        final State state = new HttpAsyncService.State();
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        state.setRequest(request);
        state.setRequestState(MessageState.ACK_EXPECTED);
        state.setResponseState(MessageState.READY);
        state.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.setEntity(new NStringEntity("stuff"));
        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);
        Mockito.when(this.conn.isResponseSubmitted()).thenReturn(Boolean.FALSE);

        this.protocolHandler.responseReady(this.conn);
    }

    @Test
    public void testResponseTrigger() throws Exception {
        final State state = new HttpAsyncService.State();
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseState(MessageState.READY);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpAsyncExchange httpexchanage = new HttpAsyncService.Exchange(
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1),
                new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK"),
                state, this.conn);
        Assert.assertFalse(httpexchanage.isCompleted());
        httpexchanage.submitResponse(this.responseProducer);
        Assert.assertTrue(httpexchanage.isCompleted());

        Assert.assertEquals(MessageState.COMPLETED, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
        Assert.assertSame(this.responseProducer, state.getResponseProducer());

        Mockito.verify(this.conn).requestOutput();

        try {
            httpexchanage.submitResponse(Mockito.mock(HttpAsyncResponseProducer.class));
            Assert.fail("IllegalStateException expected");
        } catch (final IllegalStateException ex) {
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testResponseTriggerInvalidResponseProducer() throws Exception {
        final State state = new HttpAsyncService.State();
        state.setRequestState(MessageState.ACK_EXPECTED);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);

        final HttpAsyncExchange httpexchanage = new HttpAsyncService.Exchange(
                new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1),
                new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK"),
                state, this.conn);
        httpexchanage.submitResponse(null);
    }

    @Test
    public void testResponseContent() throws Exception {
        final State state = new HttpAsyncService.State();
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.setEntity(new NStringEntity("stuff"));
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseState(MessageState.BODY_STREAM);
        state.setResponse(response);
        state.setResponseProducer(this.responseProducer);
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
        final State state = new HttpAsyncService.State();
        final HttpContext exchangeContext = state.getContext();
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.setEntity(new NStringEntity("stuff"));
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseState(MessageState.BODY_STREAM);
        state.setResponse(response);
        state.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);
        Mockito.when(this.encoder.isCompleted()).thenReturn(true);
        Mockito.when(this.reuseStrategy.keepAlive(response, exchangeContext)).thenReturn(Boolean.TRUE);

        this.protocolHandler.outputReady(conn, this.encoder);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Mockito.verify(this.responseProducer).produceContent(this.encoder, this.conn);
        Mockito.verify(this.responseProducer).responseCompleted(exchangeContext);
        Mockito.verify(this.conn).requestInput();
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testResponseContentCompletedNoKeepAlive() throws Exception {
        final State state = new HttpAsyncService.State();
        final HttpContext exchangeContext = state.getContext();
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.setEntity(new NStringEntity("stuff"));
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseState(MessageState.BODY_STREAM);
        state.setResponse(response);
        state.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);
        Mockito.when(this.encoder.isCompleted()).thenReturn(true);
        Mockito.when(this.reuseStrategy.keepAlive(response, exchangeContext)).thenReturn(Boolean.FALSE);

        this.protocolHandler.outputReady(conn, this.encoder);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());

        Mockito.verify(this.responseProducer).produceContent(this.encoder, this.conn);
        Mockito.verify(this.responseProducer).responseCompleted(exchangeContext);
        Mockito.verify(this.conn, Mockito.never()).requestInput();
        Mockito.verify(this.conn).close();
    }

    @Test
    public void testTimeoutActiveConnection() throws Exception {
        final State state = new HttpAsyncService.State();
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);
        Mockito.when(this.conn.getStatus()).thenReturn(NHttpClientConnection.ACTIVE, NHttpClientConnection.CLOSED);

        this.protocolHandler.timeout(this.conn);

        Mockito.verify(this.conn).close();
        Mockito.verify(this.conn, Mockito.never()).setSocketTimeout(Mockito.anyInt());
    }

    @Test
    public void testTimeoutActiveConnectionBufferedData() throws Exception {
        final State state = new HttpAsyncService.State();
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);
        Mockito.when(this.conn.getStatus()).thenReturn(NHttpClientConnection.ACTIVE, NHttpClientConnection.CLOSING);

        this.protocolHandler.timeout(this.conn);

        Mockito.verify(this.conn).close();
        Mockito.verify(this.conn).setSocketTimeout(250);
    }

    @Test
    public void testTimeoutClosingConnection() throws Exception {
        final State state = new HttpAsyncService.State();
        state.setRequestConsumer(this.requestConsumer);
        state.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncService.HTTP_EXCHANGE_STATE, state);
        Mockito.when(this.conn.getStatus()).thenReturn(NHttpClientConnection.CLOSING);

        this.protocolHandler.timeout(this.conn);

        Mockito.verify(this.conn).shutdown();
        Mockito.verify(this.requestConsumer).failed(Mockito.any(SocketTimeoutException.class));
        Mockito.verify(this.requestConsumer).close();
        Mockito.verify(this.responseProducer).failed(Mockito.any(SocketTimeoutException.class));
        Mockito.verify(this.responseProducer).close();
    }

}

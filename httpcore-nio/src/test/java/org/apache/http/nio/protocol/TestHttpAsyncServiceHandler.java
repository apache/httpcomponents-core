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

import junit.framework.Assert;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.UnsupportedHttpVersionException;
import org.apache.http.concurrent.Cancellable;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.nio.protocol.HttpAsyncServiceHandler.HttpExchange;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

public class TestHttpAsyncServiceHandler {

    private HttpAsyncRequestHandlerRegistry handlerResolver;
    private HttpProcessor httpProcessor;
    private HttpAsyncServiceHandler protocolHandler;
    private ConnectionReuseStrategy reuseStrategy;
    private HttpParams params;
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
        this.requestConsumer = Mockito.mock(HttpAsyncRequestConsumer.class);;
        this.responseProducer = Mockito.mock(HttpAsyncResponseProducer.class);
        this.handlerResolver = new HttpAsyncRequestHandlerRegistry();
        this.handlerResolver.register("/", this.requestHandler);
        this.httpProcessor = Mockito.mock(HttpProcessor.class);
        this.reuseStrategy = Mockito.mock(ConnectionReuseStrategy.class);
        this.params = new BasicHttpParams();
        this.protocolHandler = new HttpAsyncServiceHandler(
                this.handlerResolver, this.httpProcessor, this.reuseStrategy, this.params);
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

    @Test
    public void testInvalidConstruction() throws Exception {
        try {
            new HttpAsyncServiceHandler(null, this.httpProcessor, this.reuseStrategy, this.params);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }
        try {
            new HttpAsyncServiceHandler(this.handlerResolver, null, this.reuseStrategy, this.params);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }
        try {
            new HttpAsyncServiceHandler(this.handlerResolver, this.httpProcessor, null, this.params);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }
        try {
            new HttpAsyncServiceHandler(this.handlerResolver, this.httpProcessor, this.reuseStrategy, null);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }
    }

    @Test
    public void testConnected() throws Exception {
        this.protocolHandler.connected(this.conn);

        HttpExchange httpExchange = (HttpExchange) this.connContext.getAttribute(
                HttpAsyncServiceHandler.HTTP_EXCHANGE);
        Assert.assertNotNull(httpExchange);
        Assert.assertEquals(MessageState.READY, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.READY, httpExchange.getResponseState());
        Assert.assertEquals("request state: READY; request: ; " +
                "response state: READY; response: ;", httpExchange.toString());
    }

    @Test
    public void testClosed() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        httpExchange.setRequestState(MessageState.COMPLETED);
        httpExchange.setResponseState(MessageState.COMPLETED);
        httpExchange.setRequestConsumer(this.requestConsumer);
        httpExchange.setResponseProducer(this.responseProducer);
        httpExchange.setAsyncProcess(this.cancellable);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        this.protocolHandler.closed(this.conn);

        Assert.assertEquals(MessageState.READY, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.READY, httpExchange.getResponseState());
        Mockito.verify(this.requestConsumer).close();
        Mockito.verify(this.responseProducer).close();
        Mockito.verify(this.cancellable).cancel();
    }

    @Test
    public void testHttpExceptionHandling() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        httpExchange.setRequestState(MessageState.READY);
        httpExchange.setResponseState(MessageState.READY);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        HttpException httpex = new HttpException();
        this.protocolHandler.exception(this.conn, httpex);

        Assert.assertEquals(MessageState.READY, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.BODY_STREAM, httpExchange.getResponseState());
        Assert.assertNotNull(httpExchange.getResponseProducer());
        Assert.assertNotNull(httpExchange.getResponse());
        Assert.assertEquals(500, httpExchange.getResponse().getStatusLine().getStatusCode());
    }

    @Test
    public void testHttpExceptionHandlingRuntimeException() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        HttpContext exchangeContext = httpExchange.getContext();
        httpExchange.setRequestState(MessageState.READY);
        httpExchange.setResponseState(MessageState.READY);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        Mockito.doThrow(new RuntimeException()).when(this.httpProcessor).process(
                Mockito.any(HttpResponse.class), Mockito.eq(exchangeContext));
        try {
            HttpException httpex = new HttpException();
            this.protocolHandler.exception(this.conn, httpex);
            Assert.fail("RuntimeException expected");
        } catch (RuntimeException ex) {
            Mockito.verify(this.conn).shutdown();
        }
    }

    @Test
    public void testHttpExceptionHandlingIOException() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        HttpContext exchangeContext = httpExchange.getContext();
        httpExchange.setRequestState(MessageState.READY);
        httpExchange.setResponseState(MessageState.READY);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        Mockito.doThrow(new IOException()).when(this.httpProcessor).process(
                Mockito.any(HttpResponse.class), Mockito.eq(exchangeContext));
        HttpException httpex = new HttpException();

        this.protocolHandler.exception(this.conn, httpex);

        Mockito.verify(this.conn).shutdown();
    }

    @Test
    public void testHttpExceptionHandlingResponseSubmitted() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        httpExchange.setRequestState(MessageState.READY);
        httpExchange.setResponseState(MessageState.READY);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);
        Mockito.when(this.conn.isResponseSubmitted()).thenReturn(true);

        HttpException httpex = new HttpException();
        this.protocolHandler.exception(this.conn, httpex);

        Assert.assertEquals(MessageState.READY, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.READY, httpExchange.getResponseState());
        Mockito.verify(this.conn).close();
    }

    @Test
    public void testIOExceptionHandling() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        httpExchange.setRequestState(MessageState.READY);
        httpExchange.setResponseState(MessageState.READY);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        IOException httpex = new IOException();
        this.protocolHandler.exception(this.conn, httpex);

        Assert.assertEquals(MessageState.READY, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.READY, httpExchange.getResponseState());
        Mockito.verify(this.conn).shutdown();
    }

    @Test
    public void testBasicRequest() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        HttpContext exchangeContext = httpExchange.getContext();
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        BasicHttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        Mockito.when(this.conn.getHttpRequest()).thenReturn(request);
        Mockito.when(this.requestHandler.processRequest(
                request, exchangeContext)).thenReturn(this.requestConsumer);
        Mockito.when(this.requestConsumer.getException()).thenReturn(null);
        Object data = new Object();
        Mockito.when(this.requestConsumer.getResult()).thenReturn(data);
        Mockito.when(this.requestHandler.handle(
                Mockito.eq(data),
                Mockito.any(HttpAsyncResponseTrigger.class),
                Mockito.eq(exchangeContext))).thenReturn(this.cancellable);

        this.protocolHandler.requestReceived(this.conn);

        Assert.assertEquals(MessageState.COMPLETED, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.READY, httpExchange.getResponseState());

        Assert.assertSame(request, httpExchange.getRequest());
        Assert.assertSame(this.requestHandler, httpExchange.getRequestHandler());
        Assert.assertSame(this.requestConsumer, httpExchange.getRequestConsumer());
        Assert.assertSame(request, exchangeContext.getAttribute(ExecutionContext.HTTP_REQUEST));
        Assert.assertSame(this.conn, exchangeContext.getAttribute(ExecutionContext.HTTP_CONNECTION));

        Mockito.verify(this.httpProcessor).process(request, exchangeContext);
        Mockito.verify(this.requestConsumer).requestReceived(request);
        Mockito.verify(this.conn).suspendInput();
        Mockito.verify(this.requestConsumer).requestCompleted(exchangeContext);
        Mockito.verify(this.requestHandler).handle(
                Mockito.eq(data),
                Mockito.any(HttpAsyncResponseTrigger.class),
                Mockito.eq(exchangeContext));
        Assert.assertSame(this.cancellable, httpExchange.getAsyncProcess());
    }

    @Test
    public void testRequestNoMatchingHandler() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        HttpContext exchangeContext = httpExchange.getContext();
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST",
                "/stuff", HttpVersion.HTTP_1_1);
        request.setEntity(NStringEntity.create("stuff"));
        Mockito.when(this.conn.getHttpRequest()).thenReturn(request);
        Mockito.when(this.requestHandler.processRequest(
                request, exchangeContext)).thenReturn(this.requestConsumer);

        this.protocolHandler.requestReceived(this.conn);

        Assert.assertEquals(MessageState.BODY_STREAM, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.READY, httpExchange.getResponseState());

        Assert.assertSame(request, httpExchange.getRequest());
        Assert.assertTrue(httpExchange.getRequestHandler() instanceof NullRequestHandler);
    }

    @Test
    public void testEntityEnclosingRequest() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        HttpContext exchangeContext = httpExchange.getContext();
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        Mockito.when(this.conn.getHttpRequest()).thenReturn(request);
        Mockito.when(this.requestHandler.processRequest(
                request, exchangeContext)).thenReturn(this.requestConsumer);

        this.protocolHandler.requestReceived(this.conn);

        Assert.assertEquals(MessageState.BODY_STREAM, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.READY, httpExchange.getResponseState());

        Assert.assertSame(request, httpExchange.getRequest());
        Assert.assertSame(this.requestHandler, httpExchange.getRequestHandler());
        Assert.assertSame(this.requestConsumer, httpExchange.getRequestConsumer());
        Assert.assertSame(request, exchangeContext.getAttribute(ExecutionContext.HTTP_REQUEST));
        Assert.assertSame(this.conn, exchangeContext.getAttribute(ExecutionContext.HTTP_CONNECTION));

        Mockito.verify(this.httpProcessor).process(request, exchangeContext);
        Mockito.verify(this.requestConsumer).requestReceived(request);
        Mockito.verify(this.conn, Mockito.never()).suspendInput();
    }

    @Test
    public void testEntityEnclosingRequestContinueWithoutVerification() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        HttpContext exchangeContext = httpExchange.getContext();
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        request.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);
        Mockito.when(this.conn.getHttpRequest()).thenReturn(request);
        Mockito.when(this.requestHandler.processRequest(
                request, exchangeContext)).thenReturn(this.requestConsumer);

        this.protocolHandler.requestReceived(this.conn);

        Assert.assertEquals(MessageState.BODY_STREAM, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.READY, httpExchange.getResponseState());

        Assert.assertSame(request, httpExchange.getRequest());
        Assert.assertSame(this.requestHandler, httpExchange.getRequestHandler());
        Assert.assertSame(this.requestConsumer, httpExchange.getRequestConsumer());
        Assert.assertSame(request, exchangeContext.getAttribute(ExecutionContext.HTTP_REQUEST));
        Assert.assertSame(this.conn, exchangeContext.getAttribute(ExecutionContext.HTTP_CONNECTION));

        Mockito.verify(this.httpProcessor).process(request, exchangeContext);
        Mockito.verify(this.requestConsumer).requestReceived(request);
        Mockito.verify(this.conn, Mockito.never()).suspendInput();
        Mockito.verify(this.conn).submitResponse(Mockito.argThat(new ArgumentMatcher<HttpResponse>() {

            @Override
            public boolean matches(final Object argument) {
                int status = ((HttpResponse) argument).getStatusLine().getStatusCode();
                return status == 100;
            }

        }));
    }

    @Test
    public void testEntityEnclosingRequestExpectationVerification() throws Exception {
        HttpAsyncExpectationVerifier expectationVerifier = Mockito.mock(HttpAsyncExpectationVerifier.class);
        this.protocolHandler = new HttpAsyncServiceHandler(
                this.handlerResolver, expectationVerifier, this.httpProcessor, this.reuseStrategy, this.params);

        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        HttpContext exchangeContext = httpExchange.getContext();
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        request.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);
        Mockito.when(this.conn.getHttpRequest()).thenReturn(request);
        Mockito.when(this.requestHandler.processRequest(
                request, exchangeContext)).thenReturn(this.requestConsumer);
        Mockito.when(expectationVerifier.verify(
                Mockito.eq(request),
                Mockito.any(HttpAsyncContinueTrigger.class),
                Mockito.eq(exchangeContext))).thenReturn(this.cancellable);

        this.protocolHandler.requestReceived(this.conn);

        Assert.assertEquals(MessageState.ACK_EXPECTED, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.READY, httpExchange.getResponseState());

        Assert.assertSame(request, httpExchange.getRequest());
        Assert.assertSame(this.requestHandler, httpExchange.getRequestHandler());
        Assert.assertSame(this.requestConsumer, httpExchange.getRequestConsumer());
        Assert.assertSame(request, exchangeContext.getAttribute(ExecutionContext.HTTP_REQUEST));
        Assert.assertSame(this.conn, exchangeContext.getAttribute(ExecutionContext.HTTP_CONNECTION));

        Mockito.verify(this.httpProcessor).process(request, exchangeContext);
        Mockito.verify(this.requestConsumer).requestReceived(request);
        Mockito.verify(this.conn).suspendInput();
        Mockito.verify(expectationVerifier).verify(
                Mockito.eq(request),
                Mockito.any(HttpAsyncContinueTrigger.class),
                Mockito.eq(exchangeContext));
        Assert.assertSame(this.cancellable, httpExchange.getAsyncProcess());
    }

    @Test
    public void testRequestRuntimeException() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        HttpContext exchangeContext = httpExchange.getContext();
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        BasicHttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        Mockito.when(this.conn.getHttpRequest()).thenReturn(request);
        Mockito.when(this.requestHandler.processRequest(
                request, exchangeContext)).thenThrow(new RuntimeException());
        try {
            this.protocolHandler.requestReceived(this.conn);
            Assert.fail("RuntimeException expected");
        } catch (RuntimeException ex) {
            Mockito.verify(this.conn).shutdown();
        }
    }

    @Test
    public void testRequestHttpException() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        HttpContext exchangeContext = httpExchange.getContext();
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        BasicHttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        Mockito.when(this.conn.getHttpRequest()).thenReturn(request);
        Mockito.when(this.requestHandler.processRequest(
                request, exchangeContext)).thenThrow(new HttpException());

        this.protocolHandler.requestReceived(this.conn);
        Mockito.verify(this.conn).shutdown();
    }

    @Test
    public void testRequestExpectationFailed() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        httpExchange.setRequestState(MessageState.ACK_EXPECTED);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        HttpAsyncContinueTrigger trigger = this.protocolHandler.new ContinueTriggerImpl(httpExchange, this.conn);
        Assert.assertFalse(trigger.isTriggered());
        trigger.submitResponse(this.responseProducer);
        Assert.assertTrue(trigger.isTriggered());

        Assert.assertEquals(MessageState.ACK_EXPECTED, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.READY, httpExchange.getResponseState());
        Assert.assertSame(this.responseProducer, httpExchange.getResponseProducer());

        Mockito.verify(this.conn).requestOutput();

        try {
            trigger.continueRequest();
            Assert.fail("IllegalStateException expected");
        } catch (IllegalStateException ex) {
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testRequestExpectationFailedInvalidResponseProducer() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        httpExchange.setRequestState(MessageState.ACK_EXPECTED);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        HttpAsyncContinueTrigger trigger = this.protocolHandler.new ContinueTriggerImpl(httpExchange, this.conn);
        trigger.submitResponse(null);
    }

    @Test
    public void testRequestContinue() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        httpExchange.setRequestState(MessageState.ACK_EXPECTED);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        HttpAsyncContinueTrigger trigger = this.protocolHandler.new ContinueTriggerImpl(httpExchange, this.conn);
        Assert.assertFalse(trigger.isTriggered());
        trigger.continueRequest();
        Assert.assertTrue(trigger.isTriggered());

        Assert.assertEquals(MessageState.ACK, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.READY, httpExchange.getResponseState());
        Assert.assertNull(httpExchange.getResponseProducer());

        Mockito.verify(this.conn).requestOutput();

        try {
            trigger.submitResponse(this.responseProducer);
            Assert.fail("IllegalStateException expected");
        } catch (IllegalStateException ex) {
        }
    }

    @Test
    public void testRequestContent() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        httpExchange.setRequestState(MessageState.BODY_STREAM);
        httpExchange.setRequest(request);
        httpExchange.setRequestConsumer(this.requestConsumer);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);
        Mockito.when(this.decoder.isCompleted()).thenReturn(false);

        this.protocolHandler.inputReady(conn, this.decoder);

        Assert.assertEquals(MessageState.BODY_STREAM, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.READY, httpExchange.getResponseState());

        Mockito.verify(this.requestConsumer).consumeContent(this.decoder, this.conn);
        Mockito.verify(this.conn, Mockito.never()).suspendInput();
    }

    @Test
    public void testRequestContentCompleted() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        HttpContext exchangeContext = httpExchange.getContext();
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        httpExchange.setRequestState(MessageState.BODY_STREAM);
        httpExchange.setRequest(request);
        httpExchange.setRequestConsumer(this.requestConsumer);
        httpExchange.setRequestHandler(this.requestHandler);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);
        Mockito.when(this.decoder.isCompleted()).thenReturn(true);
        Mockito.when(this.requestConsumer.getException()).thenReturn(null);
        Object data = new Object();
        Mockito.when(this.requestConsumer.getResult()).thenReturn(data);
        Mockito.when(this.requestHandler.handle(
                Mockito.eq(data),
                Mockito.any(HttpAsyncResponseTrigger.class),
                Mockito.eq(exchangeContext))).thenReturn(this.cancellable);

        this.protocolHandler.inputReady(conn, this.decoder);

        Assert.assertEquals(MessageState.COMPLETED, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.READY, httpExchange.getResponseState());

        Mockito.verify(this.requestConsumer).consumeContent(this.decoder, this.conn);
        Mockito.verify(this.conn).suspendInput();
        Mockito.verify(this.requestConsumer).requestCompleted(exchangeContext);
        Mockito.verify(this.requestHandler).handle(
                Mockito.eq(data),
                Mockito.any(HttpAsyncResponseTrigger.class),
                Mockito.eq(exchangeContext));
        Assert.assertSame(this.cancellable, httpExchange.getAsyncProcess());
    }

    @Test
    public void testRequestCompletedWithException() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        HttpContext exchangeContext = httpExchange.getContext();
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        httpExchange.setRequestState(MessageState.BODY_STREAM);
        httpExchange.setRequest(request);
        httpExchange.setRequestConsumer(this.requestConsumer);
        httpExchange.setRequestHandler(this.requestHandler);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);
        Mockito.when(this.decoder.isCompleted()).thenReturn(true);
        Mockito.when(this.requestConsumer.getException()).thenReturn(new HttpException());
        Mockito.when(this.requestConsumer.getResult()).thenReturn(null);

        this.protocolHandler.inputReady(conn, this.decoder);

        Assert.assertEquals(MessageState.COMPLETED, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.READY, httpExchange.getResponseState());
        Assert.assertNotNull(httpExchange.getResponseProducer());

        Mockito.verify(this.requestConsumer).consumeContent(this.decoder, this.conn);
        Mockito.verify(this.conn).suspendInput();
        Mockito.verify(this.requestConsumer).requestCompleted(exchangeContext);
        Mockito.verify(this.conn).requestOutput();
        Mockito.verify(this.requestHandler, Mockito.never()).handle(
                Mockito.any(),
                Mockito.any(HttpAsyncResponseTrigger.class),
                Mockito.any(HttpContext.class));
    }

    @Test
    public void testRequestHandlingHttpException() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        HttpContext exchangeContext = httpExchange.getContext();
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        httpExchange.setRequestState(MessageState.BODY_STREAM);
        httpExchange.setRequest(request);
        httpExchange.setRequestConsumer(this.requestConsumer);
        httpExchange.setRequestHandler(this.requestHandler);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);
        Mockito.when(this.decoder.isCompleted()).thenReturn(true);
        Mockito.when(this.requestConsumer.getException()).thenReturn(null);
        Object data = new Object();
        Mockito.when(this.requestConsumer.getResult()).thenReturn(data);
        Mockito.doThrow(new UnsupportedHttpVersionException()).when(
                this.requestHandler).handle(
                        Mockito.eq(data),
                        Mockito.any(HttpAsyncResponseTrigger.class),
                        Mockito.eq(exchangeContext));

        this.protocolHandler.inputReady(conn, this.decoder);

        Assert.assertEquals(MessageState.COMPLETED, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.READY, httpExchange.getResponseState());
        Assert.assertNotNull(httpExchange.getResponseProducer());

        Mockito.verify(this.requestConsumer).consumeContent(this.decoder, this.conn);
        Mockito.verify(this.conn).suspendInput();
        Mockito.verify(this.requestConsumer).requestCompleted(exchangeContext);
        Mockito.verify(this.conn).requestOutput();
    }

    @Test
    public void testRequestContentRuntimeException() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        HttpContext exchangeContext = httpExchange.getContext();
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        httpExchange.setRequestState(MessageState.BODY_STREAM);
        httpExchange.setRequest(request);
        httpExchange.setRequestConsumer(this.requestConsumer);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);
        Mockito.when(this.decoder.isCompleted()).thenReturn(true);
        Mockito.doThrow(new RuntimeException()).when(
                this.requestConsumer).requestCompleted(exchangeContext);
        try {
            this.protocolHandler.inputReady(this.conn, this.decoder);
            Assert.fail("RuntimeException expected");
        } catch (RuntimeException ex) {
            Mockito.verify(this.conn).shutdown();
        }
    }

    @Test
    public void testRequestContentIOException() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        HttpContext exchangeContext = httpExchange.getContext();
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        httpExchange.setRequestState(MessageState.BODY_STREAM);
        httpExchange.setRequest(request);
        httpExchange.setRequestConsumer(this.requestConsumer);
        httpExchange.setRequestHandler(this.requestHandler);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);
        Mockito.when(this.decoder.isCompleted()).thenReturn(true);
        Mockito.when(this.requestConsumer.getException()).thenReturn(null);
        Object data = new Object();
        Mockito.when(this.requestConsumer.getResult()).thenReturn(data);
        Mockito.doThrow(new IOException()).when(
                this.requestHandler).handle(
                        Mockito.eq(data),
                        Mockito.any(HttpAsyncResponseTrigger.class),
                        Mockito.eq(exchangeContext));

        this.protocolHandler.inputReady(this.conn, this.decoder);

        Mockito.verify(this.conn).shutdown();
    }

    @Test
    public void testBasicResponse() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        HttpContext exchangeContext = httpExchange.getContext();
        BasicHttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        httpExchange.setRequest(request);
        httpExchange.setRequestState(MessageState.COMPLETED);
        httpExchange.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);
        Mockito.when(this.reuseStrategy.keepAlive(response, exchangeContext)).thenReturn(true);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.READY, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.READY, httpExchange.getResponseState());

        Mockito.verify(this.httpProcessor).process(response, exchangeContext);
        Mockito.verify(this.conn).submitResponse(response);
        Mockito.verify(this.responseProducer).responseCompleted(exchangeContext);
        Mockito.verify(this.conn).requestInput();
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testBasicResponseNoKeepAlive() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        HttpContext exchangeContext = httpExchange.getContext();
        BasicHttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        httpExchange.setRequest(request);
        httpExchange.setRequestState(MessageState.COMPLETED);
        httpExchange.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);
        Mockito.when(this.reuseStrategy.keepAlive(response, exchangeContext)).thenReturn(false);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.READY, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.READY, httpExchange.getResponseState());

        Mockito.verify(this.httpProcessor).process(response, exchangeContext);
        Mockito.verify(this.conn).submitResponse(response);
        Mockito.verify(this.responseProducer).responseCompleted(exchangeContext);
        Mockito.verify(this.conn).close();
    }

    @Test
    public void testEntityEnclosingResponse() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        HttpContext exchangeContext = httpExchange.getContext();
        BasicHttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        httpExchange.setRequest(request);
        httpExchange.setRequestState(MessageState.COMPLETED);
        httpExchange.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.setEntity(NStringEntity.create("stuff"));
        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.COMPLETED, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.BODY_STREAM, httpExchange.getResponseState());
        Assert.assertEquals("request state: COMPLETED; request: GET / HTTP/1.1; " +
                "response state: BODY_STREAM; response: HTTP/1.1 200 OK;", httpExchange.toString());

        Mockito.verify(this.httpProcessor).process(response, exchangeContext);
        Mockito.verify(this.conn).submitResponse(response);
        Mockito.verify(this.responseProducer, Mockito.never()).responseCompleted(exchangeContext);
    }

    @Test
    public void testResponseToHead() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        HttpContext exchangeContext = httpExchange.getContext();
        BasicHttpRequest request = new BasicHttpRequest("HEAD", "/", HttpVersion.HTTP_1_1);
        httpExchange.setRequest(request);
        httpExchange.setRequestState(MessageState.COMPLETED);
        httpExchange.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.setEntity(NStringEntity.create("stuff"));
        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);
        Mockito.when(this.reuseStrategy.keepAlive(response, exchangeContext)).thenReturn(true);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.READY, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.READY, httpExchange.getResponseState());

        Mockito.verify(this.httpProcessor).process(response, exchangeContext);
        Mockito.verify(this.conn).submitResponse(response);
        Mockito.verify(this.responseProducer).responseCompleted(exchangeContext);
        Mockito.verify(this.conn).requestInput();
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testResponseNotModified() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        HttpContext exchangeContext = httpExchange.getContext();
        BasicHttpRequest request = new BasicHttpRequest("HEAD", "/", HttpVersion.HTTP_1_1);
        httpExchange.setRequest(request);
        httpExchange.setRequestState(MessageState.COMPLETED);
        httpExchange.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_NOT_MODIFIED, "Not modified");
        response.setEntity(NStringEntity.create("stuff"));
        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);
        Mockito.when(this.reuseStrategy.keepAlive(response, exchangeContext)).thenReturn(true);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.READY, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.READY, httpExchange.getResponseState());

        Mockito.verify(this.httpProcessor).process(response, exchangeContext);
        Mockito.verify(this.conn).submitResponse(response);
        Mockito.verify(this.responseProducer).responseCompleted(exchangeContext);
        Mockito.verify(this.conn).requestInput();
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testResponseContinue() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        httpExchange.setRequest(request);
        httpExchange.setRequestState(MessageState.ACK);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.BODY_STREAM, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.READY, httpExchange.getResponseState());

        Mockito.verify(this.conn).requestInput();
        Mockito.verify(this.conn).submitResponse(Mockito.argThat(new ArgumentMatcher<HttpResponse>() {

            @Override
            public boolean matches(final Object argument) {
                int status = ((HttpResponse) argument).getStatusLine().getStatusCode();
                return status == 100;
            }

        }));
    }

    @Test
    public void testResponseFailedExpectation() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        HttpContext exchangeContext = httpExchange.getContext();
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        httpExchange.setRequest(request);
        httpExchange.setRequestState(MessageState.ACK_EXPECTED);
        httpExchange.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.setEntity(NStringEntity.create("stuff"));
        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);

        this.protocolHandler.responseReady(this.conn);

        Assert.assertEquals(MessageState.COMPLETED, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.BODY_STREAM, httpExchange.getResponseState());

        Mockito.verify(this.conn).resetInput();
        Mockito.verify(this.httpProcessor).process(response, exchangeContext);
        Mockito.verify(this.conn).submitResponse(response);
        Mockito.verify(this.responseProducer, Mockito.never()).responseCompleted(exchangeContext);
    }

    @Test
    public void testResponseTrigger() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        httpExchange.setRequestState(MessageState.COMPLETED);
        httpExchange.setResponseState(MessageState.READY);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        HttpAsyncResponseTrigger trigger = this.protocolHandler.new ResponseTriggerImpl(httpExchange, this.conn);
        Assert.assertFalse(trigger.isTriggered());
        trigger.submitResponse(this.responseProducer);
        Assert.assertTrue(trigger.isTriggered());

        Assert.assertEquals(MessageState.COMPLETED, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.READY, httpExchange.getResponseState());
        Assert.assertSame(this.responseProducer, httpExchange.getResponseProducer());

        Mockito.verify(this.conn).requestOutput();

        try {
            trigger.submitResponse(Mockito.mock(HttpAsyncResponseProducer.class));
            Assert.fail("IllegalStateException expected");
        } catch (IllegalStateException ex) {
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testResponseTriggerInvalidResponseProducer() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        httpExchange.setRequestState(MessageState.ACK_EXPECTED);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        HttpAsyncResponseTrigger trigger = this.protocolHandler.new ResponseTriggerImpl(httpExchange, this.conn);
        trigger.submitResponse(null);
    }

    @Test
    public void testResponseRuntimeException() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        HttpContext exchangeContext = httpExchange.getContext();
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        httpExchange.setRequest(request);
        httpExchange.setRequestState(MessageState.ACK_EXPECTED);
        httpExchange.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.setEntity(NStringEntity.create("stuff"));
        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);

        Mockito.doThrow(new RuntimeException()).when(
                this.httpProcessor).process(response, exchangeContext);
        try {
            this.protocolHandler.responseReady(this.conn);
            Assert.fail("RuntimeException expected");
        } catch (RuntimeException ex) {
            Mockito.verify(this.conn).shutdown();
        }
    }

    @Test
    public void testResponseIOException() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        HttpContext exchangeContext = httpExchange.getContext();
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/",
                HttpVersion.HTTP_1_1);
        httpExchange.setRequest(request);
        httpExchange.setRequestState(MessageState.ACK_EXPECTED);
        httpExchange.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);

        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.setEntity(NStringEntity.create("stuff"));
        Mockito.when(this.responseProducer.generateResponse()).thenReturn(response);
        Mockito.doThrow(new HttpException()).when(
                this.httpProcessor).process(response, exchangeContext);

        this.protocolHandler.responseReady(this.conn);

        Mockito.verify(this.conn).shutdown();
    }

    @Test
    public void testResponseContent() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.setEntity(NStringEntity.create("stuff"));
        httpExchange.setRequestState(MessageState.COMPLETED);
        httpExchange.setResponseState(MessageState.BODY_STREAM);
        httpExchange.setResponse(response);
        httpExchange.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);
        Mockito.when(this.encoder.isCompleted()).thenReturn(false);

        this.protocolHandler.outputReady(conn, this.encoder);

        Assert.assertEquals(MessageState.COMPLETED, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.BODY_STREAM, httpExchange.getResponseState());

        Mockito.verify(this.responseProducer).produceContent(this.encoder, this.conn);
        Mockito.verify(this.conn, Mockito.never()).requestInput();
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testResponseContentCompleted() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        HttpContext exchangeContext = httpExchange.getContext();
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.setEntity(NStringEntity.create("stuff"));
        httpExchange.setRequestState(MessageState.COMPLETED);
        httpExchange.setResponseState(MessageState.BODY_STREAM);
        httpExchange.setResponse(response);
        httpExchange.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);
        Mockito.when(this.encoder.isCompleted()).thenReturn(true);
        Mockito.when(this.reuseStrategy.keepAlive(response, exchangeContext)).thenReturn(true);

        this.protocolHandler.outputReady(conn, this.encoder);

        Assert.assertEquals(MessageState.READY, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.READY, httpExchange.getResponseState());

        Mockito.verify(this.responseProducer).produceContent(this.encoder, this.conn);
        Mockito.verify(this.responseProducer).responseCompleted(exchangeContext);
        Mockito.verify(this.conn).requestInput();
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testResponseContentCompletedNoKeepAlive() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        HttpContext exchangeContext = httpExchange.getContext();
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.setEntity(NStringEntity.create("stuff"));
        httpExchange.setRequestState(MessageState.COMPLETED);
        httpExchange.setResponseState(MessageState.BODY_STREAM);
        httpExchange.setResponse(response);
        httpExchange.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);
        Mockito.when(this.encoder.isCompleted()).thenReturn(true);
        Mockito.when(this.reuseStrategy.keepAlive(response, exchangeContext)).thenReturn(false);

        this.protocolHandler.outputReady(conn, this.encoder);

        Assert.assertEquals(MessageState.READY, httpExchange.getRequestState());
        Assert.assertEquals(MessageState.READY, httpExchange.getResponseState());

        Mockito.verify(this.responseProducer).produceContent(this.encoder, this.conn);
        Mockito.verify(this.responseProducer).responseCompleted(exchangeContext);
        Mockito.verify(this.conn, Mockito.never()).requestInput();
        Mockito.verify(this.conn).close();
    }

    @Test
    public void testResponseContentRuntimeException() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.setEntity(NStringEntity.create("stuff"));
        httpExchange.setRequestState(MessageState.COMPLETED);
        httpExchange.setResponseState(MessageState.BODY_STREAM);
        httpExchange.setResponse(response);
        httpExchange.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);
        Mockito.when(this.encoder.isCompleted()).thenReturn(false);

        Mockito.doThrow(new RuntimeException()).when(
                this.responseProducer).produceContent(this.encoder, this.conn);
        try {
            this.protocolHandler.outputReady(conn, this.encoder);
            Assert.fail("RuntimeException expected");
        } catch (RuntimeException ex) {
            Mockito.verify(this.conn).shutdown();
        }
    }

    @Test
    public void testResponseContentIOException() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        response.setEntity(NStringEntity.create("stuff"));
        httpExchange.setRequestState(MessageState.COMPLETED);
        httpExchange.setResponseState(MessageState.BODY_STREAM);
        httpExchange.setResponse(response);
        httpExchange.setResponseProducer(this.responseProducer);
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);
        Mockito.when(this.encoder.isCompleted()).thenReturn(false);

        Mockito.doThrow(new IOException()).when(
                this.responseProducer).produceContent(this.encoder, this.conn);
        this.protocolHandler.outputReady(conn, this.encoder);
        Mockito.verify(this.conn).shutdown();
    }

    @Test
    public void testTimeoutActiveConnection() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);
        Mockito.when(this.conn.getStatus()).thenReturn(NHttpClientConnection.ACTIVE, NHttpClientConnection.CLOSED);

        this.protocolHandler.timeout(this.conn);

        Mockito.verify(this.conn).close();
        Mockito.verify(this.conn, Mockito.never()).setSocketTimeout(Mockito.anyInt());
    }

    @Test
    public void testTimeoutActiveConnectionBufferedData() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);
        Mockito.when(this.conn.getStatus()).thenReturn(NHttpClientConnection.ACTIVE, NHttpClientConnection.CLOSING);

        this.protocolHandler.timeout(this.conn);

        Mockito.verify(this.conn).close();
        Mockito.verify(this.conn).setSocketTimeout(250);
    }

    @Test
    public void testTimeoutClosingConnection() throws Exception {
        HttpExchange httpExchange = this.protocolHandler.new HttpExchange();
        this.connContext.setAttribute(HttpAsyncServiceHandler.HTTP_EXCHANGE, httpExchange);
        Mockito.when(this.conn.getStatus()).thenReturn(NHttpClientConnection.CLOSING);

        this.protocolHandler.timeout(this.conn);

        Mockito.verify(this.conn).shutdown();
    }

}

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

import junit.framework.Assert;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.nio.protocol.HttpAsyncClientProtocolHandler.HttpExchangeState;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestHttpAsyncClientProtocolHandler {

    private HttpAsyncClientProtocolHandler protocolHandler;
    private HttpContext connContext;
    private NHttpClientConnection conn;
    private HttpAsyncClientExchangeHandler<?> exchangeHandler;
    private HttpContext exchangeContext;
    private ContentEncoder encoder;
    private ContentDecoder decoder;
    private ConnectionReuseStrategy reuseStrategy;

    @Before
    public void setUp() throws Exception {
        this.protocolHandler = new HttpAsyncClientProtocolHandler();
        this.connContext = new BasicHttpContext();
        this.conn = Mockito.mock(NHttpClientConnection.class);
        this.exchangeHandler = Mockito.mock(HttpAsyncClientExchangeHandler.class);
        this.exchangeContext = new BasicHttpContext();
        this.encoder = Mockito.mock(ContentEncoder.class);
        this.decoder = Mockito.mock(ContentDecoder.class);
        this.reuseStrategy = Mockito.mock(ConnectionReuseStrategy.class);

        Mockito.when(this.conn.getContext()).thenReturn(this.connContext);
        Mockito.when(this.exchangeHandler.getContext()).thenReturn(this.exchangeContext);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testConnected() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");
        Mockito.when(this.exchangeHandler.generateRequest()).thenReturn(request);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_HANDLER, this.exchangeHandler);

        this.protocolHandler.connected(this.conn, null);

        HttpExchangeState state = (HttpExchangeState) this.connContext.getAttribute(
                HttpAsyncClientProtocolHandler.HTTP_EXCHANGE);
        Assert.assertNotNull(state);
        Assert.assertSame(this.exchangeHandler, state.getHandler());
        Mockito.verify(this.exchangeHandler).generateRequest();
        Assert.assertSame(request, state.getRequest());
        Mockito.verify(this.conn).submitRequest(request);
        Mockito.verify(this.exchangeHandler).requestCompleted(this.exchangeContext);
        Assert.assertEquals(MessageState.COMPLETED, state.getRequestState());
        Assert.assertEquals("request state: COMPLETED; request: GET / HTTP/1.1; " +
                "response state: READY; response: ; valid: true;", state.toString());
    }

    @Test
    public void testClosed() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseState(MessageState.COMPLETED);
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);

        this.protocolHandler.closed(this.conn);

        Assert.assertNull(state.getHandler());
        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
        Mockito.verify(this.exchangeHandler).close();
    }

    @Test
    public void testHttpExceptionHandling() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseState(MessageState.COMPLETED);
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);

        HttpException httpex = new HttpException();
        this.protocolHandler.exception(this.conn, httpex);

        Mockito.verify(this.exchangeHandler).failed(httpex);
        Mockito.verify(this.conn).close();
    }

    @Test
    public void testIOExceptionHandling() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseState(MessageState.COMPLETED);
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);

        IOException ioex = new IOException();
        this.protocolHandler.exception(this.conn, ioex);

        Mockito.verify(this.exchangeHandler).failed(ioex);
        Mockito.verify(this.conn).shutdown();
    }

    @Test
    public void testBasicRequest() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        HttpRequest request = new BasicHttpRequest("GET", "/");
        Mockito.when(this.exchangeHandler.generateRequest()).thenReturn(request);

        this.protocolHandler.requestReady(this.conn);

        Mockito.verify(this.exchangeHandler).generateRequest();
        Assert.assertSame(request, state.getRequest());
        Mockito.verify(this.conn).submitRequest(request);
        Mockito.verify(this.exchangeHandler).requestCompleted(this.exchangeContext);
        Assert.assertEquals(MessageState.COMPLETED, state.getRequestState());
    }

    @Test
    public void testEntityEnclosingRequestWithoutExpectContinue() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.setEntity(NStringEntity.create("stuff"));
        Mockito.when(this.exchangeHandler.generateRequest()).thenReturn(request);

        this.protocolHandler.requestReady(this.conn);

        Mockito.verify(this.exchangeHandler).generateRequest();
        Assert.assertSame(request, state.getRequest());
        Mockito.verify(this.conn).submitRequest(request);
        Mockito.verify(this.exchangeHandler, Mockito.never()).requestCompleted(this.exchangeContext);
        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
    }

    @Test
    public void testEntityEnclosingRequestWithExpectContinue() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.setHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);
        Mockito.when(this.exchangeHandler.generateRequest()).thenReturn(request);
        Mockito.when(this.conn.getSocketTimeout()).thenReturn(1000);

        this.protocolHandler.requestReady(this.conn);

        Mockito.verify(this.exchangeHandler).generateRequest();
        Assert.assertSame(request, state.getRequest());
        Mockito.verify(this.conn).submitRequest(request);
        Mockito.verify(this.conn).setSocketTimeout(3000);
        Assert.assertEquals(1000, state.getTimeout());
        Mockito.verify(this.exchangeHandler, Mockito.never()).requestCompleted(this.exchangeContext);
        Assert.assertEquals(MessageState.ACK_EXPECTED, state.getRequestState());
    }

    @Test
    public void testRequestRuntimeException() throws Exception {
        RuntimeException runtimeex = new RuntimeException();
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        Mockito.when(this.exchangeHandler.generateRequest()).thenThrow(runtimeex);
        try {
            this.protocolHandler.requestReady(this.conn);
            Assert.fail("RuntimeException expected");
        } catch (RuntimeException ex) {
            Mockito.verify(this.conn).shutdown();
            Mockito.verify(this.exchangeHandler).failed(ex);
        }
    }

    @Test
    public void testRequestHttpException() throws Exception {
        HttpException httpex = new HttpException();
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        Mockito.when(this.exchangeHandler.generateRequest()).thenThrow(httpex);

        this.protocolHandler.requestReady(this.conn);

        Mockito.verify(this.conn).close();
        Mockito.verify(this.exchangeHandler).failed(httpex);
    }

    @Test
    public void testRequestContentOutput() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        Mockito.when(this.encoder.isCompleted()).thenReturn(false);

        this.protocolHandler.outputReady(this.conn, this.encoder);

        Mockito.verify(this.exchangeHandler).produceContent(this.encoder, this.conn);
        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
    }

    @Test
    public void testRequestContentOutputCompleted() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        Mockito.when(this.encoder.isCompleted()).thenReturn(true);

        this.protocolHandler.outputReady(this.conn, this.encoder);

        Mockito.verify(this.exchangeHandler).produceContent(this.encoder, this.conn);
        Mockito.verify(this.exchangeHandler).requestCompleted(this.exchangeContext);
        Assert.assertEquals(MessageState.COMPLETED, state.getRequestState());
    }

    @Test
    public void testRequestContentContinueExpected() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setRequestState(MessageState.ACK_EXPECTED);
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);

        this.protocolHandler.outputReady(this.conn, this.encoder);

        Mockito.verify(this.conn).suspendOutput();
        Mockito.verify(this.exchangeHandler, Mockito.never()).produceContent(this.encoder, this.conn);
        Assert.assertEquals(MessageState.ACK_EXPECTED, state.getRequestState());
    }

    @Test
    public void testRequestContentRuntimeException() throws Exception {
        RuntimeException runtimeex = new RuntimeException();
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        Mockito.doThrow(runtimeex).when(this.exchangeHandler).produceContent(this.encoder, this.conn);

        try {
            this.protocolHandler.outputReady(this.conn, this.encoder);
            Assert.fail("RuntimeException expected");
        } catch (RuntimeException ex) {
            Mockito.verify(this.conn).shutdown();
            Mockito.verify(this.exchangeHandler).failed(ex);
        }
    }

    @Test
    public void testRequestContentIOException() throws Exception {
        IOException ioex = new IOException();
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        Mockito.doThrow(ioex).when(this.exchangeHandler).produceContent(this.encoder, this.conn);

        this.protocolHandler.outputReady(this.conn, this.encoder);

        Mockito.verify(this.conn).shutdown();
        Mockito.verify(this.exchangeHandler).failed(ioex);
    }

    @Test
    public void testBasicResponse() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        HttpRequest request = new BasicHttpRequest("GET", "/");
        state.setRequest(request);
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(this.conn.getHttpResponse()).thenReturn(response);

        this.protocolHandler.responseReceived(this.conn);

        Assert.assertSame(response, state.getResponse());
        Assert.assertEquals(MessageState.BODY_STREAM, state.getResponseState());
    }

    @Test
    public void testResponseContinue() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setRequestState(MessageState.ACK_EXPECTED);
        state.setTimeout(1000);
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.setEntity(NStringEntity.create("stuff"));
        state.setRequest(request);
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 100, "Continue");
        Mockito.when(this.conn.getHttpResponse()).thenReturn(response);

        this.protocolHandler.responseReceived(this.conn);

        Assert.assertNull(state.getResponse());
        Assert.assertEquals(MessageState.ACK, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
        Mockito.verify(this.conn).setSocketTimeout(1000);
        Mockito.verify(this.conn).requestOutput();
    }

    @Test
    public void testResponseContinueOutOfSequence() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setRequestState(MessageState.COMPLETED);
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.setEntity(NStringEntity.create("stuff"));
        state.setRequest(request);
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 100, "Continue");
        Mockito.when(this.conn.getHttpResponse()).thenReturn(response);

        this.protocolHandler.responseReceived(this.conn);

        Assert.assertNull(state.getResponse());
        Assert.assertEquals(MessageState.COMPLETED, state.getRequestState());
        Mockito.verify(this.conn, Mockito.never()).requestOutput();
    }

    @Test
    public void testResponseUnsupported1xx() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setRequestState(MessageState.ACK_EXPECTED);
        state.setTimeout(1000);
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.setEntity(NStringEntity.create("stuff"));
        state.setRequest(request);
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 111, "WTF?");
        Mockito.when(this.conn.getHttpResponse()).thenReturn(response);

        this.protocolHandler.responseReceived(this.conn);

        Assert.assertNull(state.getResponse());
        Assert.assertEquals(MessageState.ACK_EXPECTED, state.getRequestState());
        Mockito.verify(this.conn).shutdown();
        Mockito.verify(this.exchangeHandler).failed(Mockito.any(HttpException.class));
    }

    @Test
    public void testResponseExpectationFailed() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setRequestState(MessageState.ACK_EXPECTED);
        state.setTimeout(1000);
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.setEntity(NStringEntity.create("stuff"));
        state.setRequest(request);
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 403, "Unauthorized");
        Mockito.when(this.conn.getHttpResponse()).thenReturn(response);

        this.protocolHandler.responseReceived(this.conn);

        Assert.assertSame(response, state.getResponse());
        Assert.assertEquals(MessageState.COMPLETED, state.getRequestState());
        Assert.assertEquals(MessageState.BODY_STREAM, state.getResponseState());
        Mockito.verify(this.conn).setSocketTimeout(1000);
        Mockito.verify(this.conn).resetOutput();
    }

    @Test
    public void testEarlyResponse() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setRequestState(MessageState.BODY_STREAM);
        state.setTimeout(1000);
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.setEntity(NStringEntity.create("stuff"));
        state.setRequest(request);
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 403, "Unauthorized");
        Mockito.when(this.conn.getHttpResponse()).thenReturn(response);

        this.protocolHandler.responseReceived(this.conn);

        Assert.assertSame(response, state.getResponse());
        Assert.assertEquals(MessageState.COMPLETED, state.getRequestState());
        Assert.assertEquals(MessageState.BODY_STREAM, state.getResponseState());
        Mockito.verify(this.conn).suspendOutput();
        Mockito.verify(this.conn).resetOutput();
        Assert.assertFalse(state.isValid());
    }

    @Test
    public void testResponseToHead() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        HttpRequest request = new BasicHttpRequest("HEAD", "/");
        state.setRequest(request);
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(this.conn.getHttpResponse()).thenReturn(response);
        Mockito.when(this.reuseStrategy.keepAlive(response, this.exchangeContext)).thenReturn(true);
        Mockito.when(this.exchangeHandler.getConnectionReuseStrategy()).thenReturn(this.reuseStrategy);

        this.protocolHandler.responseReceived(this.conn);

        Assert.assertEquals(MessageState.READY, state.getResponseState());
        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertNull(state.getRequest());
        Assert.assertNull(state.getResponse());
        Mockito.verify(this.exchangeHandler).responseReceived(response);
        Mockito.verify(this.conn).resetInput();
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testResponseToConnect() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        HttpRequest request = new BasicHttpRequest("Connect", "/");
        state.setRequest(request);
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(this.conn.getHttpResponse()).thenReturn(response);
        Mockito.when(this.reuseStrategy.keepAlive(response, this.exchangeContext)).thenReturn(true);
        Mockito.when(this.exchangeHandler.getConnectionReuseStrategy()).thenReturn(this.reuseStrategy);

        this.protocolHandler.responseReceived(this.conn);

        Assert.assertEquals(MessageState.READY, state.getResponseState());
        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertNull(state.getRequest());
        Assert.assertNull(state.getResponse());
        Mockito.verify(this.exchangeHandler).responseReceived(response);
        Mockito.verify(this.conn).resetInput();
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testResponseNotModified() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        HttpRequest request = new BasicHttpRequest("Connect", "/");
        state.setRequest(request);
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_NOT_MODIFIED, "Not modified");
        Mockito.when(this.conn.getHttpResponse()).thenReturn(response);
        Mockito.when(this.reuseStrategy.keepAlive(response, this.exchangeContext)).thenReturn(true);
        Mockito.when(this.exchangeHandler.getConnectionReuseStrategy()).thenReturn(this.reuseStrategy);

        this.protocolHandler.responseReceived(this.conn);

        Assert.assertEquals(MessageState.READY, state.getResponseState());
        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertNull(state.getRequest());
        Assert.assertNull(state.getResponse());
        Mockito.verify(this.exchangeHandler).responseReceived(response);
        Mockito.verify(this.conn).resetInput();
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testResponseRuntimeException() throws Exception {
        RuntimeException runtimeex = new RuntimeException();
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        HttpRequest request = new BasicHttpRequest("GET", "/");
        state.setRequest(request);
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(this.conn.getHttpResponse()).thenReturn(response);
        Mockito.doThrow(runtimeex).when(this.exchangeHandler).responseReceived(response);
        try {
            this.protocolHandler.responseReceived(this.conn);
            Assert.fail("RuntimeException expected");
        } catch (RuntimeException ex) {
            Mockito.verify(this.conn).shutdown();
            Mockito.verify(this.exchangeHandler).failed(ex);
        }
    }

    @Test
    public void testResponseHttpException() throws Exception {
        HttpException httpex = new HttpException();
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        HttpRequest request = new BasicHttpRequest("GET", "/");
        state.setRequest(request);
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(this.conn.getHttpResponse()).thenReturn(response);
        Mockito.doThrow(httpex).when(this.exchangeHandler).responseReceived(response);

        this.protocolHandler.responseReceived(this.conn);

        Mockito.verify(this.conn).close();
        Mockito.verify(this.exchangeHandler).failed(httpex);
    }

    @Test
    public void testResponseContentInput() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        Mockito.when(this.decoder.isCompleted()).thenReturn(false);

        this.protocolHandler.inputReady(this.conn, this.decoder);

        Mockito.verify(this.exchangeHandler).consumeContent(this.decoder, this.conn);
        Assert.assertEquals(MessageState.BODY_STREAM, state.getResponseState());
    }

    @Test
    public void testResponseContentOutputCompleted() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        HttpRequest request = new BasicHttpRequest("GET", "/");
        state.setRequest(request);
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        state.setResponse(response);
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        Mockito.when(this.reuseStrategy.keepAlive(response, this.exchangeContext)).thenReturn(true);
        Mockito.when(this.exchangeHandler.getConnectionReuseStrategy()).thenReturn(this.reuseStrategy);
        Mockito.when(this.decoder.isCompleted()).thenReturn(true);

        this.protocolHandler.inputReady(this.conn, this.decoder);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
        Mockito.verify(this.exchangeHandler).consumeContent(this.decoder, this.conn);
        Mockito.verify(this.exchangeHandler).responseCompleted(this.exchangeContext);
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testResponseInvalidState() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        HttpRequest request = new BasicHttpRequest("GET", "/");
        state.setRequest(request);
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        state.setResponse(response);
        state.invalidate();
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        Mockito.when(this.decoder.isCompleted()).thenReturn(true);

        this.protocolHandler.inputReady(this.conn, this.decoder);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
        Mockito.verify(this.exchangeHandler).consumeContent(this.decoder, this.conn);
        Mockito.verify(this.conn).close();
        Mockito.verify(this.exchangeHandler, Mockito.never()).getConnectionReuseStrategy();
    }

    @Test
    public void testResponseNoKeepAlive() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        HttpRequest request = new BasicHttpRequest("GET", "/");
        state.setRequest(request);
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        state.setResponse(response);
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        Mockito.when(this.reuseStrategy.keepAlive(response, this.exchangeContext)).thenReturn(false);
        Mockito.when(this.exchangeHandler.getConnectionReuseStrategy()).thenReturn(this.reuseStrategy);
        Mockito.when(this.decoder.isCompleted()).thenReturn(true);

        this.protocolHandler.inputReady(this.conn, this.decoder);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
        Mockito.verify(this.exchangeHandler).consumeContent(this.decoder, this.conn);
        Mockito.verify(this.exchangeHandler).responseCompleted(this.exchangeContext);
        Mockito.verify(this.conn).close();
    }

    @Test
    public void testResponseContentRuntimeException() throws Exception {
        RuntimeException runtimeex = new RuntimeException();
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        Mockito.doThrow(runtimeex).when(this.exchangeHandler).consumeContent(this.decoder, this.conn);

        try {
            this.protocolHandler.inputReady(this.conn, this.decoder);
            Assert.fail("RuntimeException expected");
        } catch (RuntimeException ex) {
            Mockito.verify(this.conn).shutdown();
            Mockito.verify(this.exchangeHandler).failed(ex);
        }
    }

    @Test
    public void testResponseContentIOException() throws Exception {
        IOException ioex = new IOException();
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        Mockito.doThrow(ioex).when(this.exchangeHandler).consumeContent(this.decoder, this.conn);

        this.protocolHandler.inputReady(this.conn, this.decoder);

        Mockito.verify(this.conn).shutdown();
        Mockito.verify(this.exchangeHandler).failed(ioex);
    }

    @Test
    public void testTimeoutNoHandler() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);

        Mockito.when(this.conn.getStatus()).thenReturn(
                NHttpConnection.ACTIVE, NHttpConnection.CLOSING);

        this.protocolHandler.timeout(this.conn);

        Mockito.verify(this.conn).close();
        Mockito.verify(this.conn).setSocketTimeout(250);
    }

    @Test
    public void testExpectContinueTimeout() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setRequestState(MessageState.ACK_EXPECTED);
        state.setTimeout(1000);
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);

        this.protocolHandler.timeout(this.conn);

        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
        Mockito.verify(this.conn).setSocketTimeout(1000);
        Mockito.verify(this.conn).requestOutput();
    }

    @Test
    public void testTimeoutActiveConnection() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setRequestState(MessageState.BODY_STREAM);
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        Mockito.when(this.conn.getStatus()).thenReturn(NHttpConnection.ACTIVE, NHttpConnection.CLOSED);

        this.protocolHandler.timeout(this.conn);

        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
        Mockito.verify(this.exchangeHandler).failed(Mockito.any(SocketTimeoutException.class));
        Mockito.verify(this.conn).close();
        Mockito.verify(this.conn, Mockito.never()).setSocketTimeout(Mockito.anyInt());
    }

    @Test
    public void testTimeoutActiveConnectionBufferedData() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setRequestState(MessageState.BODY_STREAM);
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        Mockito.when(this.conn.getStatus()).thenReturn(NHttpConnection.ACTIVE, NHttpConnection.CLOSING);

        this.protocolHandler.timeout(this.conn);

        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
        Mockito.verify(this.exchangeHandler).failed(Mockito.any(SocketTimeoutException.class));
        Mockito.verify(this.conn).close();
        Mockito.verify(this.conn).setSocketTimeout(250);
    }

    @Test
    public void testTimeoutClosingConnection() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        state.setRequestState(MessageState.BODY_STREAM);
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        Mockito.when(this.conn.getStatus()).thenReturn(NHttpConnection.CLOSING, NHttpConnection.CLOSED);

        this.protocolHandler.timeout(this.conn);

        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
        Mockito.verify(this.exchangeHandler).failed(Mockito.any(SocketTimeoutException.class));
        Mockito.verify(this.conn).shutdown();
    }

    @Test
    public void testExchangeDone() throws Exception {
        HttpExchangeState state = this.protocolHandler.new HttpExchangeState();
        HttpRequest request = new BasicHttpRequest("GET", "/");
        state.setRequest(request);
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        state.setResponse(response);
        state.setHandler(this.exchangeHandler);
        this.connContext.setAttribute(HttpAsyncClientProtocolHandler.HTTP_EXCHANGE, state);
        Mockito.when(this.exchangeHandler.isDone()).thenReturn(true);

        Assert.assertEquals("request state: READY; request: GET / HTTP/1.1; " +
                "response state: READY; response: HTTP/1.1 200 OK; valid: true;",
                state.toString());

        this.protocolHandler.requestReady(this.conn);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
        Assert.assertNull(state.getHandler());
    }

}

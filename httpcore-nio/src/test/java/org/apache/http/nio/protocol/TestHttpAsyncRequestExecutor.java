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

import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.nio.protocol.HttpAsyncRequestExecutor.State;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;

public class TestHttpAsyncRequestExecutor {

    private HttpAsyncRequestExecutor protocolHandler;
    private HttpContext connContext;
    private NHttpClientConnection conn;
    private HttpAsyncClientExchangeHandler exchangeHandler;
    private ContentEncoder encoder;
    private ContentDecoder decoder;

    @Before
    public void setUp() throws Exception {
        this.protocolHandler = new HttpAsyncRequestExecutor();
        this.connContext = new BasicHttpContext();
        this.conn = Mockito.mock(NHttpClientConnection.class);
        this.exchangeHandler = Mockito.mock(HttpAsyncClientExchangeHandler.class);
        this.encoder = Mockito.mock(ContentEncoder.class);
        this.decoder = Mockito.mock(ContentDecoder.class);
        Mockito.when(this.conn.getContext()).thenReturn(this.connContext);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testConnected() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        Mockito.when(this.exchangeHandler.generateRequest()).thenReturn(request);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);

        this.protocolHandler.connected(this.conn, null);

        final State state = (State) this.connContext.getAttribute(
                HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE);
        Assert.assertNotNull(state);
        Mockito.verify(this.exchangeHandler).generateRequest();
        Assert.assertSame(request, state.getRequest());
        Mockito.verify(this.conn).submitRequest(request);
        Mockito.verify(this.exchangeHandler).requestCompleted();
        Assert.assertEquals(MessageState.COMPLETED, state.getRequestState());
        Assert.assertEquals("request state: COMPLETED; request: GET / HTTP/1.1; " +
                "response state: READY; response: ; valid: true;", state.toString());
    }

    @Test
    public void testClosed() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        state.setRequestState(MessageState.READY);
        state.setResponseState(MessageState.READY);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);

        this.protocolHandler.closed(this.conn);

        Mockito.verify(this.exchangeHandler, Mockito.never()).close();
    }

    @Test
    public void testClosedNullState() throws Exception {
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);

        this.protocolHandler.closed(this.conn);

        Mockito.verify(this.exchangeHandler).close();
    }

    @Test
    public void testClosedInconsistentState() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseState(MessageState.INIT);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);

        this.protocolHandler.closed(this.conn);

        Mockito.verify(this.exchangeHandler).failed(Matchers.any(ConnectionClosedException.class));
    }

    @Test
    public void testHttpExceptionHandling() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseState(MessageState.COMPLETED);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);

        final HttpException httpex = new HttpException();
        this.protocolHandler.exception(this.conn, httpex);

        Mockito.verify(this.exchangeHandler).failed(httpex);
        Mockito.verify(this.conn).shutdown();
    }

    @Test
    public void testIOExceptionHandling() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseState(MessageState.COMPLETED);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);

        final IOException ioex = new IOException();
        this.protocolHandler.exception(this.conn, ioex);

        Mockito.verify(this.exchangeHandler).failed(ioex);
        Mockito.verify(this.conn).shutdown();
    }

    @Test
    public void testBasicRequest() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        Mockito.when(this.exchangeHandler.generateRequest()).thenReturn(request);

        this.protocolHandler.requestReady(this.conn);

        Mockito.verify(this.exchangeHandler).generateRequest();
        Assert.assertSame(request, state.getRequest());

        Mockito.verify(this.conn).submitRequest(request);
        Mockito.verify(this.exchangeHandler).requestCompleted();
        Assert.assertEquals(MessageState.COMPLETED, state.getRequestState());
    }

    @Test
    public void testNullRequest() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        Mockito.when(this.exchangeHandler.generateRequest()).thenReturn(null);

        this.protocolHandler.requestReady(this.conn);

        Mockito.verify(this.exchangeHandler).generateRequest();
        Assert.assertNull(state.getRequest());

        Mockito.verify(this.conn, Mockito.times(1)).suspendOutput();
        Mockito.verify(this.conn, Mockito.never()).submitRequest(Matchers.<HttpRequest>any());
    }

    @Test
    public void testEntityEnclosingRequestWithoutExpectContinue() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.setEntity(new NStringEntity("stuff"));
        Mockito.when(this.exchangeHandler.generateRequest()).thenReturn(request);

        this.protocolHandler.requestReady(this.conn);

        Mockito.verify(this.exchangeHandler).generateRequest();
        Assert.assertSame(request, state.getRequest());
        Mockito.verify(this.conn).submitRequest(request);
        Mockito.verify(this.exchangeHandler, Mockito.never()).requestCompleted();
        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
    }

    @Test
    public void testEntityEnclosingRequestWithExpectContinue() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.setHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);
        Mockito.when(this.exchangeHandler.generateRequest()).thenReturn(request);
        Mockito.when(this.conn.getSocketTimeout()).thenReturn(1000);

        this.protocolHandler.requestReady(this.conn);

        Mockito.verify(this.exchangeHandler).generateRequest();
        Assert.assertSame(request, state.getRequest());
        Mockito.verify(this.conn).submitRequest(request);
        Mockito.verify(this.conn).setSocketTimeout(3000);
        Assert.assertEquals(1000, state.getTimeout());
        Mockito.verify(this.exchangeHandler, Mockito.never()).requestCompleted();
        Assert.assertEquals(MessageState.ACK_EXPECTED, state.getRequestState());
    }

    @Test
    public void testRequestContentOutput() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        state.setRequestState(MessageState.BODY_STREAM);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        Mockito.when(this.encoder.isCompleted()).thenReturn(Boolean.FALSE);

        this.protocolHandler.outputReady(this.conn, this.encoder);

        Mockito.verify(this.exchangeHandler).produceContent(this.encoder, this.conn);
        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
    }

    @Test
    public void testRequestContentOutputCompleted() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        state.setRequestState(MessageState.BODY_STREAM);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        Mockito.when(this.encoder.isCompleted()).thenReturn(Boolean.TRUE);

        this.protocolHandler.outputReady(this.conn, this.encoder);

        Mockito.verify(this.exchangeHandler).produceContent(this.encoder, this.conn);
        Mockito.verify(this.exchangeHandler).requestCompleted();
        Assert.assertEquals(MessageState.COMPLETED, state.getRequestState());
    }

    @Test
    public void testRequestContentContinueExpected() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        state.setRequestState(MessageState.ACK_EXPECTED);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);

        this.protocolHandler.outputReady(this.conn, this.encoder);

        Mockito.verify(this.conn).suspendOutput();
        Mockito.verify(this.exchangeHandler, Mockito.never()).produceContent(this.encoder, this.conn);
        Assert.assertEquals(MessageState.ACK_EXPECTED, state.getRequestState());
    }

    @Test
    public void testBasicResponse() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        state.setRequest(request);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(this.conn.getHttpResponse()).thenReturn(response);

        this.protocolHandler.responseReceived(this.conn);

        Assert.assertSame(response, state.getResponse());
        Assert.assertEquals(MessageState.BODY_STREAM, state.getResponseState());
    }

    @Test
    public void testResponseContinue() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        state.setRequestState(MessageState.ACK_EXPECTED);
        state.setTimeout(1000);
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.setEntity(new NStringEntity("stuff"));
        state.setRequest(request);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 100, "Continue");
        Mockito.when(this.conn.getHttpResponse()).thenReturn(response);

        this.protocolHandler.responseReceived(this.conn);

        Assert.assertNull(state.getResponse());
        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
        Mockito.verify(this.conn).setSocketTimeout(1000);
        Mockito.verify(this.conn).requestOutput();
    }

    @Test
    public void testResponseContinueOutOfSequence() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        state.setRequestState(MessageState.COMPLETED);
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.setEntity(new NStringEntity("stuff"));
        state.setRequest(request);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 100, "Continue");
        Mockito.when(this.conn.getHttpResponse()).thenReturn(response);

        this.protocolHandler.responseReceived(this.conn);

        Assert.assertNull(state.getResponse());
        Assert.assertEquals(MessageState.COMPLETED, state.getRequestState());
        Mockito.verify(this.conn, Mockito.never()).requestOutput();
    }

    @Test(expected=HttpException.class)
    public void testResponseUnsupported1xx() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        state.setRequestState(MessageState.ACK_EXPECTED);
        state.setTimeout(1000);
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.setEntity(new NStringEntity("stuff"));
        state.setRequest(request);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 111, "WTF?");
        Mockito.when(this.conn.getHttpResponse()).thenReturn(response);

        this.protocolHandler.responseReceived(this.conn);
    }

    @Test
    public void testResponseExpectationFailed() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        state.setRequestState(MessageState.ACK_EXPECTED);
        state.setTimeout(1000);
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.setEntity(new NStringEntity("stuff"));
        state.setRequest(request);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 403, "Unauthorized");
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
        final State state = new HttpAsyncRequestExecutor.State();
        state.setRequestState(MessageState.BODY_STREAM);
        state.setTimeout(1000);
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.setEntity(new NStringEntity("stuff"));
        state.setRequest(request);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 403, "Unauthorized");
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
    public void testEarlyNonErrorResponse() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        state.setRequestState(MessageState.BODY_STREAM);
        state.setTimeout(1000);
        final BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.setEntity(new NStringEntity("stuff"));
        state.setRequest(request);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(this.conn.getHttpResponse()).thenReturn(response);

        this.protocolHandler.responseReceived(this.conn);

        Assert.assertSame(response, state.getResponse());
        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
        Assert.assertEquals(MessageState.BODY_STREAM, state.getResponseState());
        Mockito.verify(this.conn, Mockito.never()).suspendOutput();
        Mockito.verify(this.conn, Mockito.never()).resetOutput();
        Assert.assertTrue(state.isValid());
    }

    @Test
    public void testResponseToHead() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        final HttpRequest request = new BasicHttpRequest("HEAD", "/");
        state.setRequest(request);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(this.conn.getHttpResponse()).thenReturn(response);

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
        final State state = new HttpAsyncRequestExecutor.State();
        final HttpRequest request = new BasicHttpRequest("Connect", "/");
        state.setRequest(request);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(this.conn.getHttpResponse()).thenReturn(response);

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
        final State state = new HttpAsyncRequestExecutor.State();
        final HttpRequest request = new BasicHttpRequest("Connect", "/");
        state.setRequest(request);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1,
                HttpStatus.SC_NOT_MODIFIED, "Not modified");
        response.setEntity(new BasicHttpEntity());
        Mockito.when(this.conn.getHttpResponse()).thenReturn(response);

        this.protocolHandler.responseReceived(this.conn);

        Assert.assertEquals(MessageState.READY, state.getResponseState());
        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertNull(state.getRequest());
        Assert.assertNull(state.getResponse());
        Assert.assertNull(response.getEntity());
        Mockito.verify(this.exchangeHandler).responseReceived(response);
        Mockito.verify(this.conn).resetInput();
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testResponseContentInput() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        state.setResponseState(MessageState.BODY_STREAM);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        Mockito.when(this.decoder.isCompleted()).thenReturn(Boolean.FALSE);

        this.protocolHandler.inputReady(this.conn, this.decoder);

        Mockito.verify(this.exchangeHandler).consumeContent(this.decoder, this.conn);
        Assert.assertEquals(MessageState.BODY_STREAM, state.getResponseState());
    }

    @Test
    public void testResponseContentOutputCompleted() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        state.setRequest(request);
        state.setResponseState(MessageState.BODY_STREAM);
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        state.setResponse(response);
        Mockito.when(this.exchangeHandler.isDone()).thenReturn(Boolean.TRUE);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        Mockito.when(this.decoder.isCompleted()).thenReturn(Boolean.TRUE);

        this.protocolHandler.inputReady(this.conn, this.decoder);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
        Mockito.verify(this.exchangeHandler).consumeContent(this.decoder, this.conn);
        Mockito.verify(this.exchangeHandler).responseCompleted();
        Mockito.verify(this.conn, Mockito.never()).close();
    }

    @Test
    public void testResponseContentOutputCompletedHandlerNotDone() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        state.setResponseState(MessageState.BODY_STREAM);
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        state.setRequest(request);
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        state.setResponse(response);
        Mockito.when(this.exchangeHandler.isDone()).thenReturn(Boolean.FALSE);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        Mockito.when(this.decoder.isCompleted()).thenReturn(Boolean.TRUE);
        Mockito.when(this.conn.isOpen()).thenReturn(Boolean.TRUE);

        this.protocolHandler.inputReady(this.conn, this.decoder);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
        Mockito.verify(this.exchangeHandler).consumeContent(this.decoder, this.conn);
        Mockito.verify(this.exchangeHandler).responseCompleted();
        Mockito.verify(this.conn).requestOutput();
    }

    @Test
    public void testResponseContentOutputCompletedHandlerNotDoneConnClosed() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        state.setRequest(request);
        state.setResponseState(MessageState.BODY_STREAM);
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        state.setResponse(response);
        Mockito.when(this.exchangeHandler.isDone()).thenReturn(Boolean.FALSE);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        Mockito.when(this.decoder.isCompleted()).thenReturn(Boolean.TRUE);
        Mockito.when(this.conn.isOpen()).thenReturn(Boolean.FALSE);

        this.protocolHandler.inputReady(this.conn, this.decoder);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
        Mockito.verify(this.exchangeHandler).consumeContent(this.decoder, this.conn);
        Mockito.verify(this.exchangeHandler).responseCompleted();
        Mockito.verify(this.conn, Mockito.never()).requestOutput();
    }

    @Test
    public void testResponseInvalidState() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        state.setRequest(request);
        state.setResponseState(MessageState.BODY_STREAM);
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        state.setResponse(response);
        state.invalidate();
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        Mockito.when(this.decoder.isCompleted()).thenReturn(Boolean.TRUE);

        this.protocolHandler.inputReady(this.conn, this.decoder);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
        Mockito.verify(this.exchangeHandler).consumeContent(this.decoder, this.conn);
        Mockito.verify(this.conn).close();
    }

    @Test
    public void testEndOfInput() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);

        this.protocolHandler.endOfInput(this.conn);

        Mockito.verify(this.conn).close();
    }

    @Test
    public void testPrematureEndOfInput() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        state.setRequestState(MessageState.COMPLETED);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);

        this.protocolHandler.endOfInput(this.conn);

        Assert.assertFalse(state.isValid());

        Mockito.verify(this.conn).close();
        Mockito.verify(this.exchangeHandler).failed(Matchers.any(ConnectionClosedException.class));
    }

    @Test
    public void testPrematureEndOfInputRequestReady() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        state.setRequestState(MessageState.READY);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);

        this.protocolHandler.endOfInput(this.conn);

        Assert.assertTrue(state.isValid());

        Mockito.verify(this.exchangeHandler).inputTerminated();
    }

    @Test
    public void testTimeoutNoHandler() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);

        Mockito.when(this.conn.getStatus()).thenReturn(
                NHttpConnection.ACTIVE, NHttpConnection.CLOSING);

        this.protocolHandler.timeout(this.conn);

        Mockito.verify(this.conn).close();
        Mockito.verify(this.conn).setSocketTimeout(250);
    }

    @Test
    public void testExpectContinueTimeout() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        state.setRequestState(MessageState.ACK_EXPECTED);
        state.setTimeout(1000);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);

        this.protocolHandler.timeout(this.conn);

        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
        Assert.assertTrue(state.isValid());
        Mockito.verify(this.conn).setSocketTimeout(1000);
        Mockito.verify(this.conn).requestOutput();
    }

    @Test
    public void testTimeoutActiveConnection() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        state.setRequestState(MessageState.BODY_STREAM);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        Mockito.when(this.conn.getStatus()).thenReturn(NHttpConnection.ACTIVE, NHttpConnection.CLOSED);

        this.protocolHandler.timeout(this.conn);

        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
        Assert.assertFalse(state.isValid());
        Mockito.verify(this.exchangeHandler).failed(Matchers.any(SocketTimeoutException.class));
        Mockito.verify(this.exchangeHandler).close();
        Mockito.verify(this.conn).close();
        Mockito.verify(this.conn, Mockito.never()).setSocketTimeout(Matchers.anyInt());
    }

    @Test
    public void testTimeoutActiveConnectionBufferedData() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        state.setRequestState(MessageState.BODY_STREAM);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        Mockito.when(this.conn.getStatus()).thenReturn(NHttpConnection.ACTIVE, NHttpConnection.CLOSING);

        this.protocolHandler.timeout(this.conn);

        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
        Assert.assertFalse(state.isValid());
        Mockito.verify(this.exchangeHandler).failed(Matchers.any(SocketTimeoutException.class));
        Mockito.verify(this.exchangeHandler).close();
        Mockito.verify(this.conn).close();
        Mockito.verify(this.conn).setSocketTimeout(250);
    }

    @Test
    public void testTimeoutClosingConnection() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        state.setRequestState(MessageState.BODY_STREAM);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        Mockito.when(this.conn.getStatus()).thenReturn(NHttpConnection.CLOSING, NHttpConnection.CLOSED);

        this.protocolHandler.timeout(this.conn);

        Assert.assertEquals(MessageState.BODY_STREAM, state.getRequestState());
        Assert.assertFalse(state.isValid());
        Mockito.verify(this.exchangeHandler).failed(Matchers.any(SocketTimeoutException.class));
        Mockito.verify(this.exchangeHandler).close();
        Mockito.verify(this.conn).shutdown();
    }

    @Test
    public void testExchangeDone() throws Exception {
        final State state = new HttpAsyncRequestExecutor.State();
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        state.setRequest(request);
        final BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        state.setResponse(response);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_EXCHANGE_STATE, state);
        this.connContext.setAttribute(HttpAsyncRequestExecutor.HTTP_HANDLER, this.exchangeHandler);
        Mockito.when(this.exchangeHandler.isDone()).thenReturn(Boolean.TRUE);

        Assert.assertEquals("request state: READY; request: GET / HTTP/1.1; " +
                "response state: READY; response: HTTP/1.1 200 OK; valid: true;",
                state.toString());

        this.protocolHandler.requestReady(this.conn);

        Assert.assertEquals(MessageState.READY, state.getRequestState());
        Assert.assertEquals(MessageState.READY, state.getResponseState());
    }

}

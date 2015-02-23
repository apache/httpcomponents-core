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

package org.apache.http.protocol;

import java.io.InputStream;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpServerConnection;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.ProtocolException;
import org.apache.http.UnsupportedHttpVersionException;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TestHttpService {

    @Test
    public void testInvalidInitialization() throws Exception {
        final ConnectionReuseStrategy connReuseStrategy = Mockito.mock(ConnectionReuseStrategy.class);
        final HttpResponseFactory responseFactory = Mockito.mock(HttpResponseFactory.class);
        final HttpRequestHandlerMapper handlerResolver = Mockito.mock(HttpRequestHandlerMapper.class);
        try {
            new HttpService(
                    null,
                    connReuseStrategy,
                    responseFactory,
                    handlerResolver);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
    }

    @Test
    public void testBasicExecution() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final ConnectionReuseStrategy connReuseStrategy = Mockito.mock(ConnectionReuseStrategy.class);
        final HttpResponseFactory responseFactory = Mockito.mock(HttpResponseFactory.class);
        final HttpRequestHandlerMapper handlerResolver = Mockito.mock(HttpRequestHandlerMapper.class);

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver);
        final HttpCoreContext context = HttpCoreContext.create();
        final HttpServerConnection conn = Mockito.mock(HttpServerConnection.class);
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        Mockito.when(conn.receiveRequestHeader()).thenReturn(request);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(responseFactory.newHttpResponse(HttpVersion.HTTP_1_1, 200, context)).thenReturn(response);
        Mockito.when(connReuseStrategy.keepAlive(response, context)).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Assert.assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, response.getStatusLine().getStatusCode());

        Assert.assertSame(conn, context.getConnection());
        Assert.assertSame(request, context.getRequest());
        Assert.assertSame(response, context.getResponse());

        Mockito.verify(httprocessor).process(request, context);
        Mockito.verify(httprocessor).process(response, context);
        Mockito.verify(conn).sendResponseHeader(response);
        Mockito.verify(conn).sendResponseEntity(response);
        Mockito.verify(conn).flush();
        Mockito.verify(conn).close();
    }

    @Test
    public void testBasicExecutionHTTP10() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final ConnectionReuseStrategy connReuseStrategy = Mockito.mock(ConnectionReuseStrategy.class);
        final HttpResponseFactory responseFactory = Mockito.mock(HttpResponseFactory.class);
        final HttpRequestHandlerMapper handlerResolver = Mockito.mock(HttpRequestHandlerMapper.class);

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver);
        final HttpCoreContext context = HttpCoreContext.create();
        final HttpServerConnection conn = Mockito.mock(HttpServerConnection.class);
        final HttpRequest request = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_0);
        Mockito.when(conn.receiveRequestHeader()).thenReturn(request);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_0, 200, "OK");
        Mockito.when(responseFactory.newHttpResponse(HttpVersion.HTTP_1_1, 200, context)).thenReturn(response);
        Mockito.when(connReuseStrategy.keepAlive(response, context)).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Mockito.verify(responseFactory).newHttpResponse(HttpVersion.HTTP_1_1, 200, context);
    }

    @Test
    public void testBasicProtocolDowngrade() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final ConnectionReuseStrategy connReuseStrategy = Mockito.mock(ConnectionReuseStrategy.class);
        final HttpResponseFactory responseFactory = Mockito.mock(HttpResponseFactory.class);
        final HttpRequestHandlerMapper handlerResolver = Mockito.mock(HttpRequestHandlerMapper.class);

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver);
        final HttpCoreContext context = HttpCoreContext.create();
        final HttpServerConnection conn = Mockito.mock(HttpServerConnection.class);
        final HttpRequest request = new BasicHttpRequest("GET", "/", new HttpVersion(20, 45));
        Mockito.when(conn.receiveRequestHeader()).thenReturn(request);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(responseFactory.newHttpResponse(HttpVersion.HTTP_1_1, 200, context)).thenReturn(response);
        Mockito.when(connReuseStrategy.keepAlive(response, context)).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Mockito.verify(responseFactory).newHttpResponse(HttpVersion.HTTP_1_1, 200, context);
    }

    @Test
    public void testExecutionEntityEnclosingRequest() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final ConnectionReuseStrategy connReuseStrategy = Mockito.mock(ConnectionReuseStrategy.class);
        final HttpResponseFactory responseFactory = Mockito.mock(HttpResponseFactory.class);
        final HttpRequestHandlerMapper handlerResolver = Mockito.mock(HttpRequestHandlerMapper.class);

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver);
        final HttpCoreContext context = HttpCoreContext.create();
        final HttpServerConnection conn = Mockito.mock(HttpServerConnection.class);
        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        final InputStream instream = Mockito.mock(InputStream.class);
        final InputStreamEntity entity = new InputStreamEntity(instream, -1);
        request.setEntity(entity);

        Mockito.when(conn.receiveRequestHeader()).thenReturn(request);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(responseFactory.newHttpResponse(HttpVersion.HTTP_1_1, 200, context)).thenReturn(response);
        Mockito.when(connReuseStrategy.keepAlive(response, context)).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Assert.assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, response.getStatusLine().getStatusCode());

        Assert.assertSame(conn, context.getConnection());
        Assert.assertSame(request, context.getRequest());
        Assert.assertSame(response, context.getResponse());

        Mockito.verify(conn).receiveRequestEntity(request);
        Mockito.verify(httprocessor).process(request, context);
        Mockito.verify(instream).close();
        Mockito.verify(httprocessor).process(response, context);
        Mockito.verify(conn).sendResponseHeader(response);
        Mockito.verify(conn).sendResponseEntity(response);
        Mockito.verify(conn).flush();
        Mockito.verify(conn).close();
    }

    @Test
    public void testExecutionEntityEnclosingRequestWithExpectContinue() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final ConnectionReuseStrategy connReuseStrategy = Mockito.mock(ConnectionReuseStrategy.class);
        final HttpResponseFactory responseFactory = Mockito.mock(HttpResponseFactory.class);
        final HttpRequestHandlerMapper handlerResolver = Mockito.mock(HttpRequestHandlerMapper.class);

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver);
        final HttpCoreContext context = HttpCoreContext.create();
        final HttpServerConnection conn = Mockito.mock(HttpServerConnection.class);
        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);
        final InputStream instream = Mockito.mock(InputStream.class);
        final InputStreamEntity entity = new InputStreamEntity(instream, -1);
        request.setEntity(entity);

        Mockito.when(conn.receiveRequestHeader()).thenReturn(request);
        final HttpResponse resp100 = new BasicHttpResponse(HttpVersion.HTTP_1_1, 100, "Continue");
        Mockito.when(responseFactory.newHttpResponse(HttpVersion.HTTP_1_1, 100, context)).thenReturn(resp100);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(responseFactory.newHttpResponse(HttpVersion.HTTP_1_1, 200, context)).thenReturn(response);
        Mockito.when(connReuseStrategy.keepAlive(response, context)).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Assert.assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, response.getStatusLine().getStatusCode());

        Assert.assertSame(conn, context.getConnection());
        Assert.assertSame(request, context.getRequest());
        Assert.assertSame(response, context.getResponse());

        Mockito.verify(conn).sendResponseHeader(resp100);
        Mockito.verify(conn).receiveRequestEntity(request);
        Mockito.verify(httprocessor).process(request, context);
        Mockito.verify(instream).close();
        Mockito.verify(httprocessor).process(response, context);
        Mockito.verify(conn).sendResponseHeader(response);
        Mockito.verify(conn).sendResponseEntity(response);
        Mockito.verify(conn, Mockito.times(2)).flush();
        Mockito.verify(conn).close();
    }

    @Test
    public void testExecutionEntityEnclosingRequestCustomExpectationVerifier() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final ConnectionReuseStrategy connReuseStrategy = Mockito.mock(ConnectionReuseStrategy.class);
        final HttpResponseFactory responseFactory = Mockito.mock(HttpResponseFactory.class);
        final HttpRequestHandlerMapper handlerResolver = Mockito.mock(HttpRequestHandlerMapper.class);

        final HttpExpectationVerifier expectationVerifier = new HttpExpectationVerifier() {

            @Override
            public void verify(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException {
                response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            }

        };

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver,
                expectationVerifier);
        final HttpCoreContext context = HttpCoreContext.create();
        final HttpServerConnection conn = Mockito.mock(HttpServerConnection.class);
        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);
        final InputStream instream = Mockito.mock(InputStream.class);
        final InputStreamEntity entity = new InputStreamEntity(instream, -1);
        request.setEntity(entity);

        Mockito.when(conn.receiveRequestHeader()).thenReturn(request);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 100, "Continue");
        Mockito.when(responseFactory.newHttpResponse(HttpVersion.HTTP_1_1, 100, context)).thenReturn(response);
        Mockito.when(connReuseStrategy.keepAlive(response, context)).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Assert.assertSame(conn, context.getConnection());
        Assert.assertSame(request, context.getRequest());
        Assert.assertSame(response, context.getResponse());

        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getStatusLine().getStatusCode());

        Mockito.verify(conn).sendResponseHeader(response);
        Mockito.verify(conn, Mockito.never()).receiveRequestEntity(request);
        Mockito.verify(httprocessor).process(response, context);
        Mockito.verify(conn).sendResponseHeader(response);
        Mockito.verify(conn).sendResponseEntity(response);
        Mockito.verify(conn).flush();
        Mockito.verify(conn).close();
    }

    @Test
    public void testExecutionExceptionInCustomExpectationVerifier() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final ConnectionReuseStrategy connReuseStrategy = Mockito.mock(ConnectionReuseStrategy.class);
        final HttpResponseFactory responseFactory = Mockito.mock(HttpResponseFactory.class);
        final HttpExpectationVerifier expectationVerifier = Mockito.mock(HttpExpectationVerifier.class);
        final HttpRequestHandlerMapper handlerResolver = Mockito.mock(HttpRequestHandlerMapper.class);

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver,
                expectationVerifier);
        final HttpCoreContext context = HttpCoreContext.create();
        final HttpServerConnection conn = Mockito.mock(HttpServerConnection.class);
        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/");
        request.addHeader(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);
        final InputStream instream = Mockito.mock(InputStream.class);
        final InputStreamEntity entity = new InputStreamEntity(instream, -1);
        request.setEntity(entity);

        Mockito.when(conn.receiveRequestHeader()).thenReturn(request);
        final HttpResponse resp100 = new BasicHttpResponse(HttpVersion.HTTP_1_1, 100, "Continue");
        Mockito.when(responseFactory.newHttpResponse(HttpVersion.HTTP_1_1, 100, context)).thenReturn(resp100);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_0, 500, "Oppsie");
        Mockito.when(responseFactory.newHttpResponse(HttpVersion.HTTP_1_0, 500, context)).thenReturn(response);
        Mockito.doThrow(new HttpException("Oopsie")).when(expectationVerifier).verify(request, resp100, context);
        Mockito.when(connReuseStrategy.keepAlive(response, context)).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Assert.assertSame(conn, context.getConnection());
        Assert.assertSame(request, context.getRequest());
        Assert.assertSame(response, context.getResponse());

        Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatusLine().getStatusCode());

        Mockito.verify(conn).sendResponseHeader(response);
        Mockito.verify(conn, Mockito.never()).receiveRequestEntity(request);
        Mockito.verify(httprocessor).process(response, context);
        Mockito.verify(conn).sendResponseHeader(response);
        Mockito.verify(conn).sendResponseEntity(response);
        Mockito.verify(conn).flush();
        Mockito.verify(conn).close();
    }

    @Test
    public void testMethodNotSupported() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final ConnectionReuseStrategy connReuseStrategy = Mockito.mock(ConnectionReuseStrategy.class);
        final HttpResponseFactory responseFactory = Mockito.mock(HttpResponseFactory.class);
        final HttpRequestHandlerMapper handlerResolver = Mockito.mock(HttpRequestHandlerMapper.class);
        final HttpRequestHandler requestHandler = Mockito.mock(HttpRequestHandler.class);

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver);
        final HttpCoreContext context = HttpCoreContext.create();
        final HttpServerConnection conn = Mockito.mock(HttpServerConnection.class);
        final HttpRequest request = new BasicHttpRequest("whatever", "/");

        Mockito.when(conn.receiveRequestHeader()).thenReturn(request);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(responseFactory.newHttpResponse(HttpVersion.HTTP_1_1, 200, context)).thenReturn(response);
        final HttpResponse error = new BasicHttpResponse(HttpVersion.HTTP_1_0, 500, "Oppsie");
        Mockito.when(responseFactory.newHttpResponse(HttpVersion.HTTP_1_0, 500, context)).thenReturn(error);
        Mockito.when(handlerResolver.lookup(request)).thenReturn(requestHandler);
        Mockito.doThrow(new MethodNotSupportedException("whatever")).when(
                requestHandler).handle(request, response, context);
        Mockito.when(connReuseStrategy.keepAlive(error, context)).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Assert.assertSame(conn, context.getConnection());
        Assert.assertSame(request, context.getRequest());
        Assert.assertSame(error, context.getResponse());

        Assert.assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, error.getStatusLine().getStatusCode());

        Mockito.verify(conn).sendResponseHeader(error);
        Mockito.verify(httprocessor).process(error, context);
        Mockito.verify(conn).sendResponseHeader(error);
        Mockito.verify(conn).sendResponseEntity(error);
        Mockito.verify(conn).flush();
        Mockito.verify(conn).close();
    }

    @Test
    public void testUnsupportedHttpVersionException() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final ConnectionReuseStrategy connReuseStrategy = Mockito.mock(ConnectionReuseStrategy.class);
        final HttpResponseFactory responseFactory = Mockito.mock(HttpResponseFactory.class);
        final HttpRequestHandlerMapper handlerResolver = Mockito.mock(HttpRequestHandlerMapper.class);
        final HttpRequestHandler requestHandler = Mockito.mock(HttpRequestHandler.class);

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver);
        final HttpCoreContext context = HttpCoreContext.create();
        final HttpServerConnection conn = Mockito.mock(HttpServerConnection.class);
        final HttpRequest request = new BasicHttpRequest("whatever", "/");

        Mockito.when(conn.receiveRequestHeader()).thenReturn(request);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(responseFactory.newHttpResponse(HttpVersion.HTTP_1_1, 200, context)).thenReturn(response);
        final HttpResponse error = new BasicHttpResponse(HttpVersion.HTTP_1_0, 500, "Oppsie");
        Mockito.when(responseFactory.newHttpResponse(HttpVersion.HTTP_1_0, 500, context)).thenReturn(error);
        Mockito.when(handlerResolver.lookup(request)).thenReturn(requestHandler);
        Mockito.doThrow(new UnsupportedHttpVersionException()).when(
                requestHandler).handle(request, response, context);
        Mockito.when(connReuseStrategy.keepAlive(error, context)).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Assert.assertSame(conn, context.getConnection());
        Assert.assertSame(request, context.getRequest());
        Assert.assertSame(error, context.getResponse());

        Assert.assertEquals(HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED, error.getStatusLine().getStatusCode());

        Mockito.verify(conn).sendResponseHeader(error);
        Mockito.verify(httprocessor).process(error, context);
        Mockito.verify(conn).sendResponseHeader(error);
        Mockito.verify(conn).sendResponseEntity(error);
        Mockito.verify(conn).flush();
        Mockito.verify(conn).close();
    }

    @Test
    public void testProtocolException() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final ConnectionReuseStrategy connReuseStrategy = Mockito.mock(ConnectionReuseStrategy.class);
        final HttpResponseFactory responseFactory = Mockito.mock(HttpResponseFactory.class);
        final HttpRequestHandlerMapper handlerResolver = Mockito.mock(HttpRequestHandlerMapper.class);
        final HttpRequestHandler requestHandler = Mockito.mock(HttpRequestHandler.class);

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver);
        final HttpCoreContext context = HttpCoreContext.create();
        final HttpServerConnection conn = Mockito.mock(HttpServerConnection.class);
        final HttpRequest request = new BasicHttpRequest("whatever", "/");

        Mockito.when(conn.receiveRequestHeader()).thenReturn(request);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(responseFactory.newHttpResponse(HttpVersion.HTTP_1_1, 200, context)).thenReturn(response);
        final HttpResponse error = new BasicHttpResponse(HttpVersion.HTTP_1_0, 500, "Oppsie");
        Mockito.when(responseFactory.newHttpResponse(HttpVersion.HTTP_1_0, 500, context)).thenReturn(error);
        Mockito.when(handlerResolver.lookup(request)).thenReturn(requestHandler);
        Mockito.doThrow(new ProtocolException("oh, this world is wrong")).when(
                requestHandler).handle(request, response, context);
        Mockito.when(connReuseStrategy.keepAlive(error, context)).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Assert.assertSame(conn, context.getConnection());
        Assert.assertSame(request, context.getRequest());
        Assert.assertSame(error, context.getResponse());

        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, error.getStatusLine().getStatusCode());

        Mockito.verify(conn).sendResponseHeader(error);
        Mockito.verify(httprocessor).process(error, context);
        Mockito.verify(conn).sendResponseHeader(error);
        Mockito.verify(conn).sendResponseEntity(error);
        Mockito.verify(conn).flush();
        Mockito.verify(conn).close();
    }

    @Test
    public void testConnectionKeepAlive() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final ConnectionReuseStrategy connReuseStrategy = Mockito.mock(ConnectionReuseStrategy.class);
        final HttpResponseFactory responseFactory = Mockito.mock(HttpResponseFactory.class);
        final HttpRequestHandlerMapper handlerResolver = Mockito.mock(HttpRequestHandlerMapper.class);
        final HttpRequestHandler requestHandler = Mockito.mock(HttpRequestHandler.class);

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver);
        final HttpCoreContext context = HttpCoreContext.create();
        final HttpServerConnection conn = Mockito.mock(HttpServerConnection.class);
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        Mockito.when(conn.receiveRequestHeader()).thenReturn(request);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(responseFactory.newHttpResponse(HttpVersion.HTTP_1_1, 200, context)).thenReturn(response);
        Mockito.when(handlerResolver.lookup(request)).thenReturn(requestHandler);
        Mockito.when(connReuseStrategy.keepAlive(response, context)).thenReturn(Boolean.TRUE);

        httpservice.handleRequest(conn, context);

        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

        Assert.assertSame(conn, context.getConnection());
        Assert.assertSame(request, context.getRequest());
        Assert.assertSame(response, context.getResponse());

        Mockito.verify(httprocessor).process(request, context);
        Mockito.verify(httprocessor).process(response, context);
        Mockito.verify(conn).sendResponseHeader(response);
        Mockito.verify(conn).sendResponseEntity(response);
        Mockito.verify(conn).flush();
        Mockito.verify(conn, Mockito.never()).close();
    }

    @Test
    public void testNoContentResponse() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final ConnectionReuseStrategy connReuseStrategy = Mockito.mock(ConnectionReuseStrategy.class);
        final HttpResponseFactory responseFactory = Mockito.mock(HttpResponseFactory.class);
        final HttpRequestHandlerMapper handlerResolver = Mockito.mock(HttpRequestHandlerMapper.class);
        final HttpRequestHandler requestHandler = Mockito.mock(HttpRequestHandler.class);

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver);
        final HttpCoreContext context = HttpCoreContext.create();
        final HttpServerConnection conn = Mockito.mock(HttpServerConnection.class);
        final HttpRequest request = new BasicHttpRequest("GET", "/");

        Mockito.when(conn.receiveRequestHeader()).thenReturn(request);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_NO_CONTENT, "No Content");
        Mockito.when(responseFactory.newHttpResponse(HttpVersion.HTTP_1_1, 200, context)).thenReturn(response);
        Mockito.when(handlerResolver.lookup(request)).thenReturn(requestHandler);
        Mockito.when(connReuseStrategy.keepAlive(response, context)).thenReturn(Boolean.TRUE);

        httpservice.handleRequest(conn, context);

        Assert.assertSame(conn, context.getConnection());
        Assert.assertSame(request, context.getRequest());

        Mockito.verify(httprocessor).process(response, context);
        Mockito.verify(requestHandler).handle(request, response, context);

        Mockito.verify(conn).sendResponseHeader(response);
        Mockito.verify(conn, Mockito.never()).sendResponseEntity(Mockito.<HttpResponse>any());
        Mockito.verify(conn).flush();
        Mockito.verify(conn, Mockito.never()).close();
    }

    @Test
    public void testResponseToHead() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final ConnectionReuseStrategy connReuseStrategy = Mockito.mock(ConnectionReuseStrategy.class);
        final HttpResponseFactory responseFactory = Mockito.mock(HttpResponseFactory.class);
        final HttpRequestHandlerMapper handlerResolver = Mockito.mock(HttpRequestHandlerMapper.class);
        final HttpRequestHandler requestHandler = Mockito.mock(HttpRequestHandler.class);

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver);
        final HttpCoreContext context = HttpCoreContext.create();
        final HttpServerConnection conn = Mockito.mock(HttpServerConnection.class);
        final HttpRequest request = new BasicHttpRequest("HEAD", "/");

        Mockito.when(conn.receiveRequestHeader()).thenReturn(request);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(responseFactory.newHttpResponse(HttpVersion.HTTP_1_1, 200, context)).thenReturn(response);
        Mockito.when(handlerResolver.lookup(request)).thenReturn(requestHandler);
        Mockito.when(connReuseStrategy.keepAlive(response, context)).thenReturn(Boolean.TRUE);

        httpservice.handleRequest(conn, context);

        Assert.assertSame(conn, context.getConnection());
        Assert.assertSame(request, context.getRequest());

        Mockito.verify(httprocessor).process(response, context);
        Mockito.verify(requestHandler).handle(request, response, context);

        Mockito.verify(conn).sendResponseHeader(response);
        Mockito.verify(conn, Mockito.never()).sendResponseEntity(Mockito.<HttpResponse>any());
        Mockito.verify(conn).flush();
        Mockito.verify(conn, Mockito.never()).close();
    }

}

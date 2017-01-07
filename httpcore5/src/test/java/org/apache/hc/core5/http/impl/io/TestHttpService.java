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

package org.apache.hc.core5.http.impl.io;

import java.io.InputStream;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponseFactory;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.MethodNotSupportedException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.UnsupportedHttpVersionException;
import org.apache.hc.core5.http.io.HttpExpectationVerifier;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.HttpRequestHandlerMapper;
import org.apache.hc.core5.http.io.HttpServerConnection;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class TestHttpService {

    @Mock
    private HttpProcessor httprocessor;
    @Mock
    private ConnectionReuseStrategy connReuseStrategy;
    @Mock
    private HttpResponseFactory<ClassicHttpResponse> responseFactory;
    @Mock
    private HttpRequestHandlerMapper handlerResolver;
    @Mock
    private HttpRequestHandler requestHandler;
    @Mock
    private HttpServerConnection conn;

    private HttpService httpservice;

    @Before
    public void settup() {
        MockitoAnnotations.initMocks(this);
        httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver);
    }

    @Test
    public void testInvalidInitialization() throws Exception {
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
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        Mockito.when(conn.receiveRequestHeader()).thenReturn(request);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        Mockito.when(responseFactory.newHttpResponse(200)).thenReturn(response);
        Mockito.when(connReuseStrategy.keepAlive(request, response, context)).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Assert.assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, response.getCode());

        Assert.assertSame(request, context.getRequest());
        Assert.assertSame(response, context.getResponse());

        Mockito.verify(httprocessor).process(request, request.getEntity(), context);
        Mockito.verify(httprocessor).process(response, response.getEntity(), context);
        Mockito.verify(conn).sendResponseHeader(response);
        Mockito.verify(conn).sendResponseEntity(response);
        Mockito.verify(conn).flush();
        Mockito.verify(conn).close();
    }

    @Test
    public void testExecutionEntityEnclosingRequest() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        final InputStream instream = Mockito.mock(InputStream.class);
        final InputStreamEntity entity = new InputStreamEntity(instream, -1);
        request.setEntity(entity);

        Mockito.when(conn.receiveRequestHeader()).thenReturn(request);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        Mockito.when(responseFactory.newHttpResponse(200)).thenReturn(response);
        Mockito.when(connReuseStrategy.keepAlive(request, response, context)).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Assert.assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, response.getCode());

        Assert.assertSame(request, context.getRequest());
        Assert.assertSame(response, context.getResponse());

        Mockito.verify(conn).receiveRequestEntity(request);
        Mockito.verify(httprocessor).process(request, request.getEntity(), context);
        Mockito.verify(instream).close();
        Mockito.verify(httprocessor).process(response, response.getEntity(), context);
        Mockito.verify(conn).sendResponseHeader(response);
        Mockito.verify(conn).sendResponseEntity(response);
        Mockito.verify(conn).flush();
        Mockito.verify(conn).close();
    }

    @Test
    public void testExecutionEntityEnclosingRequestWithExpectContinue() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.addHeader(HttpHeaders.EXPECT, HeaderElements.CONTINUE);
        final InputStream instream = Mockito.mock(InputStream.class);
        final InputStreamEntity entity = new InputStreamEntity(instream, -1);
        request.setEntity(entity);

        Mockito.when(conn.receiveRequestHeader()).thenReturn(request);
        final ClassicHttpResponse resp100 = new BasicClassicHttpResponse(100, "Continue");
        Mockito.when(responseFactory.newHttpResponse(100)).thenReturn(resp100);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        Mockito.when(responseFactory.newHttpResponse(200)).thenReturn(response);
        Mockito.when(connReuseStrategy.keepAlive(request, response, context)).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Assert.assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, response.getCode());

        Assert.assertSame(request, context.getRequest());
        Assert.assertSame(response, context.getResponse());

        Mockito.verify(conn).sendResponseHeader(resp100);
        Mockito.verify(conn).receiveRequestEntity(request);
        Mockito.verify(httprocessor).process(request, request.getEntity(), context);
        Mockito.verify(instream).close();
        Mockito.verify(httprocessor).process(response, response.getEntity(), context);
        Mockito.verify(conn).sendResponseHeader(response);
        Mockito.verify(conn).sendResponseEntity(response);
        Mockito.verify(conn, Mockito.times(2)).flush();
        Mockito.verify(conn).close();
    }

    @Test
    public void testExecutionEntityEnclosingRequestCustomExpectationVerifier() throws Exception {
        final HttpExpectationVerifier expectationVerifier = new HttpExpectationVerifier() {

            @Override
            public void verify(
                    final ClassicHttpRequest request,
                    final ClassicHttpResponse response,
                    final HttpContext context) throws HttpException {
                response.setCode(HttpStatus.SC_UNAUTHORIZED);
            }

        };

        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver,
                expectationVerifier,
                null);
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.addHeader(HttpHeaders.EXPECT, HeaderElements.CONTINUE);
        final InputStream instream = Mockito.mock(InputStream.class);
        final InputStreamEntity entity = new InputStreamEntity(instream, -1);
        request.setEntity(entity);

        Mockito.when(conn.receiveRequestHeader()).thenReturn(request);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(100, "Continue");
        Mockito.when(responseFactory.newHttpResponse(100)).thenReturn(response);
        Mockito.when(connReuseStrategy.keepAlive(request, response, context)).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Assert.assertSame(request, context.getRequest());
        Assert.assertSame(response, context.getResponse());

        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());

        Mockito.verify(conn).sendResponseHeader(response);
        Mockito.verify(httprocessor).process(response, response.getEntity(), context);
        Mockito.verify(conn).sendResponseHeader(response);
        Mockito.verify(conn).sendResponseEntity(response);
        Mockito.verify(conn).flush();
        Mockito.verify(conn).receiveRequestEntity(request);
    }

    @Test
    public void testExecutionExceptionInCustomExpectationVerifier() throws Exception {
        final HttpExpectationVerifier expectationVerifier = Mockito.mock(HttpExpectationVerifier.class);
        final HttpService httpservice = new HttpService(
                httprocessor,
                connReuseStrategy,
                responseFactory,
                handlerResolver,
                expectationVerifier,
                null);
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.addHeader(HttpHeaders.EXPECT, "100-continue");
        final InputStream instream = Mockito.mock(InputStream.class);
        final InputStreamEntity entity = new InputStreamEntity(instream, -1);
        request.setEntity(entity);

        Mockito.when(conn.receiveRequestHeader()).thenReturn(request);
        final ClassicHttpResponse resp100 = new BasicClassicHttpResponse(100, "Continue");
        Mockito.when(responseFactory.newHttpResponse(100)).thenReturn(resp100);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(500, "Oppsie");
        Mockito.when(responseFactory.newHttpResponse(500)).thenReturn(response);
        Mockito.doThrow(new HttpException("Oopsie")).when(expectationVerifier).verify(request, resp100, context);
        Mockito.when(connReuseStrategy.keepAlive(request, response, context)).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Assert.assertSame(request, context.getRequest());
        Assert.assertSame(response, context.getResponse());

        Assert.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getCode());

        Mockito.verify(conn).sendResponseHeader(response);
        Mockito.verify(httprocessor).process(response, response.getEntity(), context);
        Mockito.verify(conn).sendResponseHeader(response);
        Mockito.verify(conn).sendResponseEntity(response);
        Mockito.verify(conn).flush();
        Mockito.verify(conn).receiveRequestEntity(request);
    }

    @Test
    public void testMethodNotSupported() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("whatever", "/");

        Mockito.when(conn.receiveRequestHeader()).thenReturn(request);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        Mockito.when(responseFactory.newHttpResponse(200)).thenReturn(response);
        final ClassicHttpResponse error = new BasicClassicHttpResponse(500, "Oppsie");
        Mockito.when(responseFactory.newHttpResponse(500)).thenReturn(error);
        Mockito.when(handlerResolver.lookup(request, context)).thenReturn(requestHandler);
        Mockito.doThrow(new MethodNotSupportedException("whatever")).when(
                requestHandler).handle(request, response, context);
        Mockito.when(connReuseStrategy.keepAlive(request, error, context)).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Assert.assertSame(request, context.getRequest());
        Assert.assertSame(error, context.getResponse());

        Assert.assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, error.getCode());

        Mockito.verify(conn).sendResponseHeader(error);
        Mockito.verify(httprocessor).process(error, error.getEntity(), context);
        Mockito.verify(conn).sendResponseHeader(error);
        Mockito.verify(conn).sendResponseEntity(error);
        Mockito.verify(conn).flush();
        Mockito.verify(conn).close();
    }

    @Test
    public void testUnsupportedHttpVersionException() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("whatever", "/");

        Mockito.when(conn.receiveRequestHeader()).thenReturn(request);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        Mockito.when(responseFactory.newHttpResponse(200)).thenReturn(response);
        final ClassicHttpResponse error = new BasicClassicHttpResponse(500, "Oppsie");
        Mockito.when(responseFactory.newHttpResponse(500)).thenReturn(error);
        Mockito.when(handlerResolver.lookup(request, context)).thenReturn(requestHandler);
        Mockito.doThrow(new UnsupportedHttpVersionException()).when(
                requestHandler).handle(request, response, context);
        Mockito.when(connReuseStrategy.keepAlive(request, error, context)).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Assert.assertSame(request, context.getRequest());
        Assert.assertSame(error, context.getResponse());

        Assert.assertEquals(HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED, error.getCode());

        Mockito.verify(conn).sendResponseHeader(error);
        Mockito.verify(httprocessor).process(error, error.getEntity(), context);
        Mockito.verify(conn).sendResponseHeader(error);
        Mockito.verify(conn).sendResponseEntity(error);
        Mockito.verify(conn).flush();
        Mockito.verify(conn).close();
    }

    @Test
    public void testProtocolException() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("whatever", "/");

        Mockito.when(conn.receiveRequestHeader()).thenReturn(request);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        Mockito.when(responseFactory.newHttpResponse(200)).thenReturn(response);
        final ClassicHttpResponse error = new BasicClassicHttpResponse(500, "Oppsie");
        Mockito.when(responseFactory.newHttpResponse(500)).thenReturn(error);
        Mockito.when(handlerResolver.lookup(request, context)).thenReturn(requestHandler);
        Mockito.doThrow(new ProtocolException("oh, this world is wrong")).when(
                requestHandler).handle(request, response, context);
        Mockito.when(connReuseStrategy.keepAlive(request, error, context)).thenReturn(Boolean.FALSE);

        httpservice.handleRequest(conn, context);

        Assert.assertSame(request, context.getRequest());
        Assert.assertSame(error, context.getResponse());

        Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, error.getCode());

        Mockito.verify(conn).sendResponseHeader(error);
        Mockito.verify(httprocessor).process(error, error.getEntity(), context);
        Mockito.verify(conn).sendResponseHeader(error);
        Mockito.verify(conn).sendResponseEntity(error);
        Mockito.verify(conn).flush();
        Mockito.verify(conn).close();
    }

    @Test
    public void testConnectionKeepAlive() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        Mockito.when(conn.receiveRequestHeader()).thenReturn(request);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        Mockito.when(responseFactory.newHttpResponse(200)).thenReturn(response);
        Mockito.when(handlerResolver.lookup(request, context)).thenReturn(requestHandler);
        Mockito.when(connReuseStrategy.keepAlive(request, response, context)).thenReturn(Boolean.TRUE);

        httpservice.handleRequest(conn, context);

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());

        Assert.assertSame(request, context.getRequest());
        Assert.assertSame(response, context.getResponse());

        Mockito.verify(httprocessor).process(request, request.getEntity(), context);
        Mockito.verify(httprocessor).process(response, response.getEntity(), context);
        Mockito.verify(conn).sendResponseHeader(response);
        Mockito.verify(conn).sendResponseEntity(response);
        Mockito.verify(conn).flush();
        Mockito.verify(conn, Mockito.never()).close();
    }

    @Test
    public void testNoContentResponse() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");

        Mockito.when(conn.receiveRequestHeader()).thenReturn(request);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_NO_CONTENT, "No Content");
        Mockito.when(responseFactory.newHttpResponse(200)).thenReturn(response);
        Mockito.when(handlerResolver.lookup(request, context)).thenReturn(requestHandler);
        Mockito.when(connReuseStrategy.keepAlive(request, response, context)).thenReturn(Boolean.TRUE);

        httpservice.handleRequest(conn, context);

        Assert.assertSame(request, context.getRequest());

        Mockito.verify(httprocessor).process(response, response.getEntity(), context);
        Mockito.verify(requestHandler).handle(request, response, context);

        Mockito.verify(conn).sendResponseHeader(response);
        Mockito.verify(conn, Mockito.never()).sendResponseEntity(Mockito.<ClassicHttpResponse>any());
        Mockito.verify(conn).flush();
        Mockito.verify(conn, Mockito.never()).close();
    }

    @Test
    public void testResponseToHead() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("HEAD", "/");

        Mockito.when(conn.receiveRequestHeader()).thenReturn(request);
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        Mockito.when(responseFactory.newHttpResponse(200)).thenReturn(response);
        Mockito.when(handlerResolver.lookup(request, context)).thenReturn(requestHandler);
        Mockito.when(connReuseStrategy.keepAlive(request, response, context)).thenReturn(Boolean.TRUE);

        httpservice.handleRequest(conn, context);

        Assert.assertSame(request, context.getRequest());

        Mockito.verify(httprocessor).process(response, response.getEntity(), context);
        Mockito.verify(requestHandler).handle(request, response, context);

        Mockito.verify(conn).sendResponseHeader(response);
        Mockito.verify(conn, Mockito.never()).sendResponseEntity(Mockito.<ClassicHttpResponse>any());
        Mockito.verify(conn).flush();
        Mockito.verify(conn, Mockito.never()).close();
    }

}

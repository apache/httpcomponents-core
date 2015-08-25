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

package org.apache.http.osgi;

import org.apache.http.ConnectionClosedException;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpServerConnection;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.NoHttpResponseException;
import org.apache.http.ProtocolException;
import org.apache.http.UnsupportedHttpVersionException;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestHandlerMapper;
import org.apache.http.protocol.HttpService;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Try basic classes
 */
public class BasicIT extends Common {
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
        Mockito.when(connReuseStrategy.keepAlive(request, response, context)).thenReturn(Boolean.FALSE);

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
    public void testConstructor() {
        final Throwable cause = new Exception();
        new HttpException();
        new HttpException("Oppsie");
        new HttpException("Oppsie", cause);
        new ProtocolException();
        new ProtocolException("Oppsie");
        new ProtocolException("Oppsie", cause);
        new NoHttpResponseException("Oppsie");
        new ConnectionClosedException("Oppsie");
        new MethodNotSupportedException("Oppsie");
        new MethodNotSupportedException("Oppsie", cause);
        new UnsupportedHttpVersionException();
        new UnsupportedHttpVersionException("Oppsie");
    }

}

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

import java.io.IOException;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TestHttpRequestExecutor {

    @Test
    public void testInvalidInput() throws Exception {
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        try {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.execute(null, conn, context);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.execute(request, null, context);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.execute(request, conn, null);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }

        try {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.preProcess(null, httprocessor, context);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.preProcess(request, null, context);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.preProcess(request, httprocessor, null);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }

        try {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.postProcess(null, httprocessor, context);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.postProcess(response, null, context);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.postProcess(response, httprocessor, null);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
    }

    @Test
    public void testBasicExecution() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");

        executor.preProcess(request, httprocessor, context);
        Mockito.verify(httprocessor).process(request, request.getEntity(), context);

        Mockito.when(conn.receiveResponseHeader()).thenReturn(
                new BasicClassicHttpResponse(200, "OK"));

        final ClassicHttpResponse response = executor.execute(request, conn, context);
        Mockito.verify(conn).sendRequestHeader(request);
        Mockito.verify(conn).flush();
        Mockito.verify(conn).receiveResponseHeader();
        Mockito.verify(conn).receiveResponseEntity(response);

        executor.postProcess(response, httprocessor, context);
        Mockito.verify(httprocessor).process(response, response.getEntity(), context);

        Assert.assertSame(request, context.getRequest());
        Assert.assertSame(response, context.getResponse());
    }

    @Test
    public void testExecutionSkipIntermediateResponses() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");

        executor.preProcess(request, httprocessor, context);
        Mockito.verify(httprocessor).process(request, request.getEntity(), context);

        Mockito.when(conn.receiveResponseHeader()).thenReturn(
                new BasicClassicHttpResponse(100, "OK"),
                new BasicClassicHttpResponse(101, "OK"),
                new BasicClassicHttpResponse(102, "OK"),
                new BasicClassicHttpResponse(200, "OK"));

        final ClassicHttpResponse response = executor.execute(request, conn, context);
        Mockito.verify(conn).sendRequestHeader(request);
        Mockito.verify(conn).flush();
        Mockito.verify(conn, Mockito.times(4)).receiveResponseHeader();
        Mockito.verify(conn, Mockito.times(1)).receiveResponseEntity(response);

        executor.postProcess(response, httprocessor, context);
        Mockito.verify(httprocessor).process(response, response.getEntity(), context);

        Assert.assertSame(request, context.getRequest());
        Assert.assertSame(response, context.getResponse());
    }

    @Test
    public void testExecutionNoResponseBody() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");

        executor.preProcess(request, httprocessor, context);
        Mockito.verify(httprocessor).process(request, request.getEntity(), context);

        Mockito.when(conn.receiveResponseHeader()).thenReturn(
                new BasicClassicHttpResponse(204, "OK"));

        final ClassicHttpResponse response = executor.execute(request, conn, context);
        Mockito.verify(conn).sendRequestHeader(request);
        Mockito.verify(conn).flush();
        Mockito.verify(conn).receiveResponseHeader();
        Mockito.verify(conn, Mockito.never()).receiveResponseEntity(response);

        executor.postProcess(response, httprocessor, context);
        Mockito.verify(httprocessor).process(response, response.getEntity(), context);
    }

    @Test
    public void testExecutionHead() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("HEAD", "/");

        executor.preProcess(request, httprocessor, context);
        Mockito.verify(httprocessor).process(request, request.getEntity(), context);

        Mockito.when(conn.receiveResponseHeader()).thenReturn(
                new BasicClassicHttpResponse(200, "OK"));

        final ClassicHttpResponse response = executor.execute(request, conn, context);
        Mockito.verify(conn).sendRequestHeader(request);
        Mockito.verify(conn).flush();
        Mockito.verify(conn).receiveResponseHeader();
        Mockito.verify(conn, Mockito.never()).receiveResponseEntity(response);

        executor.postProcess(response, httprocessor, context);
        Mockito.verify(httprocessor).process(response, response.getEntity(), context);
    }

    @Test
    public void testExecutionEntityEnclosingRequest() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        final HttpEntity entity = Mockito.mock(HttpEntity.class);
        request.setEntity(entity);

        executor.preProcess(request, httprocessor, context);
        Mockito.verify(httprocessor).process(request, request.getEntity(), context);

        Mockito.when(conn.receiveResponseHeader()).thenReturn(
                new BasicClassicHttpResponse(200, "OK"));

        final ClassicHttpResponse response = executor.execute(request, conn, context);
        Mockito.verify(conn).sendRequestHeader(request);
        Mockito.verify(conn).sendRequestEntity(request);
        Mockito.verify(conn).flush();
        Mockito.verify(conn).receiveResponseHeader();
        Mockito.verify(conn).receiveResponseEntity(response);

        executor.postProcess(response, httprocessor, context);
        Mockito.verify(httprocessor).process(response, response.getEntity(), context);
    }

    @Test
    public void testExecutionEntityEnclosingRequestWithExpectContinueSuccess() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.addHeader(HttpHeaders.EXPECT, HeaderElements.CONTINUE);
        final HttpEntity entity = Mockito.mock(HttpEntity.class);
        request.setEntity(entity);

        executor.preProcess(request, httprocessor, context);
        Mockito.verify(httprocessor).process(request, request.getEntity(), context);

        Mockito.when(conn.receiveResponseHeader()).thenReturn(
                new BasicClassicHttpResponse(100, "Continue"),
                new BasicClassicHttpResponse(200, "OK"));
        Mockito.when(conn.isDataAvailable(Mockito.anyInt())).thenReturn(Boolean.TRUE);

        final ClassicHttpResponse response = executor.execute(request, conn, context);
        Mockito.verify(conn).sendRequestHeader(request);
        Mockito.verify(conn).sendRequestEntity(request);
        Mockito.verify(conn, Mockito.times(2)).flush();
        Mockito.verify(conn).isDataAvailable(3000);
        Mockito.verify(conn, Mockito.times(2)).receiveResponseHeader();
        Mockito.verify(conn).receiveResponseEntity(response);

        executor.postProcess(response, httprocessor, context);
        Mockito.verify(httprocessor).process(response, response.getEntity(), context);
    }

    @Test
    public void testExecutionEntityEnclosingRequestWithExpectContinueFailure() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.addHeader(HttpHeaders.EXPECT, HeaderElements.CONTINUE);
        final HttpEntity entity = Mockito.mock(HttpEntity.class);
        request.setEntity(entity);

        executor.preProcess(request, httprocessor, context);
        Mockito.verify(httprocessor).process(request, request.getEntity(), context);

        Mockito.when(conn.receiveResponseHeader()).thenReturn(
                new BasicClassicHttpResponse(402, "OK"));
        Mockito.when(conn.isDataAvailable(Mockito.anyInt())).thenReturn(Boolean.TRUE);

        final ClassicHttpResponse response = executor.execute(request, conn, context);
        Mockito.verify(conn).sendRequestHeader(request);
        Mockito.verify(conn, Mockito.never()).sendRequestEntity(request);
        Mockito.verify(conn).terminateRequest(request);
        Mockito.verify(conn, Mockito.times(2)).flush();
        Mockito.verify(conn).isDataAvailable(3000);
        Mockito.verify(conn).receiveResponseHeader();
        Mockito.verify(conn).receiveResponseEntity(response);

        executor.postProcess(response, httprocessor, context);
        Mockito.verify(httprocessor).process(response, response.getEntity(), context);
    }

    @Test
    public void testExecutionEntityEnclosingRequestUnsupportedIntermediateResponse() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.addHeader(HttpHeaders.EXPECT, HeaderElements.CONTINUE);
        final HttpEntity entity = Mockito.mock(HttpEntity.class);
        request.setEntity(entity);

        executor.preProcess(request, httprocessor, context);
        Mockito.verify(httprocessor).process(request, request.getEntity(), context);

        Mockito.when(conn.receiveResponseHeader()).thenReturn(
                new BasicClassicHttpResponse(101, "OK"));
        Mockito.when(conn.isDataAvailable(Mockito.anyInt())).thenReturn(Boolean.TRUE);

        try {
            executor.execute(request, conn, context);
            Assert.fail("ProtocolException should have been thrown");
        } catch (final ProtocolException ex) {
            Mockito.verify(conn).close();
        }
    }

    @Test
    public void testExecutionEntityEnclosingRequestWithExpectContinueNoResponse() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/");
        request.addHeader(HttpHeaders.EXPECT, HeaderElements.CONTINUE);
        final HttpEntity entity = Mockito.mock(HttpEntity.class);
        request.setEntity(entity);

        executor.preProcess(request, httprocessor, context);
        Mockito.verify(httprocessor).process(request, request.getEntity(), context);

        Mockito.when(conn.receiveResponseHeader()).thenReturn(
                new BasicClassicHttpResponse(200, "OK"));
        Mockito.when(conn.isDataAvailable(Mockito.anyInt())).thenReturn(Boolean.FALSE);

        final ClassicHttpResponse response = executor.execute(request, conn, context);
        Mockito.verify(conn).sendRequestHeader(request);
        Mockito.verify(conn).sendRequestEntity(request);
        Mockito.verify(conn, Mockito.times(2)).flush();
        Mockito.verify(conn).isDataAvailable(3000);
        Mockito.verify(conn).receiveResponseHeader();
        Mockito.verify(conn).receiveResponseEntity(response);

        executor.postProcess(response, httprocessor, context);
        Mockito.verify(httprocessor).process(response, response.getEntity(), context);
    }

    @Test
    public void testExecutionIOException() throws Exception {
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");

        Mockito.doThrow(new IOException("Oopsie")).when(conn).sendRequestHeader(request);
        try {
            executor.execute(request, conn, context);
            Assert.fail("IOException should have been thrown");
        } catch (final IOException ex) {
            Mockito.verify(conn).close();
        }
    }

    @Test
    public void testExecutionRuntimeException() throws Exception {
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");

        Mockito.doThrow(new RuntimeException("Oopsie")).when(conn).receiveResponseHeader();
        try {
            executor.execute(request, conn, context);
            Assert.fail("IOException should have been thrown");
        } catch (final RuntimeException ex) {
            Mockito.verify(conn).close();
        }
    }

}

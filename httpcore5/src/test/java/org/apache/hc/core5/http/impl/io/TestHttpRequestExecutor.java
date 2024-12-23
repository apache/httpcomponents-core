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

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.HttpResponseInformationCallback;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;

class TestHttpRequestExecutor {

    @Test
    void testInvalidInput() {
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        Assertions.assertThrows(NullPointerException.class, () -> {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.execute(null, conn, context);
        });
        Assertions.assertThrows(NullPointerException.class, () -> {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.execute(request, null, context);
        });
        Assertions.assertThrows(NullPointerException.class, () -> {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.execute(request, conn, null);
        });

        Assertions.assertThrows(NullPointerException.class, () -> {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.preProcess(null, httprocessor, context);
        });
        Assertions.assertThrows(NullPointerException.class, () -> {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.preProcess(request, null, context);
        });
        Assertions.assertThrows(NullPointerException.class, () -> {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.preProcess(request, httprocessor, null);
        });
        Assertions.assertThrows(NullPointerException.class, () -> {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.postProcess(null, httprocessor, context);
        });
        Assertions.assertThrows(NullPointerException.class, () -> {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.postProcess(response, null, context);
        });
        Assertions.assertThrows(NullPointerException.class, () -> {
            final HttpRequestExecutor executor = new HttpRequestExecutor();
            executor.postProcess(response, httprocessor, null);
        });
    }

    @Test
    void testBasicExecution() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");

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

        Assertions.assertSame(request, context.getRequest());
        Assertions.assertSame(response, context.getResponse());
    }

    @Test
    void testExecutionSkipIntermediateResponses() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");

        executor.preProcess(request, httprocessor, context);
        Mockito.verify(httprocessor).process(request, request.getEntity(), context);

        Mockito.when(conn.receiveResponseHeader()).thenReturn(
                new BasicClassicHttpResponse(100, "Continue"),
                new BasicClassicHttpResponse(110, "Huh?"),
                new BasicClassicHttpResponse(111, "Huh?"),
                new BasicClassicHttpResponse(200, "OK"));

        final HttpResponseInformationCallback callback = Mockito.mock(HttpResponseInformationCallback.class);

        final ClassicHttpResponse response = executor.execute(request, conn, callback, context);
        Mockito.verify(conn).sendRequestHeader(request);
        Mockito.verify(conn).flush();
        Mockito.verify(conn, Mockito.times(4)).receiveResponseHeader();
        Mockito.verify(conn, Mockito.times(1)).receiveResponseEntity(response);

        final ArgumentCaptor<HttpResponse> responseCaptor = ArgumentCaptor.forClass(HttpResponse.class);
        Mockito.verify(callback, Mockito.times(2)).execute(responseCaptor.capture(), ArgumentMatchers.eq(conn), ArgumentMatchers.eq(context));
        final List<HttpResponse> infos = responseCaptor.getAllValues();
        Assertions.assertNotNull(infos);
        Assertions.assertEquals(2, infos.size());
        final HttpResponse info1 = infos.get(0);
        Assertions.assertNotNull(info1);
        Assertions.assertEquals(110, info1.getCode());
        final HttpResponse info2 = infos.get(1);
        Assertions.assertNotNull(info2);
        Assertions.assertEquals(111, info2.getCode());

        executor.postProcess(response, httprocessor, context);
        Mockito.verify(httprocessor).process(response, response.getEntity(), context);

        Assertions.assertSame(request, context.getRequest());
        Assertions.assertSame(response, context.getResponse());
    }

    @Test
    void testExecutionNoResponseBody() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");

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
    void testExecutionHead() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.HEAD, "/");

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
    void testExecutionEntityEnclosingRequest() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
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
    void testExecutionEntityEnclosingRequestWithExpectContinueSuccess() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.addHeader(HttpHeaders.EXPECT, HeaderElements.CONTINUE);
        final HttpEntity entity = Mockito.mock(HttpEntity.class);
        request.setEntity(entity);

        executor.preProcess(request, httprocessor, context);
        Mockito.verify(httprocessor).process(request, request.getEntity(), context);

        Mockito.when(conn.receiveResponseHeader()).thenReturn(
                new BasicClassicHttpResponse(100, "Continue"),
                new BasicClassicHttpResponse(200, "OK"));
        Mockito.when(conn.isDataAvailable(ArgumentMatchers.any(Timeout.class))).thenReturn(Boolean.TRUE);

        final ClassicHttpResponse response = executor.execute(request, conn, context);
        Mockito.verify(conn).sendRequestHeader(request);
        Mockito.verify(conn).sendRequestEntity(request);
        Mockito.verify(conn, Mockito.times(2)).flush();
        Mockito.verify(conn).isDataAvailable(Timeout.ofMilliseconds(3000));
        Mockito.verify(conn, Mockito.times(2)).receiveResponseHeader();
        Mockito.verify(conn).receiveResponseEntity(response);

        executor.postProcess(response, httprocessor, context);
        Mockito.verify(httprocessor).process(response, response.getEntity(), context);
    }

    @Test
    void testExecutionEntityEnclosingRequestWithExpectContinueFailure() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.addHeader(HttpHeaders.EXPECT, HeaderElements.CONTINUE);
        final HttpEntity entity = Mockito.mock(HttpEntity.class);
        request.setEntity(entity);

        executor.preProcess(request, httprocessor, context);
        Mockito.verify(httprocessor).process(request, request.getEntity(), context);

        Mockito.when(conn.receiveResponseHeader()).thenReturn(
                new BasicClassicHttpResponse(402, "OK"));
        Mockito.when(conn.isDataAvailable(ArgumentMatchers.any(Timeout.class))).thenReturn(Boolean.TRUE);

        final ClassicHttpResponse response = executor.execute(request, conn, context);
        Mockito.verify(conn).sendRequestHeader(request);
        Mockito.verify(conn, Mockito.never()).sendRequestEntity(request);
        Mockito.verify(conn).terminateRequest(request);
        Mockito.verify(conn, Mockito.times(2)).flush();
        Mockito.verify(conn).isDataAvailable(Timeout.ofMilliseconds(3000));
        Mockito.verify(conn).receiveResponseHeader();
        Mockito.verify(conn).receiveResponseEntity(response);

        executor.postProcess(response, httprocessor, context);
        Mockito.verify(httprocessor).process(response, response.getEntity(), context);
    }

    @Test
    void testExecutionEntityEnclosingRequestWithExpectContinueMultiple1xxResponses() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.addHeader(HttpHeaders.EXPECT, HeaderElements.CONTINUE);
        final HttpEntity entity = Mockito.mock(HttpEntity.class);
        request.setEntity(entity);

        executor.preProcess(request, httprocessor, context);
        Mockito.verify(httprocessor).process(request, request.getEntity(), context);

        Mockito.when(conn.receiveResponseHeader()).thenReturn(
                new BasicClassicHttpResponse(110, "Huh?"),
                new BasicClassicHttpResponse(100, "Continue"),
                new BasicClassicHttpResponse(111, "Huh?"),
                new BasicClassicHttpResponse(200, "OK"));
        Mockito.when(conn.isDataAvailable(ArgumentMatchers.any(Timeout.class))).thenReturn(Boolean.TRUE);

        final HttpResponseInformationCallback callback = Mockito.mock(HttpResponseInformationCallback.class);

        final ClassicHttpResponse response = executor.execute(request, conn, callback, context);
        Mockito.verify(conn).sendRequestHeader(request);
        Mockito.verify(conn).sendRequestEntity(request);
        Mockito.verify(conn, Mockito.times(2)).flush();
        Mockito.verify(conn, Mockito.times(2)).isDataAvailable(Timeout.ofMilliseconds(3000));
        Mockito.verify(conn, Mockito.times(4)).receiveResponseHeader();
        Mockito.verify(conn).receiveResponseEntity(response);

        final ArgumentCaptor<HttpResponse> responseCaptor = ArgumentCaptor.forClass(HttpResponse.class);
        Mockito.verify(callback, Mockito.times(2)).execute(responseCaptor.capture(), ArgumentMatchers.eq(conn), ArgumentMatchers.eq(context));
        final List<HttpResponse> infos = responseCaptor.getAllValues();
        Assertions.assertNotNull(infos);
        Assertions.assertEquals(2, infos.size());
        final HttpResponse info1 = infos.get(0);
        Assertions.assertNotNull(info1);
        Assertions.assertEquals(110, info1.getCode());
        final HttpResponse info2 = infos.get(1);
        Assertions.assertNotNull(info2);
        Assertions.assertEquals(111, info2.getCode());

        executor.postProcess(response, httprocessor, context);
        Mockito.verify(httprocessor).process(response, response.getEntity(), context);
    }

    @Test
    void testExecutionEntityEnclosingRequestWithExpectContinueNoResponse() throws Exception {
        final HttpProcessor httprocessor = Mockito.mock(HttpProcessor.class);
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/");
        request.addHeader(HttpHeaders.EXPECT, HeaderElements.CONTINUE);
        final HttpEntity entity = Mockito.mock(HttpEntity.class);
        request.setEntity(entity);

        executor.preProcess(request, httprocessor, context);
        Mockito.verify(httprocessor).process(request, request.getEntity(), context);

        Mockito.when(conn.receiveResponseHeader()).thenReturn(
                new BasicClassicHttpResponse(200, "OK"));
        Mockito.when(conn.isDataAvailable(ArgumentMatchers.any(Timeout.class))).thenReturn(Boolean.FALSE);

        final ClassicHttpResponse response = executor.execute(request, conn, context);
        Mockito.verify(conn).sendRequestHeader(request);
        Mockito.verify(conn).sendRequestEntity(request);
        Mockito.verify(conn, Mockito.times(2)).flush();
        Mockito.verify(conn).isDataAvailable(Timeout.ofMilliseconds(3000));
        Mockito.verify(conn).receiveResponseHeader();
        Mockito.verify(conn).receiveResponseEntity(response);

        executor.postProcess(response, httprocessor, context);
        Mockito.verify(httprocessor).process(response, response.getEntity(), context);
    }

    @Test
    void testExecutionIOException() throws Exception {
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");

        Mockito.doThrow(new IOException("Oopsie")).when(conn).sendRequestHeader(request);
        Assertions.assertThrows(IOException.class, () -> executor.execute(request, conn, context));
        Mockito.verify(conn).close(CloseMode.IMMEDIATE);
    }

    @Test
    void testExecutionRuntimeException() throws Exception {
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");

        Mockito.doThrow(new RuntimeException("Oopsie")).when(conn).receiveResponseHeader();
        Assertions.assertThrows(RuntimeException.class, () -> executor.execute(request, conn, context));
        Mockito.verify(conn).close(CloseMode.IMMEDIATE);
    }

    @Test
    void testExecutionHttpException() throws Exception {
        final HttpClientConnection conn = Mockito.mock(HttpClientConnection.class);
        final HttpRequestExecutor executor = new HttpRequestExecutor();

        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/");

        Mockito.doThrow(new HttpException("Oopsie")).when(conn).receiveResponseHeader();
        Assertions.assertThrows(HttpException.class, () -> executor.execute(request, conn, context));
        Mockito.verify(conn).close();
    }

}

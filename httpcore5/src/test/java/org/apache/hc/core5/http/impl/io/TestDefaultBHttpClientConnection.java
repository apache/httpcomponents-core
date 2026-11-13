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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.LengthRequiredException;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.NotImplementedException;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.DefaultContentLengthStrategy;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestDefaultBHttpClientConnection {

    @Mock
    Socket socket;

    DefaultBHttpClientConnection conn;

    @BeforeEach
    void prepareMocks() {
        conn = new DefaultBHttpClientConnection(Http1Config.DEFAULT,
                null, null,
                DefaultContentLengthStrategy.INSTANCE,
                DefaultContentLengthStrategy.INSTANCE,
                DefaultHttpRequestWriterFactory.INSTANCE,
                DefaultHttpResponseParserFactory.INSTANCE);
    }

    @Test
    void testBasics() {
        Assertions.assertThat(conn.isOpen()).isFalse();
        Assertions.assertThat(conn.toString()).isEqualTo("[Not bound]");
    }

    @Test
    void testReadResponseHead() throws Exception {
        final String s = "HTTP/1.1 200 OK\r\nUser-Agent: test\r\n\r\n";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(0);

        final ClassicHttpResponse response = conn.receiveResponseHeader();
        Assertions.assertThat(response).isNotNull();
        Assertions.assertThat(response.getCode()).isEqualTo(200);
        Assertions.assertThat(response.containsHeader("User-Agent")).isTrue();
        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(1);
    }

    @Test
    void testReadResponseEntityWithoutContentLength() throws Exception {
        final String s = "HTTP/1.1 200 OK\r\nServer: test\r\n\r\n123";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(0);

        final ClassicHttpResponse response = conn.receiveResponseHeader();

        Assertions.assertThat(response).isNotNull();
        Assertions.assertThat(response.getCode()).isEqualTo(200);
        Assertions.assertThat(response.containsHeader("Server")).isTrue();
        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(1);

        conn.receiveResponseEntity(response);

        final HttpEntity entity = response.getEntity();
        Assertions.assertThat(entity).isNotNull();
        Assertions.assertThat(entity.getContentLength()).isEqualTo(-1);
        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(1);

        final InputStream content = entity.getContent();
        Assertions.assertThat(content).isNotNull();
        Assertions.assertThat(content.available()).isEqualTo(3);
        Assertions.assertThat(content.read()).isEqualTo((int) '1');
        Assertions.assertThat(content.read()).isEqualTo((int) '2');
        Assertions.assertThat(content.read()).isEqualTo((int) '3');
    }

    @Test
    void testReadResponseEntityWithContentLength() throws Exception {
        final String s = "HTTP/1.1 200 OK\r\nServer: test\r\nContent-Length: 3\r\n\r\n123";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(0);

        final ClassicHttpResponse response = conn.receiveResponseHeader();

        Assertions.assertThat(response).isNotNull();
        Assertions.assertThat(response.getCode()).isEqualTo(200);
        Assertions.assertThat(response.containsHeader("Server")).isTrue();
        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(1);

        conn.receiveResponseEntity(response);

        final HttpEntity entity = response.getEntity();
        Assertions.assertThat(entity).isNotNull();
        Assertions.assertThat(entity.getContentLength()).isEqualTo(3);
        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(1);
        final InputStream content = entity.getContent();
        Assertions.assertThat(content).isNotNull();
        Assertions.assertThat(content).isInstanceOf(ContentLengthInputStream.class);
    }

    @Test
    void testReadResponseEntityChunkCoded() throws Exception {
        final String s = "HTTP/1.1 200 OK\r\nServer: test\r\nTransfer-Encoding: " +
                "chunked\r\n\r\n3\r\n123\r\n0\r\n\r\n";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(0);

        final ClassicHttpResponse response = conn.receiveResponseHeader();

        Assertions.assertThat(response).isNotNull();
        Assertions.assertThat(response.getCode()).isEqualTo(200);
        Assertions.assertThat(response.containsHeader("Server")).isTrue();
        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(1);

        conn.receiveResponseEntity(response);

        final HttpEntity entity = response.getEntity();
        Assertions.assertThat(entity).isNotNull();
        Assertions.assertThat(entity.getContentLength()).isEqualTo(-1);
        Assertions.assertThat(entity.isChunked()).isTrue();
        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(1);
        final InputStream content = entity.getContent();
        Assertions.assertThat(content).isNotNull();
        Assertions.assertThat(content).isInstanceOf(ChunkedInputStream.class);
    }

    @Test
    void testReadResponseEntityIdentity() throws Exception {
        final String s = "HTTP/1.1 200 OK\r\nServer: test\r\nTransfer-Encoding: identity\r\n\r\n123";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(0);

        final ClassicHttpResponse response = conn.receiveResponseHeader();

        Assertions.assertThat(response).isNotNull();
        Assertions.assertThat(response.getCode()).isEqualTo(200);
        Assertions.assertThat(response.containsHeader("Server")).isTrue();
        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(1);

        Assertions.assertThatThrownBy(() -> conn.receiveResponseEntity(response))
                .isInstanceOf(NotImplementedException.class);
    }

    @Test
    void testReadResponseNoEntity() throws Exception {
        final String s = "HTTP/1.1 200 OK\r\nServer: test\r\n\r\n";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(0);

        final ClassicHttpResponse response = conn.receiveResponseHeader();

        Assertions.assertThat(response).isNotNull();
        Assertions.assertThat(response.getCode()).isEqualTo(200);
        Assertions.assertThat(response.containsHeader("Server")).isTrue();
        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(1);

        conn.receiveResponseEntity(response);

        final HttpEntity entity = response.getEntity();
        Assertions.assertThat(entity).isNotNull();
        final InputStream content = entity.getContent();
        Assertions.assertThat(content).isNotNull();
        Assertions.assertThat(content).isInstanceOf(IdentityInputStream.class);
    }

    @Test
    void testEarlyResponseDoesNotAbortConnection() throws Exception {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final ByteArrayInputStream input = new ByteArrayInputStream(
                "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n"
                        .getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getOutputStream()).thenReturn(output);
        Mockito.when(socket.getInputStream()).thenReturn(input);

        final org.apache.hc.core5.http.io.ResponseOutOfOrderStrategy strategy =
                Mockito.mock(org.apache.hc.core5.http.io.ResponseOutOfOrderStrategy.class);
        Mockito.when(strategy.isEarlyResponseDetected(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyLong(), Mockito.anyLong()))
                .thenReturn(true);

        conn = new DefaultBHttpClientConnection(
                Http1Config.DEFAULT, null, null,
                DefaultContentLengthStrategy.INSTANCE,
                DefaultContentLengthStrategy.INSTANCE,
                strategy,
                DefaultHttpRequestWriterFactory.INSTANCE,
                DefaultHttpResponseParserFactory.INSTANCE);
        conn.bind(socket);

        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request.addHeader("Transfer-Encoding", "chunked");
        request.setEntity(new ByteArrayEntity(new byte[16384], ContentType.TEXT_PLAIN, true));

        conn.sendRequestHeader(request);
        Assertions.assertThatCode(() -> conn.sendRequestEntity(request)).doesNotThrowAnyException();
        Assertions.assertThat(conn.isOpen()).isTrue();
        Assertions.assertThat(conn.isConsistent()).isFalse();
        Assertions.assertThat(conn.receiveResponseHeader().getCode()).isEqualTo(400);
        Assertions.assertThat(new String(output.toByteArray(), StandardCharsets.US_ASCII))
                .doesNotEndWith("0\r\n\r\n");
    }

    @Test
    void testChunkedEntityFailureDoesNotTerminateMessage() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);
        conn.bind(socket);

        final HttpEntity entity = Mockito.mock(HttpEntity.class);
        Mockito.doAnswer(invocation -> {
            final OutputStream out = invocation.getArgument(0);
            out.write("123".getBytes(StandardCharsets.US_ASCII));
            throw new EOFException("source stream failed");
        }).when(entity).writeTo(Mockito.any(OutputStream.class));

        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request.addHeader("Transfer-Encoding", "chunked");
        request.setEntity(entity);
        conn.sendRequestHeader(request);
        Assertions.assertThatThrownBy(() ->
                        conn.sendRequestEntity(request))
                .isInstanceOf(EOFException.class);
        Assertions.assertThat(conn.isConsistent()).isFalse();
        Assertions.assertThat(conn.isOpen()).isFalse();
        Assertions.assertThat(new String(outStream.toByteArray(), StandardCharsets.US_ASCII))
                .doesNotEndWith("0\r\n\r\n");
    }

    @Test
    void testWriteRequestHead() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(0);

        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/stuff");
        request.addHeader("User-Agent", "test");

        conn.sendRequestHeader(request);
        conn.flush();

        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(1);
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertThat(s).isEqualTo("GET /stuff HTTP/1.1\r\nUser-Agent: test\r\n\r\n");
    }

    @Test
    void testWriteRequestEntityWithContentLength() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(0);

        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request.addHeader("User-Agent", "test");
        request.addHeader("Content-Length", "3");
        request.setEntity(new StringEntity("123", ContentType.TEXT_PLAIN));

        conn.sendRequestHeader(request);
        conn.sendRequestEntity(request);
        conn.flush();

        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(1);
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertThat(s).isEqualTo("POST /stuff HTTP/1.1\r\nUser-Agent: test\r\nContent-Length: 3\r\n\r\n123");
    }

    @Test
    void testWriteRequestEntityChunkCoded() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(0);

        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request.addHeader("User-Agent", "test");
        request.addHeader("Transfer-Encoding", "chunked");
        request.setEntity(new StringEntity("123", ContentType.TEXT_PLAIN));

        conn.sendRequestHeader(request);
        conn.sendRequestEntity(request);
        conn.flush();

        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(1);
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertThat(s).isEqualTo("POST /stuff HTTP/1.1\r\nUser-Agent: test\r\nTransfer-Encoding: " +
                "chunked\r\n\r\n3\r\n123\r\n0\r\n\r\n");
    }

    @Test
    void testWriteRequestEntityNoContentLength() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(0);

        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request.addHeader("User-Agent", "test");
        request.setEntity(new StringEntity("123", ContentType.TEXT_PLAIN));

        conn.sendRequestHeader(request);
        Assertions.assertThatThrownBy(() -> conn.sendRequestEntity(request))
                .isInstanceOf(LengthRequiredException.class);
    }

    @Test
    void testWriteRequestNoEntity() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(0);

        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request.addHeader("User-Agent", "test");

        conn.sendRequestHeader(request);
        conn.sendRequestEntity(request);
        conn.flush();

        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(1);
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertThat(s).isEqualTo("POST /stuff HTTP/1.1\r\nUser-Agent: test\r\n\r\n");
    }

    @Test
    void testTerminateRequestChunkedEntity() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(0);

        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request.addHeader("User-Agent", "test");
        request.addHeader("Transfer-Encoding", "chunked");
        final StringEntity entity = new StringEntity("123", ContentType.TEXT_PLAIN, true);

        request.setEntity(entity);

        conn.sendRequestHeader(request);
        conn.terminateRequest(request);
        conn.flush();

        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(1);
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertThat(s).isEqualTo("POST /stuff HTTP/1.1\r\nUser-Agent: test\r\nTransfer-Encoding: " +
                "chunked\r\n\r\n0\r\n\r\n");
        Assertions.assertThat(conn.isConsistent()).isTrue();
    }

    @Test
    void testTerminateRequestContentLengthShort() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(0);

        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request.addHeader("User-Agent", "test");
        request.addHeader("Content-Length", "3");
        final StringEntity entity = new StringEntity("123", ContentType.TEXT_PLAIN, true);
        request.setEntity(entity);

        conn.sendRequestHeader(request);
        conn.terminateRequest(request);
        conn.flush();

        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(1);
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertThat(s).isEqualTo("POST /stuff HTTP/1.1\r\nUser-Agent: test\r\nContent-Length: " +
                "3\r\n\r\n123");
        Assertions.assertThat(conn.isConsistent()).isTrue();
    }

    @Test
    void testTerminateRequestContentLengthLong() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(0);

        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request.addHeader("User-Agent", "test");
        request.addHeader("Content-Length", "3000");
        final ByteArrayEntity entity = new ByteArrayEntity(new byte[3000], ContentType.TEXT_PLAIN, true);
        request.setEntity(entity);

        conn.sendRequestHeader(request);
        conn.terminateRequest(request);
        conn.flush();

        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(1);
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertThat(s).isEqualTo("POST /stuff HTTP/1.1\r\nUser-Agent: test\r\nContent-Length: " +
                "3000\r\n\r\n");
        Assertions.assertThat(conn.isConsistent()).isFalse();
    }

}

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
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.NotImplementedException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.DefaultContentLengthStrategy;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.util.Timeout;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestDefaultBHttpServerConnection {

    @Mock
    Socket socket;

    DefaultBHttpServerConnection conn;

    @BeforeEach
    void prepareMocks() {
        conn = new DefaultBHttpServerConnection("http", Http1Config.DEFAULT,
                null, null,
                DefaultContentLengthStrategy.INSTANCE,
                DefaultContentLengthStrategy.INSTANCE,
                DefaultHttpRequestParserFactory.INSTANCE,
                DefaultHttpResponseWriterFactory.INSTANCE);
    }

    @Test
    void testBasics() {
        Assertions.assertThat(conn.isOpen()).isFalse();
        Assertions.assertThat(conn.toString()).isEqualTo("[Not bound]");
    }

    @Test
    void testReadRequestHead() throws Exception {
        final String s = "GET / HTTP/1.1\r\nUser-Agent: test\r\n\r\n";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(0);

        final ClassicHttpRequest request = conn.receiveRequestHeader();
        Assertions.assertThat(request).isNotNull();
        Assertions.assertThat(request.getPath()).isEqualTo("/");
        Assertions.assertThat(request.getMethod()).isEqualTo(Method.GET.name());
        Assertions.assertThat(request.containsHeader("User-Agent")).isTrue();
        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(1);
        Assertions.assertThat(conn.getEndpointDetails().getRemoteAddress()).isNull();
        Assertions.assertThat(conn.getEndpointDetails().getLocalAddress()).isNull();

        Assertions.assertThat(conn.getEndpointDetails().getSocketTimeout()).isEqualTo(Timeout.ofMilliseconds(0));
        Assertions.assertThat(conn.getEndpointDetails().toString()).isEqualTo("null<->null");
    }

    @Test
    void testReadRequestEntityWithContentLength() throws Exception {
        final String s = "POST / HTTP/1.1\r\nUser-Agent: test\r\nContent-Length: 3\r\n\r\n123";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(0);

        final ClassicHttpRequest request = conn.receiveRequestHeader();

        Assertions.assertThat(request).isNotNull();
        Assertions.assertThat(request.getPath()).isEqualTo("/");
        Assertions.assertThat(request.getMethod()).isEqualTo(Method.POST.name());
        Assertions.assertThat(request.containsHeader("User-Agent")).isTrue();
        Assertions.assertThat(request.getEntity()).isNull();
        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(1);

        conn.receiveRequestEntity(request);

        final HttpEntity entity = request.getEntity();
        Assertions.assertThat(entity).isNotNull();
        Assertions.assertThat(entity.getContentLength()).isEqualTo(3);
        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(1);
        final InputStream content = entity.getContent();
        Assertions.assertThat(content).isNotNull();
        Assertions.assertThat(content).isInstanceOf(ContentLengthInputStream.class);
    }

    @Test
    void testReadRequestEntityChunckCoded() throws Exception {
        final String s = "POST /stuff HTTP/1.1\r\nUser-Agent: test\r\nTransfer-Encoding: " +
                "chunked\r\n\r\n3\r\n123\r\n0\r\n\r\n";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(0);

        final ClassicHttpRequest request = conn.receiveRequestHeader();

        Assertions.assertThat(request).isNotNull();
        Assertions.assertThat(request.getPath()).isEqualTo("/stuff");
        Assertions.assertThat(request.getMethod()).isEqualTo(Method.POST.name());
        Assertions.assertThat(request.containsHeader("User-Agent")).isTrue();
        Assertions.assertThat(request.getEntity()).isNull();
        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(1);

        conn.receiveRequestEntity(request);

        final HttpEntity entity = request.getEntity();
        Assertions.assertThat(entity).isNotNull();
        Assertions.assertThat(entity.getContentLength()).isEqualTo(-1);
        Assertions.assertThat(entity.isChunked()).isTrue();
        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(1);
        final InputStream content = entity.getContent();
        Assertions.assertThat(content).isNotNull();
        Assertions.assertThat(content).isInstanceOf(ChunkedInputStream.class);
    }

    @Test
    void testReadRequestEntityIdentity() throws Exception {
        final String s = "POST /stuff HTTP/1.1\r\nUser-Agent: test\r\nTransfer-Encoding: " +
                "identity\r\n\r\n123";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(0);

        final ClassicHttpRequest request = conn.receiveRequestHeader();

        Assertions.assertThat(request).isNotNull();
        Assertions.assertThat(request.getPath()).isEqualTo("/stuff");
        Assertions.assertThat(request.getMethod()).isEqualTo(Method.POST.name());
        Assertions.assertThat(request.containsHeader("User-Agent")).isTrue();
        Assertions.assertThat(request.getEntity()).isNull();
        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(1);

        Assertions.assertThatThrownBy(() ->
                conn.receiveRequestEntity(request)).isInstanceOf(ProtocolException.class);
    }

    @Test
    void testReadRequestNoEntity() throws Exception {
        final String s = "POST /stuff HTTP/1.1\r\nUser-Agent: test\r\n\r\n";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(0);

        final ClassicHttpRequest request = conn.receiveRequestHeader();

        Assertions.assertThat(request).isNotNull();
        Assertions.assertThat(request.getPath()).isEqualTo("/stuff");
        Assertions.assertThat(request.getMethod()).isEqualTo(Method.POST.name());
        Assertions.assertThat(request.containsHeader("User-Agent")).isTrue();
        Assertions.assertThat(request.getEntity()).isNull();
        Assertions.assertThat(conn.getEndpointDetails().getRequestCount()).isEqualTo(1);

        conn.receiveRequestEntity(request);

        final HttpEntity entity = request.getEntity();
        Assertions.assertThat(entity).isNull();
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

        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        response.addHeader("Transfer-Encoding", "chunked");
        response.setEntity(entity);
        conn.sendResponseHeader(response);
        Assertions.assertThatThrownBy(() -> conn.sendResponseEntity(response))
                .isInstanceOf(EOFException.class);
        Assertions.assertThat(conn.isOpen()).isFalse();
        Assertions.assertThat(new String(outStream.toByteArray(), StandardCharsets.US_ASCII)
                .endsWith("0\r\n\r\n")).isFalse();
    }

    @Test
    void testWriteResponseHead() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(0);

        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        response.addHeader("User-Agent", "test");

        conn.sendResponseHeader(response);
        conn.flush();

        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(1);
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertThat(s).isEqualTo("HTTP/1.1 200 OK\r\nUser-Agent: test\r\n\r\n");
    }

    @Test
    void testWriteResponse100Head() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(0);

        final ClassicHttpResponse response = new BasicClassicHttpResponse(100, "Go on");

        conn.sendResponseHeader(response);
        conn.flush();

        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(0);
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertThat(s).isEqualTo("HTTP/1.1 100 Go on\r\n\r\n");
    }

    @Test
    void testWriteResponseEntityWithContentLength() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(0);

        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        response.addHeader("Server", "test");
        response.addHeader("Content-Length", "3");
        response.setEntity(new StringEntity("123", ContentType.TEXT_PLAIN));

        conn.sendResponseHeader(response);
        conn.sendResponseEntity(response);
        conn.flush();

        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(1);
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertThat(s).isEqualTo("HTTP/1.1 200 OK\r\nServer: test\r\nContent-Length: 3\r\n\r\n123");
    }

    @Test
    void testWriteResponseEntityChunkCoded() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(0);

        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        response.addHeader("Server", "test");
        response.addHeader("Transfer-Encoding", "chunked");
        response.setEntity(new StringEntity("123", ContentType.TEXT_PLAIN));

        conn.sendResponseHeader(response);
        conn.sendResponseEntity(response);
        conn.flush();

        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(1);
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertThat(s).isEqualTo("HTTP/1.1 200 OK\r\nServer: test\r\nTransfer-Encoding: " +
                "chunked\r\n\r\n3\r\n123\r\n0\r\n\r\n");
    }

    @Test
    void testWriteResponseEntityIdentity() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(0);

        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        response.addHeader("Server", "test");
        response.addHeader("Transfer-Encoding", "identity");
        response.setEntity(new StringEntity("123", ContentType.TEXT_PLAIN));

        conn.sendResponseHeader(response);
        Assertions.assertThatThrownBy(() ->
                conn.sendResponseEntity(response)).isInstanceOf(NotImplementedException.class);
        conn.flush();
    }

    @Test
    void testWriteResponseNoEntity() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(0);

        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        response.addHeader("Server", "test");

        conn.sendResponseHeader(response);
        conn.sendResponseEntity(response);
        conn.flush();

        Assertions.assertThat(conn.getEndpointDetails().getResponseCount()).isEqualTo(1);
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertThat(s).isEqualTo("HTTP/1.1 200 OK\r\nServer: test\r\n\r\n");
    }

}

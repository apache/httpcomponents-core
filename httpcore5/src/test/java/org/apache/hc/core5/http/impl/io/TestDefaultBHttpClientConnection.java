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
import org.junit.jupiter.api.Assertions;
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
        Assertions.assertFalse(conn.isOpen());
        Assertions.assertEquals("[Not bound]", conn.toString());
    }

    @Test
    void testReadResponseHead() throws Exception {
        final String s = "HTTP/1.1 200 OK\r\nUser-Agent: test\r\n\r\n";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getResponseCount());

        final ClassicHttpResponse response = conn.receiveResponseHeader();
        Assertions.assertNotNull(response);
        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(response.containsHeader("User-Agent"));
        Assertions.assertEquals(1, conn.getEndpointDetails().getResponseCount());
    }

    @Test
    void testReadResponseEntityWithoutContentLength() throws Exception {
        final String s = "HTTP/1.1 200 OK\r\nServer: test\r\n\r\n123";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getResponseCount());

        final ClassicHttpResponse response = conn.receiveResponseHeader();

        Assertions.assertNotNull(response);
        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(response.containsHeader("Server"));
        Assertions.assertEquals(1, conn.getEndpointDetails().getResponseCount());

        conn.receiveResponseEntity(response);

        final HttpEntity entity = response.getEntity();
        Assertions.assertNotNull(entity);
        Assertions.assertEquals(-1, entity.getContentLength());
        Assertions.assertEquals(1, conn.getEndpointDetails().getResponseCount());

        final InputStream content = entity.getContent();
        Assertions.assertNotNull(content);
        Assertions.assertEquals(3, content.available());
        Assertions.assertEquals('1', content.read());
        Assertions.assertEquals('2', content.read());
        Assertions.assertEquals('3', content.read());
    }

    @Test
    void testReadResponseEntityWithContentLength() throws Exception {
        final String s = "HTTP/1.1 200 OK\r\nServer: test\r\nContent-Length: 3\r\n\r\n123";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getResponseCount());

        final ClassicHttpResponse response = conn.receiveResponseHeader();

        Assertions.assertNotNull(response);
        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(response.containsHeader("Server"));
        Assertions.assertEquals(1, conn.getEndpointDetails().getResponseCount());

        conn.receiveResponseEntity(response);

        final HttpEntity entity = response.getEntity();
        Assertions.assertNotNull(entity);
        Assertions.assertEquals(3, entity.getContentLength());
        Assertions.assertEquals(1, conn.getEndpointDetails().getResponseCount());
        final InputStream content = entity.getContent();
        Assertions.assertNotNull(content);
        Assertions.assertInstanceOf(ContentLengthInputStream.class, content);
    }

    @Test
    void testReadResponseEntityChunkCoded() throws Exception {
        final String s = "HTTP/1.1 200 OK\r\nServer: test\r\nTransfer-Encoding: " +
                "chunked\r\n\r\n3\r\n123\r\n0\r\n\r\n";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getResponseCount());

        final ClassicHttpResponse response = conn.receiveResponseHeader();

        Assertions.assertNotNull(response);
        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(response.containsHeader("Server"));
        Assertions.assertEquals(1, conn.getEndpointDetails().getResponseCount());

        conn.receiveResponseEntity(response);

        final HttpEntity entity = response.getEntity();
        Assertions.assertNotNull(entity);
        Assertions.assertEquals(-1, entity.getContentLength());
        Assertions.assertTrue(entity.isChunked());
        Assertions.assertEquals(1, conn.getEndpointDetails().getResponseCount());
        final InputStream content = entity.getContent();
        Assertions.assertNotNull(content);
        Assertions.assertInstanceOf(ChunkedInputStream.class, content);
    }

    @Test
    void testReadResponseEntityIdentity() throws Exception {
        final String s = "HTTP/1.1 200 OK\r\nServer: test\r\nTransfer-Encoding: identity\r\n\r\n123";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getResponseCount());

        final ClassicHttpResponse response = conn.receiveResponseHeader();

        Assertions.assertNotNull(response);
        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(response.containsHeader("Server"));
        Assertions.assertEquals(1, conn.getEndpointDetails().getResponseCount());

        Assertions.assertThrows(NotImplementedException.class, () ->
                conn.receiveResponseEntity(response));
    }

    @Test
    void testReadResponseNoEntity() throws Exception {
        final String s = "HTTP/1.1 200 OK\r\nServer: test\r\n\r\n";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getResponseCount());

        final ClassicHttpResponse response = conn.receiveResponseHeader();

        Assertions.assertNotNull(response);
        Assertions.assertEquals(200, response.getCode());
        Assertions.assertTrue(response.containsHeader("Server"));
        Assertions.assertEquals(1, conn.getEndpointDetails().getResponseCount());

        conn.receiveResponseEntity(response);

        final HttpEntity entity = response.getEntity();
        Assertions.assertNotNull(entity);
        final InputStream content = entity.getContent();
        Assertions.assertNotNull(content);
        Assertions.assertInstanceOf(IdentityInputStream.class, content);
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
        Assertions.assertDoesNotThrow(() -> conn.sendRequestEntity(request));
        Assertions.assertTrue(conn.isOpen());
        Assertions.assertFalse(conn.isConsistent());
        Assertions.assertEquals(400, conn.receiveResponseHeader().getCode());
        Assertions.assertFalse(new String(output.toByteArray(), StandardCharsets.US_ASCII)
                .endsWith("0\r\n\r\n"));
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
        Assertions.assertThrows(EOFException.class, () -> conn.sendRequestEntity(request));
        Assertions.assertFalse(conn.isConsistent());
        Assertions.assertFalse(conn.isOpen());
        Assertions.assertFalse(new String(outStream.toByteArray(), StandardCharsets.US_ASCII)
                .endsWith("0\r\n\r\n"));
    }

    @Test
    void testWriteRequestHead() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getRequestCount());

        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.GET, "/stuff");
        request.addHeader("User-Agent", "test");

        conn.sendRequestHeader(request);
        conn.flush();

        Assertions.assertEquals(1, conn.getEndpointDetails().getRequestCount());
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertEquals("GET /stuff HTTP/1.1\r\nUser-Agent: test\r\n\r\n", s);
    }

    @Test
    void testWriteRequestEntityWithContentLength() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getRequestCount());

        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request.addHeader("User-Agent", "test");
        request.addHeader("Content-Length", "3");
        request.setEntity(new StringEntity("123", ContentType.TEXT_PLAIN));

        conn.sendRequestHeader(request);
        conn.sendRequestEntity(request);
        conn.flush();

        Assertions.assertEquals(1, conn.getEndpointDetails().getRequestCount());
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertEquals("POST /stuff HTTP/1.1\r\nUser-Agent: test\r\nContent-Length: 3\r\n\r\n123", s);
    }

    @Test
    void testWriteRequestEntityChunkCoded() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getRequestCount());

        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request.addHeader("User-Agent", "test");
        request.addHeader("Transfer-Encoding", "chunked");
        request.setEntity(new StringEntity("123", ContentType.TEXT_PLAIN));

        conn.sendRequestHeader(request);
        conn.sendRequestEntity(request);
        conn.flush();

        Assertions.assertEquals(1, conn.getEndpointDetails().getRequestCount());
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertEquals("POST /stuff HTTP/1.1\r\nUser-Agent: test\r\nTransfer-Encoding: " +
                "chunked\r\n\r\n3\r\n123\r\n0\r\n\r\n", s);
    }

    @Test
    void testWriteRequestEntityNoContentLength() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getRequestCount());

        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request.addHeader("User-Agent", "test");
        request.setEntity(new StringEntity("123", ContentType.TEXT_PLAIN));

        conn.sendRequestHeader(request);
        Assertions.assertThrows(LengthRequiredException.class, () ->
                conn.sendRequestEntity(request));
    }

    @Test
    void testWriteRequestNoEntity() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getRequestCount());

        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request.addHeader("User-Agent", "test");

        conn.sendRequestHeader(request);
        conn.sendRequestEntity(request);
        conn.flush();

        Assertions.assertEquals(1, conn.getEndpointDetails().getRequestCount());
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertEquals("POST /stuff HTTP/1.1\r\nUser-Agent: test\r\n\r\n", s);
    }

    @Test
    void testTerminateRequestChunkedEntity() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getRequestCount());

        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request.addHeader("User-Agent", "test");
        request.addHeader("Transfer-Encoding", "chunked");
        final StringEntity entity = new StringEntity("123", ContentType.TEXT_PLAIN, true);

        request.setEntity(entity);

        conn.sendRequestHeader(request);
        conn.terminateRequest(request);
        conn.flush();

        Assertions.assertEquals(1, conn.getEndpointDetails().getRequestCount());
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertEquals("POST /stuff HTTP/1.1\r\nUser-Agent: test\r\nTransfer-Encoding: " +
                "chunked\r\n\r\n0\r\n\r\n", s);
        Assertions.assertTrue(conn.isConsistent());
    }

    @Test
    void testTerminateRequestContentLengthShort() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getRequestCount());

        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request.addHeader("User-Agent", "test");
        request.addHeader("Content-Length", "3");
        final StringEntity entity = new StringEntity("123", ContentType.TEXT_PLAIN, true);
        request.setEntity(entity);

        conn.sendRequestHeader(request);
        conn.terminateRequest(request);
        conn.flush();

        Assertions.assertEquals(1, conn.getEndpointDetails().getRequestCount());
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertEquals("POST /stuff HTTP/1.1\r\nUser-Agent: test\r\nContent-Length: " +
                "3\r\n\r\n123", s);
        Assertions.assertTrue(conn.isConsistent());
    }

    @Test
    void testTerminateRequestContentLengthLong() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assertions.assertEquals(0, conn.getEndpointDetails().getRequestCount());

        final ClassicHttpRequest request = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request.addHeader("User-Agent", "test");
        request.addHeader("Content-Length", "3000");
        final ByteArrayEntity entity = new ByteArrayEntity(new byte[3000], ContentType.TEXT_PLAIN, true);
        request.setEntity(entity);

        conn.sendRequestHeader(request);
        conn.terminateRequest(request);
        conn.flush();

        Assertions.assertEquals(1, conn.getEndpointDetails().getRequestCount());
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertEquals("POST /stuff HTTP/1.1\r\nUser-Agent: test\r\nContent-Length: " +
                "3000\r\n\r\n", s);
        Assertions.assertFalse(conn.isConsistent());
    }

}

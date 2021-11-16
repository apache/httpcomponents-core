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
import java.io.InputStream;
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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class TestDefaultBHttpClientConnection {

    @Mock
    private Socket socket;

    private DefaultBHttpClientConnection conn;

    @BeforeEach
    public void prepareMocks() {
        MockitoAnnotations.openMocks(this);
        conn = new DefaultBHttpClientConnection(Http1Config.DEFAULT,
                null, null,
                DefaultContentLengthStrategy.INSTANCE,
                DefaultContentLengthStrategy.INSTANCE,
                DefaultHttpRequestWriterFactory.INSTANCE,
                DefaultHttpResponseParserFactory.INSTANCE);
    }

    @Test
    public void testBasics() throws Exception {
        Assertions.assertFalse(conn.isOpen());
        Assertions.assertEquals("[Not bound]", conn.toString());
    }

    @Test
    public void testReadResponseHead() throws Exception {
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
    public void testReadResponseEntityWithoutContentLength() throws Exception {
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
    public void testReadResponseEntityWithContentLength() throws Exception {
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
        Assertions.assertTrue(content instanceof ContentLengthInputStream);
    }

    @Test
    public void testReadResponseEntityChunkCoded() throws Exception {
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
        Assertions.assertTrue(content instanceof ChunkedInputStream);
    }

    @Test
    public void testReadResponseEntityIdentity() throws Exception {
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
    public void testReadResponseNoEntity() throws Exception {
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
        Assertions.assertTrue(content instanceof IdentityInputStream);
    }

    @Test
    public void testWriteRequestHead() throws Exception {
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
    public void testWriteRequestEntityWithContentLength() throws Exception {
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
    public void testWriteRequestEntityChunkCoded() throws Exception {
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
    public void testWriteRequestEntityNoContentLength() throws Exception {
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
    public void testWriteRequestNoEntity() throws Exception {
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
    public void testTerminateRequestChunkedEntity() throws Exception {
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
    public void testTerminateRequestContentLengthShort() throws Exception {
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
    public void testTerminateRequestContentLengthLong() throws Exception {
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

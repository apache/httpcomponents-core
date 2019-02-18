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
import org.apache.hc.core5.http.NotImplementedException;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.DefaultContentLengthStrategy;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class TestDefaultBHttpClientConnection {

    @Mock
    private Socket socket;

    private DefaultBHttpClientConnection conn;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        conn = new DefaultBHttpClientConnection(H1Config.DEFAULT,
            null, null,
            DefaultContentLengthStrategy.INSTANCE,
            DefaultContentLengthStrategy.INSTANCE,
            DefaultHttpRequestWriterFactory.INSTANCE,
            DefaultHttpResponseParserFactory.INSTANCE);
    }

    @Test
    public void testBasics() throws Exception {
        Assert.assertFalse(conn.isOpen());
        Assert.assertEquals("[Not bound]", conn.toString());
    }

    @Test
    public void testReadResponseHead() throws Exception {
        final String s = "HTTP/1.1 200 OK\r\nUser-Agent: test\r\n\r\n";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assert.assertEquals(0, conn.getEndpointDetails().getResponseCount());

        final ClassicHttpResponse response = conn.receiveResponseHeader();
        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getCode());
        Assert.assertTrue(response.containsHeader("User-Agent"));
        Assert.assertEquals(1, conn.getEndpointDetails().getResponseCount());
    }

    @Test
    public void testReadResponseEntityWithoutContentLength() throws Exception {
        final String s = "HTTP/1.1 200 OK\r\nServer: test\r\n\r\n123";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assert.assertEquals(0, conn.getEndpointDetails().getResponseCount());

        final ClassicHttpResponse response = conn.receiveResponseHeader();

        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getCode());
        Assert.assertTrue(response.containsHeader("Server"));
        Assert.assertEquals(1, conn.getEndpointDetails().getResponseCount());

        conn.receiveResponseEntity(response);

        final HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertEquals(-1, entity.getContentLength());
        Assert.assertEquals(1, conn.getEndpointDetails().getResponseCount());

        final InputStream content = entity.getContent();
        Assert.assertNotNull(content);
        Assert.assertEquals(3, content.available());
        Assert.assertEquals('1', content.read());
        Assert.assertEquals('2', content.read());
        Assert.assertEquals('3', content.read());
    }

    @Test
    public void testReadResponseEntityWithContentLength() throws Exception {
        final String s = "HTTP/1.1 200 OK\r\nServer: test\r\nContent-Length: 3\r\n\r\n123";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assert.assertEquals(0, conn.getEndpointDetails().getResponseCount());

        final ClassicHttpResponse response = conn.receiveResponseHeader();

        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getCode());
        Assert.assertTrue(response.containsHeader("Server"));
        Assert.assertEquals(1, conn.getEndpointDetails().getResponseCount());

        conn.receiveResponseEntity(response);

        final HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertEquals(3, entity.getContentLength());
        Assert.assertEquals(1, conn.getEndpointDetails().getResponseCount());
        final InputStream content = entity.getContent();
        Assert.assertNotNull(content);
        Assert.assertTrue(content instanceof ContentLengthInputStream);
    }

    @Test
    public void testReadResponseEntityChunkCoded() throws Exception {
        final String s = "HTTP/1.1 200 OK\r\nServer: test\r\nTransfer-Encoding: " +
                "chunked\r\n\r\n3\r\n123\r\n0\r\n\r\n";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assert.assertEquals(0, conn.getEndpointDetails().getResponseCount());

        final ClassicHttpResponse response = conn.receiveResponseHeader();

        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getCode());
        Assert.assertTrue(response.containsHeader("Server"));
        Assert.assertEquals(1, conn.getEndpointDetails().getResponseCount());

        conn.receiveResponseEntity(response);

        final HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertEquals(-1, entity.getContentLength());
        Assert.assertEquals(true, entity.isChunked());
        Assert.assertEquals(1, conn.getEndpointDetails().getResponseCount());
        final InputStream content = entity.getContent();
        Assert.assertNotNull(content);
        Assert.assertTrue(content instanceof ChunkedInputStream);
    }

    @Test(expected = NotImplementedException.class)
    public void testReadResponseEntityIdentity() throws Exception {
        final String s = "HTTP/1.1 200 OK\r\nServer: test\r\nTransfer-Encoding: identity\r\n\r\n123";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assert.assertEquals(0, conn.getEndpointDetails().getResponseCount());

        final ClassicHttpResponse response = conn.receiveResponseHeader();

        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getCode());
        Assert.assertTrue(response.containsHeader("Server"));
        Assert.assertEquals(1, conn.getEndpointDetails().getResponseCount());

        conn.receiveResponseEntity(response);
    }

    @Test
    public void testReadResponseNoEntity() throws Exception {
        final String s = "HTTP/1.1 200 OK\r\nServer: test\r\n\r\n";
        final ByteArrayInputStream inStream = new ByteArrayInputStream(s.getBytes(StandardCharsets.US_ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(inStream);

        conn.bind(socket);

        Assert.assertEquals(0, conn.getEndpointDetails().getResponseCount());

        final ClassicHttpResponse response = conn.receiveResponseHeader();

        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getCode());
        Assert.assertTrue(response.containsHeader("Server"));
        Assert.assertEquals(1, conn.getEndpointDetails().getResponseCount());

        conn.receiveResponseEntity(response);

        final HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        final InputStream content = entity.getContent();
        Assert.assertNotNull(content);
        Assert.assertTrue(content instanceof IdentityInputStream);
    }

    @Test
    public void testWriteRequestHead() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assert.assertEquals(0, conn.getEndpointDetails().getRequestCount());

        final ClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/stuff");
        request.addHeader("User-Agent", "test");

        conn.sendRequestHeader(request);
        conn.flush();

        Assert.assertEquals(1, conn.getEndpointDetails().getRequestCount());
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assert.assertEquals("GET /stuff HTTP/1.1\r\nUser-Agent: test\r\n\r\n", s);
    }

    @Test
    public void testWriteRequestEntityWithContentLength() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assert.assertEquals(0, conn.getEndpointDetails().getRequestCount());

        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/stuff");
        request.addHeader("User-Agent", "test");
        request.addHeader("Content-Length", "3");
        request.setEntity(new StringEntity("123", ContentType.TEXT_PLAIN));

        conn.sendRequestHeader(request);
        conn.sendRequestEntity(request);
        conn.flush();

        Assert.assertEquals(1, conn.getEndpointDetails().getRequestCount());
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assert.assertEquals("POST /stuff HTTP/1.1\r\nUser-Agent: test\r\nContent-Length: 3\r\n\r\n123", s);
    }

    @Test
    public void testWriteRequestEntityChunkCoded() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assert.assertEquals(0, conn.getEndpointDetails().getRequestCount());

        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/stuff");
        request.addHeader("User-Agent", "test");
        request.addHeader("Transfer-Encoding", "chunked");
        request.setEntity(new StringEntity("123", ContentType.TEXT_PLAIN));

        conn.sendRequestHeader(request);
        conn.sendRequestEntity(request);
        conn.flush();

        Assert.assertEquals(1, conn.getEndpointDetails().getRequestCount());
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assert.assertEquals("POST /stuff HTTP/1.1\r\nUser-Agent: test\r\nTransfer-Encoding: " +
                "chunked\r\n\r\n3\r\n123\r\n0\r\n\r\n", s);
    }

    @Test(expected = LengthRequiredException.class)
    public void testWriteRequestEntityNoContentLength() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assert.assertEquals(0, conn.getEndpointDetails().getRequestCount());

        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/stuff");
        request.addHeader("User-Agent", "test");
        request.setEntity(new StringEntity("123", ContentType.TEXT_PLAIN));

        conn.sendRequestHeader(request);
        conn.sendRequestEntity(request);
    }

    @Test
    public void testWriteRequestNoEntity() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assert.assertEquals(0, conn.getEndpointDetails().getRequestCount());

        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/stuff");
        request.addHeader("User-Agent", "test");

        conn.sendRequestHeader(request);
        conn.sendRequestEntity(request);
        conn.flush();

        Assert.assertEquals(1, conn.getEndpointDetails().getRequestCount());
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assert.assertEquals("POST /stuff HTTP/1.1\r\nUser-Agent: test\r\n\r\n", s);
    }

    @Test
    public void testTerminateRequestChunkedEntity() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assert.assertEquals(0, conn.getEndpointDetails().getRequestCount());

        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/stuff");
        request.addHeader("User-Agent", "test");
        request.addHeader("Transfer-Encoding", "chunked");
        final StringEntity entity = new StringEntity("123", ContentType.TEXT_PLAIN, true);

        request.setEntity(entity);

        conn.sendRequestHeader(request);
        conn.terminateRequest(request);
        conn.flush();

        Assert.assertEquals(1, conn.getEndpointDetails().getRequestCount());
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assert.assertEquals("POST /stuff HTTP/1.1\r\nUser-Agent: test\r\nTransfer-Encoding: " +
                "chunked\r\n\r\n0\r\n\r\n", s);
        Assert.assertTrue(conn.isConsistent());
    }

    @Test
    public void testTerminateRequestContentLengthShort() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assert.assertEquals(0, conn.getEndpointDetails().getRequestCount());

        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/stuff");
        request.addHeader("User-Agent", "test");
        request.addHeader("Content-Length", "3");
        final StringEntity entity = new StringEntity("123", ContentType.TEXT_PLAIN, true);
        request.setEntity(entity);

        conn.sendRequestHeader(request);
        conn.terminateRequest(request);
        conn.flush();

        Assert.assertEquals(1, conn.getEndpointDetails().getRequestCount());
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assert.assertEquals("POST /stuff HTTP/1.1\r\nUser-Agent: test\r\nContent-Length: " +
                "3\r\n\r\n123", s);
        Assert.assertTrue(conn.isConsistent());
    }

    @Test
    public void testTerminateRequestContentLengthLong() throws Exception {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);

        conn.bind(socket);

        Assert.assertEquals(0, conn.getEndpointDetails().getRequestCount());

        final ClassicHttpRequest request = new BasicClassicHttpRequest("POST", "/stuff");
        request.addHeader("User-Agent", "test");
        request.addHeader("Content-Length", "3000");
        final ByteArrayEntity entity = new ByteArrayEntity(new byte[3000], ContentType.TEXT_PLAIN, true);
        request.setEntity(entity);

        conn.sendRequestHeader(request);
        conn.terminateRequest(request);
        conn.flush();

        Assert.assertEquals(1, conn.getEndpointDetails().getRequestCount());
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assert.assertEquals("POST /stuff HTTP/1.1\r\nUser-Agent: test\r\nContent-Length: " +
                "3000\r\n\r\n", s);
        Assert.assertFalse(conn.isConsistent());
    }

}

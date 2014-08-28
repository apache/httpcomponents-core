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
package org.apache.http.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.Socket;

import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.config.MessageConstraints;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.entity.LaxContentLengthStrategy;
import org.apache.http.impl.entity.StrictContentLengthStrategy;
import org.apache.http.impl.io.DefaultHttpRequestWriterFactory;
import org.apache.http.impl.io.DefaultHttpResponseParserFactory;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import org.junit.Assert;

public class TestDefaultBHttpClientConnection {

    @Mock
    private Socket socket;

    private DefaultBHttpClientConnection conn;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        conn = new DefaultBHttpClientConnection(1024, 1024,
            null, null,
            MessageConstraints.DEFAULT,
            LaxContentLengthStrategy.INSTANCE,
            StrictContentLengthStrategy.INSTANCE,
            DefaultHttpRequestWriterFactory.INSTANCE,
            DefaultHttpResponseParserFactory.INSTANCE);
    }

    @Test
    public void testBasics() throws Exception {
        Assert.assertFalse(conn.isOpen());
        Assert.assertEquals("[Not bound]", conn.toString());
    }

    @Test
    public void testReadRequestHead() throws Exception {
        final String s = "HTTP/1.1 200 OK\r\nUser-Agent: test\r\n\r\n";
        final ByteArrayInputStream instream = new ByteArrayInputStream(s.getBytes(Consts.ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(instream);

        conn.bind(socket);

        Assert.assertEquals(0, conn.getMetrics().getResponseCount());

        final HttpResponse response = conn.receiveResponseHeader();
        Assert.assertNotNull(response);
        Assert.assertEquals(HttpVersion.HTTP_1_1, response.getProtocolVersion());
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Assert.assertTrue(response.containsHeader("User-Agent"));
        Assert.assertEquals(1, conn.getMetrics().getResponseCount());
    }

    @Test
    public void testReadRequestEntity() throws Exception {
        final String s = "HTTP/1.1 200 OK\r\nUser-Agent: test\r\nContent-Length: 3\r\n\r\n123";
        final ByteArrayInputStream instream = new ByteArrayInputStream(s.getBytes(Consts.ASCII));
        Mockito.when(socket.getInputStream()).thenReturn(instream);

        conn.bind(socket);

        Assert.assertEquals(0, conn.getMetrics().getResponseCount());

        final HttpResponse response = conn.receiveResponseHeader();

        Assert.assertNotNull(response);
        Assert.assertEquals(HttpVersion.HTTP_1_1, response.getProtocolVersion());
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        Assert.assertTrue(response.containsHeader("User-Agent"));
        Assert.assertEquals(1, conn.getMetrics().getResponseCount());

        conn.receiveResponseEntity(response);

        final HttpEntity entity = response.getEntity();
        Assert.assertNotNull(entity);
        Assert.assertEquals(3, entity.getContentLength());
        Assert.assertEquals(1, conn.getMetrics().getResponseCount());
    }

    @Test
    public void testWriteResponseHead() throws Exception {
        final ByteArrayOutputStream outstream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outstream);

        conn.bind(socket);

        Assert.assertEquals(0, conn.getMetrics().getRequestCount());

        final HttpRequest request = new BasicHttpRequest("GET", "/stuff", HttpVersion.HTTP_1_1);
        request.addHeader("User-Agent", "test");

        conn.sendRequestHeader(request);
        conn.flush();

        Assert.assertEquals(1, conn.getMetrics().getRequestCount());
        final String s = new String(outstream.toByteArray(), "ASCII");
        Assert.assertEquals("GET /stuff HTTP/1.1\r\nUser-Agent: test\r\n\r\n", s);
    }

    @Test
    public void testWriteResponseEntity() throws Exception {
        final ByteArrayOutputStream outstream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outstream);

        conn.bind(socket);

        Assert.assertEquals(0, conn.getMetrics().getRequestCount());

        final HttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST",
                "/stuff", HttpVersion.HTTP_1_1);
        request.addHeader("User-Agent", "test");
        request.addHeader("Content-Length", "3");
        request.setEntity(new StringEntity("123", ContentType.TEXT_PLAIN));

        conn.sendRequestHeader(request);
        conn.sendRequestEntity(request);
        conn.flush();

        Assert.assertEquals(1, conn.getMetrics().getRequestCount());
        final String s = new String(outstream.toByteArray(), "ASCII");
        Assert.assertEquals("POST /stuff HTTP/1.1\r\nUser-Agent: test\r\nContent-Length: 3\r\n\r\n123", s);
    }

}

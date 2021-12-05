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
package org.apache.hc.core5.http.io.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.DefaultContentLengthStrategy;
import org.apache.hc.core5.http.impl.io.DefaultBHttpServerConnection;
import org.apache.hc.core5.http.impl.io.DefaultHttpRequestParserFactory;
import org.apache.hc.core5.http.impl.io.DefaultHttpResponseWriterFactory;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class ClassicResponseBuilderTest {

    @Mock
    Socket socket;

    DefaultBHttpServerConnection conn;

    ByteArrayOutputStream outStream;

    @BeforeEach
    public void prepareMocks() throws IOException {
        MockitoAnnotations.openMocks(this);
        conn = new DefaultBHttpServerConnection("http", Http1Config.DEFAULT,
                null, null,
                DefaultContentLengthStrategy.INSTANCE,
                DefaultContentLengthStrategy.INSTANCE,
                DefaultHttpRequestParserFactory.INSTANCE,
                DefaultHttpResponseWriterFactory.INSTANCE);
        outStream = new ByteArrayOutputStream();
        Mockito.when(socket.getOutputStream()).thenReturn(outStream);
        conn.bind(socket);
        Assertions.assertEquals(0, conn.getEndpointDetails().getResponseCount());
    }

    @Test
    void create() throws IOException, HttpException {

        final ClassicHttpResponse response = ClassicResponseBuilder.create(200)
                .setHeader("X-Test-Filter", "active")
                .addHeader("header1", "blah")
                .setHeader(new BasicHeader("header2", "blah"))
                .addHeader(new BasicHeader("header3", "blah"))
                .setVersion(HttpVersion.HTTP_1_1)
                .setEntity("<html><body><h1>Access OK</h1></body></html>", ContentType.TEXT_HTML)
                .setEntity("Another entity")
                .build();

        response.addHeader("User-Agent", "test");

        conn.sendResponseHeader(response);
        conn.flush();

        Assertions.assertEquals(1, conn.getEndpointDetails().getResponseCount());
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertEquals("HTTP/1.1 200 OK\r\nX-Test-Filter: active\r\nheader1: blah\r\nheader2: blah\r\nheader3: blah\r\nUser-Agent: test\r\n\r\n", s);
        Assertions.assertNotNull(response.getEntity());
    }

    @Test
    void remove() throws IOException, HttpException {
        final Header header = new BasicHeader("header2", "blah");
        final ClassicHttpResponse response = ClassicResponseBuilder.create(200)
                .setEntity(new StringEntity("123", ContentType.TEXT_PLAIN))
                .setHeader("X-Test-Filter", "active")
                .addHeader("header1", "blah")
                .setHeader(header)
                .addHeader(new BasicHeader("header3", "blah"))
                .setVersion(HttpVersion.HTTP_1_1)
                .setEntity("<html><body><h1>Access OK</h1></body></html>", ContentType.TEXT_HTML)
                .setEntity("Another entity")
                .removeHeader(header)
                .build();

        response.removeHeaders("X-Test-Filter");

        conn.sendResponseHeader(response);
        conn.flush();

        Assertions.assertEquals(1, conn.getEndpointDetails().getResponseCount());
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertEquals("HTTP/1.1 200 OK\r\nheader1: blah\r\nheader3: blah\r\n\r\n", s);
        Assertions.assertNotNull(response.getEntity());

    }

    @Test
    void copy() throws IOException, HttpException {
        final Header header = new BasicHeader("header2", "blah");
        final ClassicHttpResponse response = ClassicResponseBuilder.create(200)
                .setEntity(new StringEntity("123", ContentType.TEXT_PLAIN))
                .addHeader("header1", "blah")
                .setHeader(header)
                .addHeader(new BasicHeader("header3", "blah"))
                .setVersion(HttpVersion.HTTP_1_1)
                .setEntity("<html><body><h1>Access OK</h1></body></html>", ContentType.TEXT_HTML)
                .setEntity("Another entity")
                .removeHeader(header)
                .build();

        final ClassicResponseBuilder classicResponseBuilder = ClassicResponseBuilder.copy(response);
        final ClassicHttpResponse response2 = classicResponseBuilder.build();
        conn.sendResponseHeader(response2);
        conn.flush();

        Assertions.assertEquals(1, conn.getEndpointDetails().getResponseCount());
        final String s = new String(outStream.toByteArray(), StandardCharsets.US_ASCII);
        Assertions.assertEquals("HTTP/1.1 200 OK\r\nheader1: blah\r\nheader3: blah\r\n\r\n", s);
        Assertions.assertNotNull(response.getEntity());
        Assertions.assertNotNull(classicResponseBuilder.toString());
    }
}
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

package org.apache.hc.core5.http.integration;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.entity.ContentType;
import org.apache.hc.core5.http.entity.EntityUtils;
import org.apache.hc.core5.http.impl.nio.BasicAsyncRequestHandler;
import org.apache.hc.core5.http.impl.nio.UriHttpAsyncRequestHandlerMapper;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.nio.entity.NByteArrayEntity;
import org.apache.hc.core5.http.nio.entity.NStringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.ImmutableHttpProcessor;
import org.apache.hc.core5.http.protocol.ResponseConnControl;
import org.apache.hc.core5.http.protocol.ResponseContent;
import org.apache.hc.core5.http.protocol.ResponseServer;
import org.apache.hc.core5.http.testserver.nio.HttpCoreNIOTestBase;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for handling pipelined requests.
 */
public class TestServerSidePipelining extends HttpCoreNIOTestBase {

    @Before
    public void setUp() throws Exception {
        initServer();
        this.server.setHttpProcessor(new ImmutableHttpProcessor(
                new ResponseServer("TEST-SERVER/1.1"), new ResponseContent(), new ResponseConnControl()));
        final UriHttpAsyncRequestHandlerMapper registry = new UriHttpAsyncRequestHandlerMapper();
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new HttpRequestHandler() {

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                final String content = "thank you very much";
                final NStringEntity entity = new NStringEntity(content, ContentType.DEFAULT_TEXT);
                response.setEntity(entity);
            }

        }));
        this.server.registerHandler("/goodbye", new BasicAsyncRequestHandler(new HttpRequestHandler() {

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                final String content = "and goodbye";
                final NStringEntity entity = new NStringEntity(content, ContentType.DEFAULT_TEXT);
                response.setEntity(entity);
                response.setHeader(HttpHeaders.CONNECTION, "Close");
            }

        }));
        this.server.registerHandler("/echo", new BasicAsyncRequestHandler(new HttpRequestHandler() {

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                final HttpEntity requestEntity = request.getEntity();
                final HttpEntity responseEntity;
                if (requestEntity != null) {
                    final ContentType contentType = ContentType.getOrDefault(requestEntity);
                    responseEntity = new NByteArrayEntity(
                            EntityUtils.toByteArray(requestEntity), contentType);
                } else {
                    responseEntity = new NStringEntity("Say what?", ContentType.DEFAULT_TEXT);
                }
                response.setEntity(responseEntity);
            }

        }));
    }

    @After
    public void tearDown() throws Exception {
        shutDownServer();
    }

    @Test
    public void testGetRequestPipelining() throws Exception {
        this.server.start();

        final ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();

        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
        try (final Socket socket = new Socket("localhost", address.getPort())) {
            final OutputStream outstream = socket.getOutputStream();
            final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outstream, "US-ASCII"));
            writer.write("GET / HTTP/1.1\r\n");
            writer.write("Host: localhost\r\n");
            writer.write("\r\n");
            writer.write("GET / HTTP/1.1\r\n");
            writer.write("Host: localhost\r\n");
            writer.write("\r\n");
            writer.write("GET / HTTP/1.1\r\n");
            writer.write("Host: localhost\r\n");
            writer.write("\r\n");
            writer.write("GET / HTTP/1.1\r\n");
            writer.write("Host: localhost\r\n");
            writer.write("Connection: close\r\n");
            writer.write("\r\n");
            writer.flush();
            final InputStream instream = socket.getInputStream();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(instream, "US-ASCII"));
            final StringBuilder buf = new StringBuilder();
            final char[] tmp = new char[1024];
            int l;
            while ((l = reader.read(tmp)) != -1) {
                buf.append(tmp, 0, l);
            }
            reader.close();
            writer.close();
            final String expected =
                    "HTTP/1.1 200 OK\r\n" +
                    "Server: TEST-SERVER/1.1\r\n" +
                    "Content-Length: 19\r\n" +
                    "Content-Type: text/plain; charset=ISO-8859-1\r\n" +
                    "\r\n" +
                    "thank you very much" +
                    "HTTP/1.1 200 OK\r\n" +
                    "Server: TEST-SERVER/1.1\r\n" +
                    "Content-Length: 19\r\n" +
                    "Content-Type: text/plain; charset=ISO-8859-1\r\n" +
                    "\r\n" +
                    "thank you very much" +
                    "HTTP/1.1 200 OK\r\n" +
                    "Server: TEST-SERVER/1.1\r\n" +
                    "Content-Length: 19\r\n" +
                    "Content-Type: text/plain; charset=ISO-8859-1\r\n" +
                    "\r\n" +
                    "thank you very much" +
                    "HTTP/1.1 200 OK\r\n" +
                    "Server: TEST-SERVER/1.1\r\n" +
                    "Content-Length: 19\r\n" +
                    "Content-Type: text/plain; charset=ISO-8859-1\r\n" +
                    "Connection: close\r\n" +
                    "\r\n" +
                    "thank you very much";
            Assert.assertEquals(expected, buf.toString());

        }

    }

    @Test
    public void testPostRequestPipelining() throws Exception {
        this.server.start();

        final ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();

        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
        try (final Socket socket = new Socket("localhost", address.getPort())) {
            final OutputStream outstream = socket.getOutputStream();
            final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outstream, "US-ASCII"));
            writer.write("POST /echo HTTP/1.1\r\n");
            writer.write("Host: localhost\r\n");
            writer.write("Content-Length: 16\r\n");
            writer.write("Content-Type: text/plain; charset=ISO-8859-1\r\n");
            writer.write("\r\n");
            writer.write("blah blah blah\r\n");
            writer.write("POST /echo HTTP/1.1\r\n");
            writer.write("Host: localhost\r\n");
            writer.write("Transfer-Encoding: chunked\r\n");
            writer.write("Content-Type: text/plain; charset=ISO-8859-1\r\n");
            writer.write("\r\n");
            writer.write("10\r\n");
            writer.write("yada yada yada\r\n");
            writer.write("\r\n");
            writer.write("0\r\n");
            writer.write("\r\n");
            writer.write("GET / HTTP/1.1\r\n");
            writer.write("Host: localhost\r\n");
            writer.write("\r\n");
            writer.write("GET /goodbye HTTP/1.1\r\n");
            writer.write("Host: localhost\r\n");
            writer.write("\r\n");
            writer.flush();
            final InputStream instream = socket.getInputStream();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(instream, "US-ASCII"));
            final StringBuilder buf = new StringBuilder();
            final char[] tmp = new char[1024];
            int l;
            while ((l = reader.read(tmp)) != -1) {
                buf.append(tmp, 0, l);
            }
            reader.close();
            writer.close();
            final String expected =
                    "HTTP/1.1 200 OK\r\n" +
                    "Server: TEST-SERVER/1.1\r\n" +
                    "Content-Length: 16\r\n" +
                    "Content-Type: text/plain; charset=ISO-8859-1\r\n" +
                    "\r\n" +
                    "blah blah blah\r\n" +
                    "HTTP/1.1 200 OK\r\n" +
                    "Server: TEST-SERVER/1.1\r\n" +
                    "Content-Length: 16\r\n" +
                    "Content-Type: text/plain; charset=ISO-8859-1\r\n" +
                    "\r\n" +
                    "yada yada yada\r\n" +
                    "HTTP/1.1 200 OK\r\n" +
                    "Server: TEST-SERVER/1.1\r\n" +
                    "Content-Length: 19\r\n" +
                    "Content-Type: text/plain; charset=ISO-8859-1\r\n" +
                    "\r\n" +
                    "thank you very much" +
                    "HTTP/1.1 200 OK\r\n" +
                    "Connection: Close\r\n" +
                    "Server: TEST-SERVER/1.1\r\n" +
                    "Content-Length: 11\r\n" +
                    "Content-Type: text/plain; charset=ISO-8859-1\r\n" +
                    "\r\n" +
                    "and goodbye";
            Assert.assertEquals(expected, buf.toString());

        }

    }

    @Test
    public void testPostRequestPipeliningExpectContinue() throws Exception {
        this.server.start();

        final ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();

        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
        try (final Socket socket = new Socket("localhost", address.getPort())) {
            final OutputStream outstream = socket.getOutputStream();
            final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outstream, "US-ASCII"));
            writer.write("POST /echo HTTP/1.1\r\n");
            writer.write("Host: localhost\r\n");
            writer.write("Expect: 100-Continue\r\n");
            writer.write("Content-Length: 16\r\n");
            writer.write("Content-Type: text/plain; charset=ISO-8859-1\r\n");
            writer.write("\r\n");
            writer.write("blah blah blah\r\n");
            writer.write("POST /echo HTTP/1.1\r\n");
            writer.write("Host: localhost\r\n");
            writer.write("Expect: 100-Continue\r\n");
            writer.write("Content-Length: 16\r\n");
            writer.write("Content-Type: text/plain; charset=ISO-8859-1\r\n");
            writer.write("\r\n");
            writer.write("yada yada yada\r\n");
            writer.write("POST /echo HTTP/1.1\r\n");
            writer.write("Host: localhost\r\n");
            writer.write("Expect: 100-Continue\r\n");
            writer.write("Content-Length: 16\r\n");
            writer.write("Content-Type: text/plain; charset=ISO-8859-1\r\n");
            writer.write("Connection: close\r\n");
            writer.write("\r\n");
            writer.write("booo booo booo\r\n");
            writer.flush();
            final InputStream instream = socket.getInputStream();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(instream, "US-ASCII"));
            final StringBuilder buf = new StringBuilder();
            final char[] tmp = new char[1024];
            int l;
            while ((l = reader.read(tmp)) != -1) {
                buf.append(tmp, 0, l);
            }
            reader.close();
            writer.close();
            final String expected = "HTTP/1.1 200 OK\r\n" +
                    "Server: TEST-SERVER/1.1\r\n" +
                    "Content-Length: 16\r\n" +
                    "Content-Type: text/plain; charset=ISO-8859-1\r\n" +
                    "\r\n" +
                    "blah blah blah\r\n" +
                    "HTTP/1.1 200 OK\r\n" +
                    "Server: TEST-SERVER/1.1\r\n" +
                    "Content-Length: 16\r\n" +
                    "Content-Type: text/plain; charset=ISO-8859-1\r\n" +
                    "\r\n" +
                    "yada yada yada\r\n" +
                    "HTTP/1.1 200 OK\r\n" +
                    "Server: TEST-SERVER/1.1\r\n" +
                    "Content-Length: 16\r\n" +
                    "Content-Type: text/plain; charset=ISO-8859-1\r\n" +
                    "Connection: close\r\n" +
                    "\r\n" +
                    "booo booo booo\r\n";
            Assert.assertEquals(expected, buf.toString());

        }
    }

}

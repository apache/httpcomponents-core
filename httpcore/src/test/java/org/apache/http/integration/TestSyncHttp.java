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

package org.apache.http.integration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.http.Header;
import org.apache.http.HttpConnectionMetrics;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultHttpClientConnection;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExpectationVerifier;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.testserver.HttpClient;
import org.apache.http.testserver.HttpServer;
import org.apache.http.util.EncodingUtils;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestSyncHttp {

    private HttpServer server;
    private HttpClient client;

    @Before
    public void initServer() throws Exception {
        this.server = new HttpServer();
    }

    @Before
    public void initClient() throws Exception {
        this.client = new HttpClient();
    }

    @After
    public void shutDownServer() throws Exception {
        if (this.server != null) {
            this.server.shutdown();
        }
    }

    /**
     * This test case executes a series of simple GET requests
     */
    @Test
    public void testSimpleBasicHttpRequests() throws Exception {

        int reqNo = 20;

        Random rnd = new Random();

        // Prepare some random data
        final List<byte[]> testData = new ArrayList<byte[]>(reqNo);
        for (int i = 0; i < reqNo; i++) {
            int size = rnd.nextInt(5000);
            byte[] data = new byte[size];
            rnd.nextBytes(data);
            testData.add(data);
        }

        // Initialize the server-side request handler
        this.server.registerHandler("*", new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                String s = request.getRequestLine().getUri();
                if (s.startsWith("/?")) {
                    s = s.substring(2);
                }
                int index = Integer.parseInt(s);
                byte[] data = testData.get(index);
                ByteArrayEntity entity = new ByteArrayEntity(data);
                response.setEntity(entity);
            }

        });

        this.server.start();

        DefaultHttpClientConnection conn = new DefaultHttpClientConnection();
        HttpHost host = new HttpHost("localhost", this.server.getPort());

        try {
            for (int r = 0; r < reqNo; r++) {
                if (!conn.isOpen()) {
                    Socket socket = new Socket(host.getHostName(), host.getPort());
                    conn.bind(socket, this.client.getParams());
                }

                BasicHttpRequest get = new BasicHttpRequest("GET", "/?" + r);
                HttpResponse response = this.client.execute(get, host, conn);
                byte[] received = EntityUtils.toByteArray(response.getEntity());
                byte[] expected = testData.get(r);

                Assert.assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    Assert.assertEquals(expected[i], received[i]);
                }
                if (!this.client.keepAlive(response)) {
                    conn.close();
                }
            }

            //Verify the connection metrics
            HttpConnectionMetrics cm = conn.getMetrics();
            Assert.assertEquals(reqNo, cm.getRequestCount());
            Assert.assertEquals(reqNo, cm.getResponseCount());

        } finally {
            conn.close();
            this.server.shutdown();
        }
    }

    /**
     * This test case executes a series of simple POST requests with content length
     * delimited content.
     */
    @Test
    public void testSimpleHttpPostsWithContentLength() throws Exception {

        int reqNo = 20;

        Random rnd = new Random();

        // Prepare some random data
        List<byte[]> testData = new ArrayList<byte[]>(reqNo);
        for (int i = 0; i < reqNo; i++) {
            int size = rnd.nextInt(5000);
            byte[] data = new byte[size];
            rnd.nextBytes(data);
            testData.add(data);
        }

        // Initialize the server-side request handler
        this.server.registerHandler("*", new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                if (request instanceof HttpEntityEnclosingRequest) {
                    HttpEntity incoming = ((HttpEntityEnclosingRequest) request).getEntity();
                    byte[] data = EntityUtils.toByteArray(incoming);

                    ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    outgoing.setChunked(false);
                    response.setEntity(outgoing);
                } else {
                    StringEntity outgoing = new StringEntity("No content");
                    response.setEntity(outgoing);
                }
            }

        });

        this.server.start();

        DefaultHttpClientConnection conn = new DefaultHttpClientConnection();
        HttpHost host = new HttpHost("localhost", this.server.getPort());

        try {
            for (int r = 0; r < reqNo; r++) {
                if (!conn.isOpen()) {
                    Socket socket = new Socket(host.getHostName(), host.getPort());
                    conn.bind(socket, this.client.getParams());
                }

                BasicHttpEntityEnclosingRequest post = new BasicHttpEntityEnclosingRequest("POST", "/");
                byte[] data = testData.get(r);
                ByteArrayEntity outgoing = new ByteArrayEntity(data);
                post.setEntity(outgoing);

                HttpResponse response = this.client.execute(post, host, conn);
                byte[] received = EntityUtils.toByteArray(response.getEntity());
                byte[] expected = testData.get(r);

                Assert.assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    Assert.assertEquals(expected[i], received[i]);
                }
                if (!this.client.keepAlive(response)) {
                    conn.close();
                }
            }
            //Verify the connection metrics
            HttpConnectionMetrics cm = conn.getMetrics();
            Assert.assertEquals(reqNo, cm.getRequestCount());
            Assert.assertEquals(reqNo, cm.getResponseCount());

        } finally {
            conn.close();
            this.server.shutdown();
        }
    }

    /**
     * This test case executes a series of simple POST requests with chunk
     * coded content content.
     */
    @Test
    public void testSimpleHttpPostsChunked() throws Exception {

        int reqNo = 20;

        Random rnd = new Random();

        // Prepare some random data
        List<byte[]> testData = new ArrayList<byte[]>(reqNo);
        for (int i = 0; i < reqNo; i++) {
            int size = rnd.nextInt(20000);
            byte[] data = new byte[size];
            rnd.nextBytes(data);
            testData.add(data);
        }

        // Initialize the server-side request handler
        this.server.registerHandler("*", new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                if (request instanceof HttpEntityEnclosingRequest) {
                    HttpEntity incoming = ((HttpEntityEnclosingRequest) request).getEntity();
                    byte[] data = EntityUtils.toByteArray(incoming);

                    ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    outgoing.setChunked(true);
                    response.setEntity(outgoing);
                } else {
                    StringEntity outgoing = new StringEntity("No content");
                    response.setEntity(outgoing);
                }
            }

        });

        this.server.start();

        DefaultHttpClientConnection conn = new DefaultHttpClientConnection();
        HttpHost host = new HttpHost("localhost", this.server.getPort());

        try {
            for (int r = 0; r < reqNo; r++) {
                if (!conn.isOpen()) {
                    Socket socket = new Socket(host.getHostName(), host.getPort());
                    conn.bind(socket, this.client.getParams());
                }

                BasicHttpEntityEnclosingRequest post = new BasicHttpEntityEnclosingRequest("POST", "/");
                byte[] data = testData.get(r);
                ByteArrayEntity outgoing = new ByteArrayEntity(data);
                outgoing.setChunked(true);
                post.setEntity(outgoing);

                HttpResponse response = this.client.execute(post, host, conn);
                byte[] received = EntityUtils.toByteArray(response.getEntity());
                byte[] expected = testData.get(r);

                Assert.assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    Assert.assertEquals(expected[i], received[i]);
                }
                if (!this.client.keepAlive(response)) {
                    conn.close();
                }
            }
            //Verify the connection metrics
            HttpConnectionMetrics cm = conn.getMetrics();
            Assert.assertEquals(reqNo, cm.getRequestCount());
            Assert.assertEquals(reqNo, cm.getResponseCount());
        } finally {
            conn.close();
            this.server.shutdown();
        }
    }

    /**
     * This test case executes a series of simple HTTP/1.0 POST requests.
     */
    @Test
    public void testSimpleHttpPostsHTTP10() throws Exception {

        int reqNo = 20;

        Random rnd = new Random();

        // Prepare some random data
        List<byte[]> testData = new ArrayList<byte[]>(reqNo);
        for (int i = 0; i < reqNo; i++) {
            int size = rnd.nextInt(5000);
            byte[] data = new byte[size];
            rnd.nextBytes(data);
            testData.add(data);
        }

        // Initialize the server-side request handler
        this.server.registerHandler("*", new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                if (request instanceof HttpEntityEnclosingRequest) {
                    HttpEntity incoming = ((HttpEntityEnclosingRequest) request).getEntity();
                    byte[] data = EntityUtils.toByteArray(incoming);

                    ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    outgoing.setChunked(false);
                    response.setEntity(outgoing);
                } else {
                    StringEntity outgoing = new StringEntity("No content");
                    response.setEntity(outgoing);
                }
            }

        });

        this.server.start();

        // Set protocol level to HTTP/1.0
        this.client.getParams().setParameter(
                CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_0);

        DefaultHttpClientConnection conn = new DefaultHttpClientConnection();
        HttpHost host = new HttpHost("localhost", this.server.getPort());

        try {
            for (int r = 0; r < reqNo; r++) {
                if (!conn.isOpen()) {
                    Socket socket = new Socket(host.getHostName(), host.getPort());
                    conn.bind(socket, this.client.getParams());
                }

                BasicHttpEntityEnclosingRequest post = new BasicHttpEntityEnclosingRequest("POST", "/");
                byte[] data = testData.get(r);
                ByteArrayEntity outgoing = new ByteArrayEntity(data);
                post.setEntity(outgoing);

                HttpResponse response = this.client.execute(post, host, conn);
                Assert.assertEquals(HttpVersion.HTTP_1_1, response.getStatusLine().getProtocolVersion());
                byte[] received = EntityUtils.toByteArray(response.getEntity());
                byte[] expected = testData.get(r);

                Assert.assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    Assert.assertEquals(expected[i], received[i]);
                }
                if (!this.client.keepAlive(response)) {
                    conn.close();
                }
            }

            //Verify the connection metrics
            HttpConnectionMetrics cm = conn.getMetrics();
            Assert.assertEquals(reqNo, cm.getRequestCount());
            Assert.assertEquals(reqNo, cm.getResponseCount());
        } finally {
            conn.close();
            this.server.shutdown();
        }
    }

    /**
     * This test case executes a series of simple POST requests using
     * the 'expect: continue' handshake.
     */
    @Test
    public void testHttpPostsWithExpectContinue() throws Exception {

        int reqNo = 20;

        Random rnd = new Random();

        // Prepare some random data
        List<byte[]> testData = new ArrayList<byte[]>(reqNo);
        for (int i = 0; i < reqNo; i++) {
            int size = rnd.nextInt(5000);
            byte[] data = new byte[size];
            rnd.nextBytes(data);
            testData.add(data);
        }

        // Initialize the server-side request handler
        this.server.registerHandler("*", new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                if (request instanceof HttpEntityEnclosingRequest) {
                    HttpEntity incoming = ((HttpEntityEnclosingRequest) request).getEntity();
                    byte[] data = EntityUtils.toByteArray(incoming);

                    ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    outgoing.setChunked(true);
                    response.setEntity(outgoing);
                } else {
                    StringEntity outgoing = new StringEntity("No content");
                    response.setEntity(outgoing);
                }
            }

        });

        this.server.start();

        // Activate 'expect: continue' handshake
        this.client.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, true);

        DefaultHttpClientConnection conn = new DefaultHttpClientConnection();
        HttpHost host = new HttpHost("localhost", this.server.getPort());

        try {
            for (int r = 0; r < reqNo; r++) {
                if (!conn.isOpen()) {
                    Socket socket = new Socket(host.getHostName(), host.getPort());
                    conn.bind(socket, this.client.getParams());
                }

                BasicHttpEntityEnclosingRequest post = new BasicHttpEntityEnclosingRequest("POST", "/");
                byte[] data = testData.get(r);
                ByteArrayEntity outgoing = new ByteArrayEntity(data);
                outgoing.setChunked(true);
                post.setEntity(outgoing);

                HttpResponse response = this.client.execute(post, host, conn);
                byte[] received = EntityUtils.toByteArray(response.getEntity());
                byte[] expected = testData.get(r);

                Assert.assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    Assert.assertEquals(expected[i], received[i]);
                }
                if (!this.client.keepAlive(response)) {
                    conn.close();
                }
            }

            //Verify the connection metrics
            HttpConnectionMetrics cm = conn.getMetrics();
            Assert.assertEquals(reqNo, cm.getRequestCount());
            Assert.assertEquals(reqNo, cm.getResponseCount());
        } finally {
            conn.close();
            this.server.shutdown();
        }
    }


    /**
     * This test case executes a series of simple POST requests that do not
     * meet the target server expectations.
     */
    @Test
    public void testHttpPostsWithExpectationVerification() throws Exception {

        int reqNo = 3;

        // Initialize the server-side request handler
        this.server.registerHandler("*", new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                StringEntity outgoing = new StringEntity("No content");
                response.setEntity(outgoing);
            }

        });

        this.server.setExpectationVerifier(new HttpExpectationVerifier() {

            public void verify(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException {
                Header someheader = request.getFirstHeader("Secret");
                if (someheader != null) {
                    int secretNumber;
                    try {
                        secretNumber = Integer.parseInt(someheader.getValue());
                    } catch (NumberFormatException ex) {
                        response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
                        return;
                    }
                    if (secretNumber < 2) {
                        response.setStatusCode(HttpStatus.SC_EXPECTATION_FAILED);
                        ByteArrayEntity outgoing = new ByteArrayEntity(
                                EncodingUtils.getAsciiBytes("Wrong secret number"));
                        response.setEntity(outgoing);
                    }
                }
            }

        });

        this.server.start();

        // Activate 'expect: continue' handshake
        this.client.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, true);

        DefaultHttpClientConnection conn = new DefaultHttpClientConnection();
        HttpHost host = new HttpHost("localhost", this.server.getPort());

        try {
            for (int r = 0; r < reqNo; r++) {
                if (!conn.isOpen()) {
                    Socket socket = new Socket(host.getHostName(), host.getPort());
                    conn.bind(socket, this.client.getParams());
                }

                BasicHttpEntityEnclosingRequest post = new BasicHttpEntityEnclosingRequest("POST", "/");
                post.addHeader("Secret", Integer.toString(r));
                ByteArrayEntity outgoing = new ByteArrayEntity(
                        EncodingUtils.getAsciiBytes("No content"));
                post.setEntity(outgoing);

                HttpResponse response = this.client.execute(post, host, conn);

                HttpEntity entity = response.getEntity();
                Assert.assertNotNull(entity);
                EntityUtils.consume(entity);

                if (r < 2) {
                    Assert.assertEquals(HttpStatus.SC_EXPECTATION_FAILED, response.getStatusLine().getStatusCode());
                } else {
                    Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
                }

                if (!this.client.keepAlive(response)) {
                    conn.close();
                }
            }
            //Verify the connection metrics
            HttpConnectionMetrics cm = conn.getMetrics();
            Assert.assertEquals(reqNo, cm.getRequestCount());
            Assert.assertEquals(reqNo, cm.getResponseCount());
        } finally {
            conn.close();
            this.server.shutdown();
        }
    }

    static class RepeatingEntity extends AbstractHttpEntity {

        private final byte[] raw;
        private int n;

        public RepeatingEntity(final String content, Charset charset, int n) {
            super();
            if (content == null) {
                throw new IllegalArgumentException("Content may not be null");
            }
            if (n <= 0) {
                throw new IllegalArgumentException("N may not be negative or zero");
            }
            if (charset == null) {
                charset = Charset.forName("US-ASCII"); // US-ASCII is built-in
            }
            byte[] b;
            // Java 6 only:
            // b = content.getBytes(charset);
            // Java 5 OK:
            try {
                b = content.getBytes(charset.name());
            } catch (UnsupportedEncodingException ex) {
                b = content.getBytes();
            }
            this.raw = b;
            this.n = n;
        }

        public InputStream getContent() throws IOException, IllegalStateException {
            throw new IllegalStateException("This method is not implemented");
        }

        public long getContentLength() {
            return (this.raw.length + 2) * this.n;
        }

        public boolean isRepeatable() {
            return true;
        }

        public boolean isStreaming() {
            return false;
        }

        public void writeTo(final OutputStream outstream) throws IOException {
            for (int i = 0; i < this.n; i++) {
                outstream.write(this.raw);
                outstream.write('\r');
                outstream.write('\n');
            }
            outstream.flush();
        }

    }

    @Test
    public void testHttpContent() throws Exception {

        String[] patterns = {

            "0123456789ABCDEF",
            "yadayada-blahblah-this-and-that-yadayada-blahblah-this-and-that-" +
            "yadayada-blahblah-this-and-that-yadayada-blahblah-this-and-that-" +
            "yadayada-blahblah-this-and-that-yadayada-blahblah-this-and-that-" +
            "yadayada-blahblah-this-and-that-yadayada-blahblah-this-and-that-" +
            "yadayada-blahblah-this-and-that-yadayada-blahblah-this-and-that-" +
            "yadayada-blahblah-this-and-that-yadayada-blahblah-this-and-that-" +
            "yadayada-blahblah-this-and-that-yadayada-blahblah-this-and-that-" +
            "yadayada-blahblah-this-and-that-yadayada-blahblah-this-and-that-" +
            "yadayada-blahblah-this-and-that-yadayada-blahblah-this-and-that-" +
            "yadayada-blahblah-this-and-that-yadayada-blahblah-this-and-that-" +
            "yadayada-blahblah-this-and-that-yadayada-blahblah-this-and-that-" +
            "yadayada-blahblah-this-and-that-yadayada-blahblah-this-and-that-" +
            "yadayada-blahblah-this-and-that-yadayada-blahblah-this-and-that-" +
            "yadayada-blahblah-this-and-that-yadayada-blahblah-this-and-that-" +
            "yadayada-blahblah-this-and-that-yadayada-blahblah-this-and-that"
        };

        // Initialize the server-side request handler
        this.server.registerHandler("*", new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                if (request instanceof HttpEntityEnclosingRequest) {
                    int n = 1;
                    String s = request.getRequestLine().getUri();
                    if (s.startsWith("/?n=")) {
                        s = s.substring(4);
                        try {
                            n = Integer.parseInt(s);
                            if (n <= 0) {
                                throw new HttpException("Invalid request: " +
                                        "number of repetitions cannot be negative or zero");
                            }
                        } catch (NumberFormatException ex) {
                            throw new HttpException("Invalid request: " +
                                    "number of repetitions is invalid");
                        }
                    }

                    HttpEntity incoming = ((HttpEntityEnclosingRequest) request).getEntity();
                    String line = EntityUtils.toString(incoming);
                    ContentType contentType = ContentType.getOrDefault(incoming);
                    Charset charset = contentType.getCharset();
                    if (charset == null) {
                        charset = HTTP.DEF_CONTENT_CHARSET;
                    }
                    RepeatingEntity outgoing = new RepeatingEntity(line, charset, n);
                    outgoing.setChunked(n % 2 == 0);
                    response.setEntity(outgoing);
                } else {
                    throw new HttpException("Invalid request: POST request expected");
                }
            }

        });

        this.server.start();
        DefaultHttpClientConnection conn = new DefaultHttpClientConnection();
        HttpHost host = new HttpHost("localhost", this.server.getPort());

        try {
            for (int i = 0; i < patterns.length; i++) {
                String pattern = patterns[i];
                for (int n = 1000; n < 1020; n++) {
                    if (!conn.isOpen()) {
                        Socket socket = new Socket(host.getHostName(), host.getPort());
                        conn.bind(socket, this.client.getParams());
                    }

                    BasicHttpEntityEnclosingRequest post = new BasicHttpEntityEnclosingRequest(
                            "POST", "/?n=" + n);
                    StringEntity outgoing = new StringEntity(pattern);
                    outgoing.setChunked(n % 2 == 0);
                    post.setEntity(outgoing);

                    HttpResponse response = this.client.execute(post, host, conn);
                    HttpEntity incoming = response.getEntity();
                    Assert.assertNotNull(incoming);
                    InputStream instream = incoming.getContent();
                    ContentType contentType = ContentType.getOrDefault(incoming);
                    Charset charset = contentType.getCharset();
                    if (charset == null) {
                        charset = HTTP.DEF_CONTENT_CHARSET;
                    }
                    Assert.assertNotNull(instream);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(instream, charset));

                    String line;
                    int count = 0;
                    while ((line = reader.readLine()) != null) {
                        Assert.assertEquals(pattern, line);
                        count++;
                    }
                    Assert.assertEquals(n, count);
                    if (!this.client.keepAlive(response)) {
                        conn.close();
                    }
                }
            }
        } finally {
            conn.close();
            this.server.shutdown();
        }
    }

    @Test
    public void testHttpPostNoEntity() throws Exception {
        this.server.registerHandler("*", new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                if (request instanceof HttpEntityEnclosingRequest) {
                    HttpEntity incoming = ((HttpEntityEnclosingRequest) request).getEntity();
                    byte[] data = EntityUtils.toByteArray(incoming);
                    ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    response.setEntity(outgoing);
                } else {
                    StringEntity outgoing = new StringEntity("No content");
                    response.setEntity(outgoing);
                }
            }

        });

        this.server.start();

        DefaultHttpClientConnection conn = new DefaultHttpClientConnection();
        HttpHost host = new HttpHost("localhost", this.server.getPort());

        try {
            if (!conn.isOpen()) {
                Socket socket = new Socket(host.getHostName(), host.getPort());
                conn.bind(socket, this.client.getParams());
            }

            BasicHttpEntityEnclosingRequest post = new BasicHttpEntityEnclosingRequest("POST", "/");
            post.setEntity(null);

            HttpResponse response = this.client.execute(post, host, conn);
            Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
            byte[] received = EntityUtils.toByteArray(response.getEntity());
            Assert.assertEquals(0, received.length);
        } finally {
            conn.close();
            this.server.shutdown();
        }
    }

    @Test
    public void testHttpPostNoContentLength() throws Exception {
        this.server.registerHandler("*", new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                if (request instanceof HttpEntityEnclosingRequest) {
                    HttpEntity incoming = ((HttpEntityEnclosingRequest) request).getEntity();
                    byte[] data = EntityUtils.toByteArray(incoming);
                    ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    response.setEntity(outgoing);
                } else {
                    StringEntity outgoing = new StringEntity("No content");
                    response.setEntity(outgoing);
                }
            }

        });

        this.server.start();

        DefaultHttpClientConnection conn = new DefaultHttpClientConnection();
        HttpHost host = new HttpHost("localhost", this.server.getPort());

        try {
            if (!conn.isOpen()) {
                Socket socket = new Socket(host.getHostName(), host.getPort());
                conn.bind(socket, this.client.getParams());
            }

            BasicHttpEntityEnclosingRequest post = new BasicHttpEntityEnclosingRequest("POST", "/");
            post.setEntity(null);

            this.client = new HttpClient(new ImmutableHttpProcessor(
                    new HttpRequestInterceptor[] {
                            new RequestTargetHost(),
                            new RequestConnControl(),
                            new RequestUserAgent(),
                            new RequestExpectContinue() }));
            
            HttpResponse response = this.client.execute(post, host, conn);
            Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
            byte[] received = EntityUtils.toByteArray(response.getEntity());
            Assert.assertEquals(0, received.length);
        } finally {
            conn.close();
            this.server.shutdown();
        }
    }

    @Test
    public void testHttpPostIdentity() throws Exception {
        this.server.registerHandler("*", new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                if (request instanceof HttpEntityEnclosingRequest) {
                    HttpEntity incoming = ((HttpEntityEnclosingRequest) request).getEntity();
                    byte[] data = EntityUtils.toByteArray(incoming);
                    ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    response.setEntity(outgoing);
                } else {
                    StringEntity outgoing = new StringEntity("No content");
                    response.setEntity(outgoing);
                }
            }

        });

        this.server.start();

        DefaultHttpClientConnection conn = new DefaultHttpClientConnection();
        HttpHost host = new HttpHost("localhost", this.server.getPort());

        try {
            if (!conn.isOpen()) {
                Socket socket = new Socket(host.getHostName(), host.getPort());
                conn.bind(socket, this.client.getParams());
            }

            BasicHttpEntityEnclosingRequest post = new BasicHttpEntityEnclosingRequest("POST", "/");
            post.setEntity(null);

            this.client = new HttpClient(new ImmutableHttpProcessor(
                    new HttpRequestInterceptor[] {
                            new HttpRequestInterceptor() {
                                
                                public void process(
                                        final HttpRequest request, 
                                        final HttpContext context) throws HttpException, IOException {
                                    request.addHeader(HTTP.TRANSFER_ENCODING, "identity");
                                }

                            },
                            new RequestTargetHost(),
                            new RequestConnControl(),
                            new RequestUserAgent(),
                            new RequestExpectContinue() }));
            
            HttpResponse response = this.client.execute(post, host, conn);
            Assert.assertEquals(HttpStatus.SC_BAD_REQUEST, response.getStatusLine().getStatusCode());
        } finally {
            conn.close();
            this.server.shutdown();
        }
    }

    @Test
    public void testNoContentResponse() throws Exception {

        int reqNo = 20;

        // Initialize the server-side request handler
        this.server.registerHandler("*", new HttpRequestHandler() {

            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                response.setStatusCode(HttpStatus.SC_NO_CONTENT);
            }

        });

        this.server.start();

        DefaultHttpClientConnection conn = new DefaultHttpClientConnection();
        HttpHost host = new HttpHost("localhost", this.server.getPort());

        try {
            for (int r = 0; r < reqNo; r++) {
                if (!conn.isOpen()) {
                    Socket socket = new Socket(host.getHostName(), host.getPort());
                    conn.bind(socket, this.client.getParams());
                }

                BasicHttpRequest get = new BasicHttpRequest("GET", "/?" + r);
                HttpResponse response = this.client.execute(get, host, conn);
                Assert.assertNull(response.getEntity());
                if (!this.client.keepAlive(response)) {
                    conn.close();
                    Assert.fail("Connection expected to be re-usable");
                }
            }

            //Verify the connection metrics
            HttpConnectionMetrics cm = conn.getMetrics();
            Assert.assertEquals(reqNo, cm.getRequestCount());
            Assert.assertEquals(reqNo, cm.getResponseCount());

        } finally {
            conn.close();
            this.server.shutdown();
        }
    }

}

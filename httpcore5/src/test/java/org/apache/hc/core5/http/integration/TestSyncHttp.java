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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnectionMetrics;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.entity.AbstractHttpEntity;
import org.apache.hc.core5.http.entity.ByteArrayEntity;
import org.apache.hc.core5.http.entity.ContentType;
import org.apache.hc.core5.http.entity.EntityUtils;
import org.apache.hc.core5.http.entity.StringEntity;
import org.apache.hc.core5.http.impl.io.DefaultBHttpClientConnection;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpExpectationVerifier;
import org.apache.hc.core5.http.protocol.ImmutableHttpProcessor;
import org.apache.hc.core5.http.protocol.RequestConnControl;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.http.protocol.RequestExpectContinue;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.apache.hc.core5.http.testserver.io.HttpClient;
import org.apache.hc.core5.http.testserver.io.HttpServer;
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
        this.server.setTimeout(5000);
    }

    @Before
    public void initClient() throws Exception {
        this.client = new HttpClient();
        this.client.setTimeout(5000);
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

        final int reqNo = 20;

        final Random rnd = new Random();

        // Prepare some random data
        final List<byte[]> testData = new ArrayList<>(reqNo);
        for (int i = 0; i < reqNo; i++) {
            final int size = rnd.nextInt(5000);
            final byte[] data = new byte[size];
            rnd.nextBytes(data);
            testData.add(data);
        }

        // Initialize the server-side request handler
        this.server.registerHandler("*", new HttpRequestHandler() {

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                String s = request.getRequestLine().getUri();
                if (s.startsWith("/?")) {
                    s = s.substring(2);
                }
                final int index = Integer.parseInt(s);
                final byte[] data = testData.get(index);
                final ByteArrayEntity entity = new ByteArrayEntity(data);
                response.setEntity(entity);
            }

        });

        this.server.start();

        final DefaultBHttpClientConnection conn = client.createConnection();
        final HttpHost host = new HttpHost("localhost", this.server.getPort());

        try {
            for (int r = 0; r < reqNo; r++) {
                if (!conn.isOpen()) {
                    client.connect(host, conn);
                }

                final BasicHttpRequest get = new BasicHttpRequest("GET", "/?" + r);
                final HttpResponse response = this.client.execute(get, host, conn);
                final byte[] received = EntityUtils.toByteArray(response.getEntity());
                final byte[] expected = testData.get(r);

                Assert.assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    Assert.assertEquals(expected[i], received[i]);
                }
                if (!this.client.keepAlive(get, response)) {
                    conn.close();
                }
            }

            //Verify the connection metrics
            final HttpConnectionMetrics cm = conn.getMetrics();
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

        final int reqNo = 20;

        final Random rnd = new Random();

        // Prepare some random data
        final List<byte[]> testData = new ArrayList<>(reqNo);
        for (int i = 0; i < reqNo; i++) {
            final int size = rnd.nextInt(5000);
            final byte[] data = new byte[size];
            rnd.nextBytes(data);
            testData.add(data);
        }

        // Initialize the server-side request handler
        this.server.registerHandler("*", new HttpRequestHandler() {

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                final HttpEntity incoming = request.getEntity();
                if (incoming != null) {
                    final byte[] data = EntityUtils.toByteArray(incoming);

                    final ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    outgoing.setChunked(false);
                    response.setEntity(outgoing);
                }
            }

        });

        this.server.start();

        final DefaultBHttpClientConnection conn = client.createConnection();
        final HttpHost host = new HttpHost("localhost", this.server.getPort());

        try {
            for (int r = 0; r < reqNo; r++) {
                if (!conn.isOpen()) {
                    client.connect(host, conn);
                }

                final BasicHttpRequest post = new BasicHttpRequest("POST", "/");
                final byte[] data = testData.get(r);
                final ByteArrayEntity outgoing = new ByteArrayEntity(data);
                post.setEntity(outgoing);

                final HttpResponse response = this.client.execute(post, host, conn);
                final byte[] received = EntityUtils.toByteArray(response.getEntity());
                final byte[] expected = testData.get(r);

                Assert.assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    Assert.assertEquals(expected[i], received[i]);
                }
                if (!this.client.keepAlive(post, response)) {
                    conn.close();
                }
            }
            //Verify the connection metrics
            final HttpConnectionMetrics cm = conn.getMetrics();
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

        final int reqNo = 20;

        final Random rnd = new Random();

        // Prepare some random data
        final List<byte[]> testData = new ArrayList<>(reqNo);
        for (int i = 0; i < reqNo; i++) {
            final int size = rnd.nextInt(20000);
            final byte[] data = new byte[size];
            rnd.nextBytes(data);
            testData.add(data);
        }

        // Initialize the server-side request handler
        this.server.registerHandler("*", new HttpRequestHandler() {

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                final HttpEntity entity = request.getEntity();
                if (entity != null) {
                    final byte[] data = EntityUtils.toByteArray(entity);
                    final ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    outgoing.setChunked(true);
                    response.setEntity(outgoing);
                }
            }

        });

        this.server.start();

        final DefaultBHttpClientConnection conn = client.createConnection();
        final HttpHost host = new HttpHost("localhost", this.server.getPort());

        try {
            for (int r = 0; r < reqNo; r++) {
                if (!conn.isOpen()) {
                    client.connect(host, conn);
                }

                final BasicHttpRequest post = new BasicHttpRequest("POST", "/");
                final byte[] data = testData.get(r);
                final ByteArrayEntity outgoing = new ByteArrayEntity(data);
                outgoing.setChunked(true);
                post.setEntity(outgoing);

                final HttpResponse response = this.client.execute(post, host, conn);
                final byte[] received = EntityUtils.toByteArray(response.getEntity());
                final byte[] expected = testData.get(r);

                Assert.assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    Assert.assertEquals(expected[i], received[i]);
                }
                if (!this.client.keepAlive(post, response)) {
                    conn.close();
                }
            }
            //Verify the connection metrics
            final HttpConnectionMetrics cm = conn.getMetrics();
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

        final int reqNo = 20;

        final Random rnd = new Random();

        // Prepare some random data
        final List<byte[]> testData = new ArrayList<>(reqNo);
        for (int i = 0; i < reqNo; i++) {
            final int size = rnd.nextInt(5000);
            final byte[] data = new byte[size];
            rnd.nextBytes(data);
            testData.add(data);
        }

        // Initialize the server-side request handler
        this.server.registerHandler("*", new HttpRequestHandler() {

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                final HttpEntity incoming = request.getEntity();
                if (incoming != null) {
                    final byte[] data = EntityUtils.toByteArray(incoming);

                    final ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    outgoing.setChunked(false);
                    response.setEntity(outgoing);
                }
            }

        });

        this.server.start();

        final DefaultBHttpClientConnection conn = client.createConnection();
        final HttpHost host = new HttpHost("localhost", this.server.getPort());

        try {
            for (int r = 0; r < reqNo; r++) {
                if (!conn.isOpen()) {
                    client.connect(host, conn);
                }

                // Set protocol level to HTTP/1.0
                final BasicHttpRequest post = new BasicHttpRequest(
                        "POST", "/", HttpVersion.HTTP_1_0);
                final byte[] data = testData.get(r);
                final ByteArrayEntity outgoing = new ByteArrayEntity(data);
                post.setEntity(outgoing);

                final HttpResponse response = this.client.execute(post, host, conn);
                Assert.assertEquals(HttpVersion.HTTP_1_1, response.getStatusLine().getProtocolVersion());
                final byte[] received = EntityUtils.toByteArray(response.getEntity());
                final byte[] expected = testData.get(r);

                Assert.assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    Assert.assertEquals(expected[i], received[i]);
                }
                if (!this.client.keepAlive(post, response)) {
                    conn.close();
                }
            }

            //Verify the connection metrics
            final HttpConnectionMetrics cm = conn.getMetrics();
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

        final int reqNo = 20;

        final Random rnd = new Random();

        // Prepare some random data
        final List<byte[]> testData = new ArrayList<>(reqNo);
        for (int i = 0; i < reqNo; i++) {
            final int size = rnd.nextInt(5000);
            final byte[] data = new byte[size];
            rnd.nextBytes(data);
            testData.add(data);
        }

        // Initialize the server-side request handler
        this.server.registerHandler("*", new HttpRequestHandler() {

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                final HttpEntity incoming = request.getEntity();
                if (incoming != null) {
                    final byte[] data = EntityUtils.toByteArray(incoming);

                    final ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    outgoing.setChunked(true);
                    response.setEntity(outgoing);
                }
            }

        });

        this.server.start();

        // Activate 'expect: continue' handshake
        final DefaultBHttpClientConnection conn = client.createConnection();
        final HttpHost host = new HttpHost("localhost", this.server.getPort());

        try {
            for (int r = 0; r < reqNo; r++) {
                if (!conn.isOpen()) {
                    client.connect(host, conn);
                }

                final BasicHttpRequest post = new BasicHttpRequest("POST", "/");
                final byte[] data = testData.get(r);
                final ByteArrayEntity outgoing = new ByteArrayEntity(data);
                outgoing.setChunked(true);
                post.setEntity(outgoing);

                final HttpResponse response = this.client.execute(post, host, conn);
                final byte[] received = EntityUtils.toByteArray(response.getEntity());
                final byte[] expected = testData.get(r);

                Assert.assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    Assert.assertEquals(expected[i], received[i]);
                }
                if (!this.client.keepAlive(post, response)) {
                    conn.close();
                }
            }

            //Verify the connection metrics
            final HttpConnectionMetrics cm = conn.getMetrics();
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

        final int reqNo = 20;

        // Initialize the server-side request handler
        this.server.registerHandler("*", new HttpRequestHandler() {

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                final StringEntity outgoing = new StringEntity("No content");
                response.setEntity(outgoing);
            }

        });

        this.server.setExpectationVerifier(new HttpExpectationVerifier() {

            @Override
            public void verify(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException {
                final Header someheader = request.getFirstHeader("Secret");
                if (someheader != null) {
                    final int secretNumber;
                    try {
                        secretNumber = Integer.parseInt(someheader.getValue());
                    } catch (final NumberFormatException ex) {
                        response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
                        return;
                    }
                    if (secretNumber >= 2) {
                        response.setStatusCode(HttpStatus.SC_EXPECTATION_FAILED);
                        response.setEntity(
                                new StringEntity("Wrong secret number", ContentType.TEXT_PLAIN));
                    }
                }
            }

        });

        this.server.start();

        final DefaultBHttpClientConnection conn = client.createConnection();
        final HttpHost host = new HttpHost("localhost", this.server.getPort());

        try {
            for (int r = 0; r < reqNo; r++) {
                if (!conn.isOpen()) {
                    client.connect(host, conn);
                }

                final BasicHttpRequest post = new BasicHttpRequest("POST", "/");
                post.addHeader("Secret", Integer.toString(r));

                final byte[] b = new byte[2048];
                for (int i = 0; i < b.length; i++) {
                    b[i] = (byte) ('a' + r);
                }
                final ByteArrayEntity requestEntity = new ByteArrayEntity(b, ContentType.TEXT_PLAIN);
                requestEntity.setChunked(false);
                post.setEntity(requestEntity);

                final HttpResponse response = this.client.execute(post, host, conn);

                final HttpEntity responseEntity = response.getEntity();
                Assert.assertNotNull(responseEntity);
                EntityUtils.consume(responseEntity);

                if (r >= 2) {
                    Assert.assertEquals(HttpStatus.SC_EXPECTATION_FAILED, response.getCode());
                } else {
                    Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
                }

                if (!conn.isConsistent() || !this.client.keepAlive(post, response)) {
                    conn.close();
                }
            }
            //Verify the connection metrics
            final HttpConnectionMetrics cm = conn.getMetrics();
            Assert.assertEquals(reqNo, cm.getRequestCount());
            Assert.assertEquals(reqNo, cm.getResponseCount());
        } finally {
            conn.close();
            this.server.shutdown();
        }
    }

    static class RepeatingEntity extends AbstractHttpEntity {

        private final byte[] raw;
        private final int n;

        public RepeatingEntity(final String content, final Charset charset, final int n) {
            super();
            final Charset cs = charset != null ? charset : Charset.forName("US-ASCII");
            this.raw = content.getBytes(cs);
            this.n = n;
        }

        @Override
        public InputStream getContent() throws IOException, IllegalStateException {
            throw new IllegalStateException("This method is not implemented");
        }

        @Override
        public long getContentLength() {
            return (this.raw.length + 2) * this.n;
        }

        @Override
        public boolean isRepeatable() {
            return true;
        }

        @Override
        public boolean isStreaming() {
            return false;
        }

        @Override
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

        final String[] patterns = {

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

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                if (request instanceof HttpRequest) {
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
                        } catch (final NumberFormatException ex) {
                            throw new HttpException("Invalid request: " +
                                    "number of repetitions is invalid");
                        }
                    }

                    final HttpEntity incoming = request.getEntity();
                    if (incoming != null) {
                        final String line = EntityUtils.toString(incoming);
                        final ContentType contentType = ContentType.getOrDefault(incoming);
                        Charset charset = contentType.getCharset();
                        if (charset == null) {
                            charset = StandardCharsets.ISO_8859_1;
                        }
                        final RepeatingEntity outgoing = new RepeatingEntity(line, charset, n);
                        outgoing.setChunked(n % 2 == 0);
                        response.setEntity(outgoing);
                    }
                } else {
                    throw new HttpException("Invalid request: POST request expected");
                }
            }

        });

        this.server.start();
        final DefaultBHttpClientConnection conn = client.createConnection();
        final HttpHost host = new HttpHost("localhost", this.server.getPort());

        try {
            for (final String pattern : patterns) {
                for (int n = 1000; n < 1020; n++) {
                    if (!conn.isOpen()) {
                        client.connect(host, conn);
                    }

                    final BasicHttpRequest post = new BasicHttpRequest(
                            "POST", "/?n=" + n);
                    final StringEntity outgoing = new StringEntity(pattern);
                    outgoing.setChunked(n % 2 == 0);
                    post.setEntity(outgoing);

                    final HttpResponse response = this.client.execute(post, host, conn);
                    final HttpEntity incoming = response.getEntity();
                    Assert.assertNotNull(incoming);
                    final InputStream instream = incoming.getContent();
                    final ContentType contentType = ContentType.getOrDefault(incoming);
                    Charset charset = contentType.getCharset();
                    if (charset == null) {
                        charset = StandardCharsets.ISO_8859_1;
                    }
                    Assert.assertNotNull(instream);
                    final BufferedReader reader = new BufferedReader(new InputStreamReader(instream, charset));

                    String line;
                    int count = 0;
                    while ((line = reader.readLine()) != null) {
                        Assert.assertEquals(pattern, line);
                        count++;
                    }
                    Assert.assertEquals(n, count);
                    if (!this.client.keepAlive(post, response)) {
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

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                final HttpEntity incoming = request.getEntity();
                if (incoming != null) {
                    final byte[] data = EntityUtils.toByteArray(incoming);
                    final ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    response.setEntity(outgoing);
                }
            }

        });

        this.server.start();

        final DefaultBHttpClientConnection conn = client.createConnection();
        final HttpHost host = new HttpHost("localhost", this.server.getPort());

        try {
            if (!conn.isOpen()) {
                client.connect(host, conn);
            }

            final BasicHttpRequest post = new BasicHttpRequest("POST", "/");
            post.setEntity(null);

            final HttpResponse response = this.client.execute(post, host, conn);
            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
            final byte[] received = EntityUtils.toByteArray(response.getEntity());
            Assert.assertEquals(0, received.length);
        } finally {
            conn.close();
            this.server.shutdown();
        }
    }

    @Test
    public void testHttpPostNoContentLength() throws Exception {
        this.server.registerHandler("*", new HttpRequestHandler() {

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                final HttpEntity incoming = request.getEntity();
                if (incoming != null) {
                    final byte[] data = EntityUtils.toByteArray(incoming);
                    final ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    response.setEntity(outgoing);
                }
            }

        });

        this.server.start();

        final DefaultBHttpClientConnection conn = client.createConnection();
        final HttpHost host = new HttpHost("localhost", this.server.getPort());

        try {
            if (!conn.isOpen()) {
                client.connect(host, conn);
            }

            final BasicHttpRequest post = new BasicHttpRequest("POST", "/");
            post.setEntity(null);

            this.client = new HttpClient(new ImmutableHttpProcessor(
                    new RequestTargetHost(),
                    new RequestConnControl(),
                    new RequestUserAgent(),
                    new RequestExpectContinue()));

            final HttpResponse response = this.client.execute(post, host, conn);
            Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
            final byte[] received = EntityUtils.toByteArray(response.getEntity());
            Assert.assertEquals(0, received.length);
        } finally {
            conn.close();
            this.server.shutdown();
        }
    }

    @Test
    public void testHttpPostIdentity() throws Exception {
        this.server.registerHandler("*", new HttpRequestHandler() {

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {

                final HttpEntity incoming = request.getEntity();
                if (incoming != null) {
                    final byte[] data = EntityUtils.toByteArray(incoming);
                    final ByteArrayEntity outgoing = new ByteArrayEntity(data);
                    response.setEntity(outgoing);
                }
            }

        });

        this.server.start();

        final DefaultBHttpClientConnection conn = client.createConnection();
        final HttpHost host = new HttpHost("localhost", this.server.getPort());

        try {
            if (!conn.isOpen()) {
                client.connect(host, conn);
            }

            final BasicHttpRequest post = new BasicHttpRequest("POST", "/");
            post.setEntity(null);

            this.client = new HttpClient(new ImmutableHttpProcessor(
                    new HttpRequestInterceptor() {

                        @Override
                        public void process(
                                final HttpRequest request,
                                final HttpContext context) throws HttpException, IOException {
                            request.addHeader(HttpHeaders.TRANSFER_ENCODING, "identity");
                        }

                    },
                    new RequestTargetHost(),
                    new RequestConnControl(),
                    new RequestUserAgent(),
                    new RequestExpectContinue()));

            final HttpResponse response = this.client.execute(post, host, conn);
            Assert.assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, response.getCode());
        } finally {
            conn.close();
            this.server.shutdown();
        }
    }

    @Test
    public void testNoContentResponse() throws Exception {

        final int reqNo = 20;

        // Initialize the server-side request handler
        this.server.registerHandler("*", new HttpRequestHandler() {

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                response.setStatusCode(HttpStatus.SC_NO_CONTENT);
            }

        });

        this.server.start();

        final DefaultBHttpClientConnection conn = client.createConnection();
        final HttpHost host = new HttpHost("localhost", this.server.getPort());

        try {
            for (int r = 0; r < reqNo; r++) {
                if (!conn.isOpen()) {
                    client.connect(host, conn);
                }

                final BasicHttpRequest get = new BasicHttpRequest("GET", "/?" + r);
                final HttpResponse response = this.client.execute(get, host, conn);
                Assert.assertNull(response.getEntity());
                if (!this.client.keepAlive(get, response)) {
                    conn.close();
                    Assert.fail("Connection expected to be re-usable");
                }
            }

            //Verify the connection metrics
            final HttpConnectionMetrics cm = conn.getMetrics();
            Assert.assertEquals(reqNo, cm.getRequestCount());
            Assert.assertEquals(reqNo, cm.getResponseCount());

        } finally {
            conn.close();
            this.server.shutdown();
        }
    }

    @Test
    public void testAbsentHostHeader() throws Exception {

        // Initialize the server-side request handler
        this.server.registerHandler("*", new HttpRequestHandler() {

            @Override
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                response.setEntity(new StringEntity("All is well", StandardCharsets.US_ASCII));
            }

        });

        this.client = new HttpClient(new ImmutableHttpProcessor(new RequestContent(), new RequestConnControl()));
        this.server.start();

        final DefaultBHttpClientConnection conn = client.createConnection();
        final HttpHost host = new HttpHost("localhost", this.server.getPort());
        client.connect(host, conn);

        try {
            final BasicHttpRequest get1 = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_0);
            final HttpResponse response1 = this.client.execute(get1, host, conn);
            Assert.assertEquals(200, response1.getCode());
            EntityUtils.consume(response1.getEntity());
            final BasicHttpRequest get2 = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
            final HttpResponse response2 = this.client.execute(get2, host, conn);
            Assert.assertEquals(400, response2.getCode());
            EntityUtils.consume(response2.getEntity());

        } finally {
            conn.close();
            this.server.shutdown();
        }
    }

}

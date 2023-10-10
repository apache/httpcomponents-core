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

package org.apache.hc.core5.testing.classic;

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

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.BasicHttpServerExpectationDecorator;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.RequestConnControl;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.http.protocol.RequestExpectContinue;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.apache.hc.core5.testing.classic.extension.ClassicTestResources;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class ClassicIntegrationTest {

    private static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    private final URIScheme scheme;
    @RegisterExtension
    private final ClassicTestResources testResources;

    public ClassicIntegrationTest(final URIScheme scheme) {
        this.scheme = scheme;
        this.testResources = new ClassicTestResources(scheme, TIMEOUT);
    }

    /**
     * This test case executes a series of simple GET requests
     */
    @Test
    public void testSimpleBasicHttpRequests() throws Exception {
        final ClassicTestServer server = testResources.server();
        final ClassicTestClient client = testResources.client();

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
        server.registerHandler("*", (request, response, context) -> {

            String s = request.getPath();
            if (s.startsWith("/?")) {
                s = s.substring(2);
            }
            final int index = Integer.parseInt(s);
            final byte[] data = testData.get(index);
            final ByteArrayEntity entity = new ByteArrayEntity(data, null);
            response.setEntity(entity);
        });

        server.start();
        client.start();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpHost host = new HttpHost(scheme.id, "localhost", server.getPort());

        for (int r = 0; r < reqNo; r++) {
            final BasicClassicHttpRequest get = new BasicClassicHttpRequest(Method.GET, "/?" + r);
            try (final ClassicHttpResponse response = client.execute(host, get, context)) {
                final byte[] received = EntityUtils.toByteArray(response.getEntity());
                final byte[] expected = testData.get(r);

                Assertions.assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    Assertions.assertEquals(expected[i], received[i]);
                }
            }
        }
    }

    /**
     * This test case executes a series of simple POST requests with content length
     * delimited content.
     */
    @Test
    public void testSimpleHttpPostsWithContentLength() throws Exception {
        final ClassicTestServer server = testResources.server();
        final ClassicTestClient client = testResources.client();

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
        server.registerHandler("*", (request, response, context) -> {

            final HttpEntity entity = request.getEntity();
            if (entity != null) {
                final byte[] data = EntityUtils.toByteArray(entity);
                response.setEntity(new ByteArrayEntity(data, null));
            }
        });

        server.start();
        client.start();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpHost host = new HttpHost(scheme.id, "localhost", server.getPort());

        for (int r = 0; r < reqNo; r++) {
            final BasicClassicHttpRequest post = new BasicClassicHttpRequest(Method.POST, "/");
            final byte[] data = testData.get(r);
            post.setEntity(new ByteArrayEntity(data, null));

            try (final ClassicHttpResponse response = client.execute(host, post, context)) {
                final byte[] received = EntityUtils.toByteArray(response.getEntity());
                final byte[] expected = testData.get(r);

                Assertions.assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    Assertions.assertEquals(expected[i], received[i]);
                }
            }
        }
    }

    /**
     * This test case executes a series of simple POST requests with chunk
     * coded content content.
     */
    @Test
    public void testSimpleHttpPostsChunked() throws Exception {
        final ClassicTestServer server = testResources.server();
        final ClassicTestClient client = testResources.client();

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
        server.registerHandler("*", (request, response, context) -> {

            final HttpEntity entity = request.getEntity();
            if (entity != null) {
                final byte[] data = EntityUtils.toByteArray(entity);
                response.setEntity(new ByteArrayEntity(data, null, true));
            }
        });

        server.start();
        client.start();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpHost host = new HttpHost(scheme.id, "localhost", server.getPort());

        for (int r = 0; r < reqNo; r++) {
            final BasicClassicHttpRequest post = new BasicClassicHttpRequest(Method.POST, "/");
            final byte[] data = testData.get(r);
            post.setEntity(new ByteArrayEntity(data, null, true));

            try (final ClassicHttpResponse response = client.execute(host, post, context)) {
                final byte[] received = EntityUtils.toByteArray(response.getEntity());
                final byte[] expected = testData.get(r);

                Assertions.assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    Assertions.assertEquals(expected[i], received[i]);
                }
            }
        }
    }

    /**
     * This test case executes a series of simple HTTP/1.0 POST requests.
     */
    @Test
    public void testSimpleHttpPostsHTTP10() throws Exception {
        final ClassicTestServer server = testResources.server();
        final ClassicTestClient client = testResources.client();

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
        server.registerHandler("*", (request, response, context) -> {

            final HttpEntity entity = request.getEntity();
            if (entity != null) {
                final byte[] data = EntityUtils.toByteArray(entity);
                response.setEntity(new ByteArrayEntity(data, null));
            }
        });

        server.start();
        client.start();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpHost host = new HttpHost(scheme.id, "localhost", server.getPort());

        for (int r = 0; r < reqNo; r++) {
            // Set protocol level to HTTP/1.0
            final BasicClassicHttpRequest post = new BasicClassicHttpRequest(Method.POST, "/");
            post.setVersion(HttpVersion.HTTP_1_0);
            final byte[] data = testData.get(r);
            post.setEntity(new ByteArrayEntity(data, null));

            try (final ClassicHttpResponse response = client.execute(host, post, context)) {
                final byte[] received = EntityUtils.toByteArray(response.getEntity());
                final byte[] expected = testData.get(r);

                Assertions.assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    Assertions.assertEquals(expected[i], received[i]);
                }
            }
        }
    }

    /**
     * This test case ensures that HTTP/1.1 features are disabled when executing
     * HTTP/1.0 compatible requests.
     */
    @Test
    public void testHTTP11FeaturesDisabledWithHTTP10Requests() throws Exception {
        final ClassicTestServer server = testResources.server();
        final ClassicTestClient client = testResources.client();

        server.start();
        client.start();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpHost host = new HttpHost(scheme.id, "localhost", server.getPort());

        final BasicClassicHttpRequest post = new BasicClassicHttpRequest(Method.POST, "/");
        post.setVersion(HttpVersion.HTTP_1_0);
        post.setEntity(new ByteArrayEntity(new byte[] {'a', 'b', 'c'}, null, true));

        Assertions.assertThrows(ProtocolException.class, () -> {
            try (final ClassicHttpResponse response = client.execute(host, post, context)) {
                EntityUtils.consume(response.getEntity());
            }
        });
    }

    /**
     * This test case executes a series of simple POST requests using
     * the 'expect: continue' handshake.
     */
    @Test
    public void testHttpPostsWithExpectContinue() throws Exception {
        final ClassicTestServer server = testResources.server();
        final ClassicTestClient client = testResources.client();

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
        server.registerHandler("*", (request, response, context) -> {

            final HttpEntity entity = request.getEntity();
            if (entity != null) {
                final byte[] data = EntityUtils.toByteArray(entity);
                response.setEntity(new ByteArrayEntity(data, null, true));
            }
        });

        server.start();
        client.start();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpHost host = new HttpHost(scheme.id, "localhost", server.getPort());

        for (int r = 0; r < reqNo; r++) {
            final BasicClassicHttpRequest post = new BasicClassicHttpRequest(Method.POST, "/");
            final byte[] data = testData.get(r);
            post.setEntity(new ByteArrayEntity(data, null, true));

            try (final ClassicHttpResponse response = client.execute(host, post, context)) {
                final byte[] received = EntityUtils.toByteArray(response.getEntity());
                final byte[] expected = testData.get(r);

                Assertions.assertEquals(expected.length, received.length);
                for (int i = 0; i < expected.length; i++) {
                    Assertions.assertEquals(expected[i], received[i]);
                }
            }
        }
    }

    /**
     * This test case executes a series of simple POST requests that do not
     * meet the target server expectations.
     */
    @Test
    public void testHttpPostsWithExpectationVerification() throws Exception {
        final ClassicTestServer server = testResources.server();
        final ClassicTestClient client = testResources.client();

        final int reqNo = 20;

        // Initialize the server-side request handler
        server.registerHandler("*", (request, response, context) -> response.setEntity(new StringEntity("No content")));

        server.start(null, null, handler -> new BasicHttpServerExpectationDecorator(handler) {

            @Override
            protected ClassicHttpResponse verify(final ClassicHttpRequest request, final HttpContext context) {
                final Header someheader = request.getFirstHeader("Secret");
                if (someheader != null) {
                    final int secretNumber;
                    try {
                        secretNumber = Integer.parseInt(someheader.getValue());
                    } catch (final NumberFormatException ex) {
                        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_BAD_REQUEST);
                        response.setEntity(new StringEntity(ex.toString()));
                        return response;
                    }
                    if (secretNumber >= 2) {
                        final ClassicHttpResponse response = new BasicClassicHttpResponse(HttpStatus.SC_EXPECTATION_FAILED);
                        response.setEntity(new StringEntity("Wrong secret number", ContentType.TEXT_PLAIN));
                        return response;
                    }
                }
                return null;
            }

        });
        client.start();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpHost host = new HttpHost(scheme.id, "localhost", server.getPort());

        for (int r = 0; r < reqNo; r++) {
            final BasicClassicHttpRequest post = new BasicClassicHttpRequest(Method.POST, "/");
            post.addHeader("Secret", Integer.toString(r));

            final byte[] b = new byte[2048];
            for (int i = 0; i < b.length; i++) {
                b[i] = (byte) ('a' + r);
            }
            post.setEntity(new ByteArrayEntity(b, ContentType.TEXT_PLAIN));

            try (final ClassicHttpResponse response = client.execute(host, post, context)) {
                final HttpEntity responseEntity = response.getEntity();
                Assertions.assertNotNull(responseEntity);
                EntityUtils.consume(responseEntity);

                if (r >= 2) {
                    Assertions.assertEquals(HttpStatus.SC_EXPECTATION_FAILED, response.getCode());
                } else {
                    Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
                }
            }
        }
    }

    static class RepeatingEntity extends AbstractHttpEntity {

        private final byte[] raw;
        private final int n;

        public RepeatingEntity(final String content, final Charset charset, final int n, final boolean chunked) {
            super(ContentType.TEXT_PLAIN.withCharset(charset), null, chunked);
            final Charset cs = charset != null ? charset : StandardCharsets.UTF_8;
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
        public void writeTo(final OutputStream outStream) throws IOException {
            for (int i = 0; i < this.n; i++) {
                outStream.write(this.raw);
                outStream.write('\r');
                outStream.write('\n');
            }
            outStream.flush();
        }

        @Override
        public void close() throws IOException {
        }

    }

    @Test
    public void testHttpContent() throws Exception {
        final ClassicTestServer server = testResources.server();
        final ClassicTestClient client = testResources.client();

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
        server.registerHandler("*", (request, response, context) -> {

            int n = 1;
            String s = request.getPath();
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

            final HttpEntity entity = request.getEntity();
            if (entity != null) {
                final String line = EntityUtils.toString(entity);
                final ContentType contentType = ContentType.parse(entity.getContentType());
                final Charset charset = ContentType.getCharset(contentType, StandardCharsets.UTF_8);
                response.setEntity(new RepeatingEntity(line, charset, n, n % 2 == 0));
            }
        });

        server.start();
        client.start();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpHost host = new HttpHost(scheme.id, "localhost", server.getPort());

        for (final String pattern : patterns) {
            for (int n = 1000; n < 1020; n++) {
                final BasicClassicHttpRequest post = new BasicClassicHttpRequest(
                        Method.POST.name(), "/?n=" + n);
                post.setEntity(new StringEntity(pattern, ContentType.TEXT_PLAIN, n % 2 == 0));

                try (final ClassicHttpResponse response = client.execute(host, post, context)) {
                    final HttpEntity entity = response.getEntity();
                    Assertions.assertNotNull(entity);
                    final InputStream inStream = entity.getContent();
                    final ContentType contentType = ContentType.parse(entity.getContentType());
                    final Charset charset = ContentType.getCharset(contentType, StandardCharsets.UTF_8);
                    Assertions.assertNotNull(inStream);
                    final BufferedReader reader = new BufferedReader(new InputStreamReader(inStream, charset));

                    String line;
                    int count = 0;
                    while ((line = reader.readLine()) != null) {
                        Assertions.assertEquals(pattern, line);
                        count++;
                    }
                    Assertions.assertEquals(n, count);
                }
            }
        }
    }

    @Test
    public void testHttpPostNoEntity() throws Exception {
        final ClassicTestServer server = testResources.server();
        final ClassicTestClient client = testResources.client();

        server.registerHandler("*", (request, response, context) -> {

            final HttpEntity entity = request.getEntity();
            if (entity != null) {
                final byte[] data = EntityUtils.toByteArray(entity);
                response.setEntity(new ByteArrayEntity(data, null));
            }
        });

        server.start();
        client.start();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpHost host = new HttpHost(scheme.id, "localhost", server.getPort());

        final BasicClassicHttpRequest post = new BasicClassicHttpRequest(Method.POST, "/");
        post.setEntity(null);

        try (final ClassicHttpResponse response = client.execute(host, post, context)) {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            final byte[] received = EntityUtils.toByteArray(response.getEntity());
            Assertions.assertEquals(0, received.length);
        }
    }

    @Test
    public void testHttpPostNoContentLength() throws Exception {
        final ClassicTestServer server = testResources.server();
        final ClassicTestClient client = testResources.client();

        server.registerHandler("*", (request, response, context) -> {

            final HttpEntity entity = request.getEntity();
            if (entity != null) {
                final byte[] data = EntityUtils.toByteArray(entity);
                response.setEntity(new ByteArrayEntity(data, null));
            }
        });

        server.start();
        client.start(new DefaultHttpProcessor(
                RequestTargetHost.INSTANCE,
                RequestConnControl.INSTANCE,
                RequestUserAgent.INSTANCE,
                RequestExpectContinue.INSTANCE));

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpHost host = new HttpHost(scheme.id, "localhost", server.getPort());

        final BasicClassicHttpRequest post = new BasicClassicHttpRequest(Method.POST, "/");
        post.setEntity(null);

        try (final ClassicHttpResponse response = client.execute(host, post, context)) {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            final byte[] received = EntityUtils.toByteArray(response.getEntity());
            Assertions.assertEquals(0, received.length);
        }
    }

    @Test
    public void testHttpPostIdentity() throws Exception {
        final ClassicTestServer server = testResources.server();
        final ClassicTestClient client = testResources.client();

        server.registerHandler("*", (request, response, context) -> {

            final HttpEntity entity = request.getEntity();
            if (entity != null) {
                final byte[] data = EntityUtils.toByteArray(entity);
                response.setEntity(new ByteArrayEntity(data, null));
            }
        });

        server.start();
        client.start(new DefaultHttpProcessor(
                (request, entity, context) -> request.addHeader(HttpHeaders.TRANSFER_ENCODING, "identity"),
                RequestTargetHost.INSTANCE,
                RequestConnControl.INSTANCE,
                RequestUserAgent.INSTANCE,
                RequestExpectContinue.INSTANCE));

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpHost host = new HttpHost(scheme.id, "localhost", server.getPort());

        final BasicClassicHttpRequest post = new BasicClassicHttpRequest(Method.POST, "/");
        post.setEntity(null);

        try (final ClassicHttpResponse response = client.execute(host, post, context)) {
            Assertions.assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, response.getCode());
        }
    }

    @Test
    public void testNoContentResponse() throws Exception {
        final ClassicTestServer server = testResources.server();
        final ClassicTestClient client = testResources.client();

        final int reqNo = 20;

        // Initialize the server-side request handler
        server.registerHandler("*", (request, response, context) -> response.setCode(HttpStatus.SC_NO_CONTENT));

        server.start();
        client.start();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpHost host = new HttpHost(scheme.id, "localhost", server.getPort());

        for (int r = 0; r < reqNo; r++) {
            final BasicClassicHttpRequest get = new BasicClassicHttpRequest(Method.GET, "/?" + r);
            try (final ClassicHttpResponse response = client.execute(host, get, context)) {
                Assertions.assertNull(response.getEntity());
            }
        }
    }

    @Test
    public void testHeaderTooLarge() throws Exception {
        final ClassicTestServer server = testResources.server();
        final ClassicTestClient client = testResources.client();

        server.registerHandler("*", (request, response, context) ->
                response.setEntity(new StringEntity("All is well", StandardCharsets.US_ASCII)));

        server.start(
                Http1Config.custom()
                        .setMaxLineLength(100)
                        .build(),
                null,
                null);
        client.start();

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpHost host = new HttpHost(scheme.id, "localhost", server.getPort());

        final BasicClassicHttpRequest get1 = new BasicClassicHttpRequest(Method.GET, "/");
        get1.setHeader("big-f-header", "1234567890123456789012345678901234567890123456789012345678901234567890" +
                "1234567890123456789012345678901234567890");
        try (final ClassicHttpResponse response1 = client.execute(host, get1, context)) {
            Assertions.assertEquals(431, response1.getCode());
            EntityUtils.consume(response1.getEntity());
        }
    }

    @Test
    public void testHeaderTooLargePost() throws Exception {
        final ClassicTestServer server = testResources.server();
        final ClassicTestClient client = testResources.client();

        server.registerHandler("*", (request, response, context) ->
                response.setEntity(new StringEntity("All is well", StandardCharsets.US_ASCII)));

        server.start(
                Http1Config.custom()
                        .setMaxLineLength(100)
                        .build(),
                null,
                null);
        client.start(
                new DefaultHttpProcessor(RequestContent.INSTANCE, RequestTargetHost.INSTANCE, RequestConnControl.INSTANCE));

        final HttpCoreContext context = HttpCoreContext.create();
        final HttpHost host = new HttpHost(scheme.id, "localhost", server.getPort());

        final ClassicHttpRequest post1 = new BasicClassicHttpRequest(Method.POST, "/");
        post1.setHeader("big-f-header", "1234567890123456789012345678901234567890123456789012345678901234567890" +
                "1234567890123456789012345678901234567890");
        final byte[] b = new byte[2048];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) ('a' + i % 10);
        }
        post1.setEntity(new ByteArrayEntity(b, ContentType.TEXT_PLAIN));

        try (final ClassicHttpResponse response1 = client.execute(host, post1, context)) {
            Assertions.assertEquals(431, response1.getCode());
            EntityUtils.consume(response1.getEntity());
        }
    }

}

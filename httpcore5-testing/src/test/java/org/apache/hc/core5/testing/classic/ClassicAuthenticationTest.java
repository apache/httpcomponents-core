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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.impl.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.RequesterBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.StandardFilter;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.AbstractHttpServerAuthFilter;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.Timeout;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(Parameterized.class)
public class ClassicAuthenticationTest {

    @Parameterized.Parameters(name = "respond immediately on auth failure: {0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                { Boolean.FALSE },
                { Boolean.TRUE }
        });
    }

    private static final Timeout TIMEOUT = Timeout.ofSeconds(30);

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final boolean respondImmediately;
    private HttpServer server;

    public ClassicAuthenticationTest(final Boolean respondImmediately) {
        this.respondImmediately = respondImmediately;
    }

    @Rule
    public ExternalResource serverResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            log.debug("Starting up test server");
            server = ServerBootstrap.bootstrap()
                    .setSocketConfig(
                            SocketConfig.custom()
                                    .setSoTimeout(TIMEOUT)
                                    .build())
                    .register("*", new EchoHandler())
                    .replaceFilter(StandardFilter.EXPECT_CONTINUE.name(), new AbstractHttpServerAuthFilter<String>(respondImmediately) {

                        @Override
                        protected String parseChallengeResponse(
                                final String challenge, final HttpContext context) throws HttpException {
                            return challenge;
                        }

                        @Override
                        protected boolean authenticate(
                                final String challengeResponse,
                                final URIAuthority authority,
                                final String requestUri,
                                final HttpContext context) {
                            return challengeResponse != null && challengeResponse.equals("let me pass");
                        }

                        @Override
                        protected String generateChallenge(
                                final String challengeResponse,
                                final URIAuthority authority,
                                final String requestUri,
                                final HttpContext context) {
                            return "who goes there?";
                        }

                        @Override
                        protected HttpEntity generateResponseContent(final HttpResponse unauthorized) {
                            return new StringEntity("You shall not pass!!!");
                        }
                    })
                    .setConnectionFactory(LoggingBHttpServerConnectionFactory.INSTANCE)
                    .setExceptionListener(LoggingExceptionListener.INSTANCE)
                    .setStreamListener(LoggingHttp1StreamListener.INSTANCE)
                    .create();
        }

        @Override
        protected void after() {
            log.debug("Shutting down test server");
            if (server != null) {
                try {
                    server.close(CloseMode.IMMEDIATE);
                } catch (final Exception ignore) {
                }
            }
        }

    };

    private HttpRequester requester;

    @Rule
    public ExternalResource clientResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            log.debug("Starting up test client");
            requester = RequesterBootstrap.bootstrap()
                    .setSocketConfig(SocketConfig.custom()
                            .setSoTimeout(TIMEOUT)
                            .build())
                    .setMaxTotal(2)
                    .setDefaultMaxPerRoute(2)
                    .setConnectionFactory(LoggingBHttpClientConnectionFactory.INSTANCE)
                    .setStreamListener(LoggingHttp1StreamListener.INSTANCE)
                    .setConnPoolListener(LoggingConnPoolListener.INSTANCE)
                    .create();
        }

        @Override
        protected void after() {
            log.debug("Shutting down test client");
            if (requester != null) {
                try {
                    requester.close(CloseMode.GRACEFUL);
                } catch (final Exception ignore) {
                }
            }
        }

    };

    @Test
    public void testGetRequestAuthentication() throws Exception {
        server.start();
        final HttpHost target = new HttpHost("localhost", server.getLocalPort());
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request1 = new BasicClassicHttpRequest(Method.GET, "/stuff");
        try (final ClassicHttpResponse response1 = requester.execute(target, request1, TIMEOUT, context)) {
            MatcherAssert.assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_UNAUTHORIZED));
            final String body1 = EntityUtils.toString(response1.getEntity());
            MatcherAssert.assertThat(body1, CoreMatchers.equalTo("You shall not pass!!!"));
        }
        final ClassicHttpRequest request2 = new BasicClassicHttpRequest(Method.GET, "/stuff");
        request2.setHeader(HttpHeaders.AUTHORIZATION, "let me pass");
        try (final ClassicHttpResponse response2 = requester.execute(target, request2, TIMEOUT, context)) {
            MatcherAssert.assertThat(response2.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body1 = EntityUtils.toString(response2.getEntity());
            MatcherAssert.assertThat(body1, CoreMatchers.equalTo(""));
        }
    }

    @Test
    public void testPostRequestAuthentication() throws Exception {
        server.start();
        final HttpHost target = new HttpHost("localhost", server.getLocalPort());
        final HttpCoreContext context = HttpCoreContext.create();
        final Random rnd = new Random();
        final byte[] stuff = new byte[10240];
        for (int i = 0; i < stuff.length; i++) {
            stuff[i] = (byte)('a' + rnd.nextInt(10));
        }
        final ClassicHttpRequest request1 = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request1.setEntity(new ByteArrayEntity(stuff, ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response1 = requester.execute(target, request1, TIMEOUT, context)) {
            MatcherAssert.assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_UNAUTHORIZED));
            final String body1 = EntityUtils.toString(response1.getEntity());
            MatcherAssert.assertThat(body1, CoreMatchers.equalTo("You shall not pass!!!"));
        }
        final ClassicHttpRequest request2 = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request2.setHeader(HttpHeaders.AUTHORIZATION, "let me pass");
        request2.setEntity(new ByteArrayEntity(stuff, ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response2 = requester.execute(target, request2, TIMEOUT, context)) {
            MatcherAssert.assertThat(response2.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body1 = EntityUtils.toString(response2.getEntity());
            MatcherAssert.assertThat(body1, CoreMatchers.equalTo(new String(stuff, StandardCharsets.US_ASCII)));
        }
    }

    @Test
    public void testPostRequestAuthenticationNoExpectContinue() throws Exception {
        server.start();
        final HttpHost target = new HttpHost("localhost", server.getLocalPort());
        final HttpCoreContext context = HttpCoreContext.create();
        final Random rnd = new Random();
        final byte[] stuff = new byte[10240];
        for (int i = 0; i < stuff.length; i++) {
            stuff[i] = (byte)('a' + rnd.nextInt(10));
        }
        final ClassicHttpRequest request1 = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request1.setVersion(HttpVersion.HTTP_1_0);
        request1.setEntity(new ByteArrayEntity(stuff, ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response1 = requester.execute(target, request1, TIMEOUT, context)) {
            MatcherAssert.assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_UNAUTHORIZED));
            final String body1 = EntityUtils.toString(response1.getEntity());
            MatcherAssert.assertThat(body1, CoreMatchers.equalTo("You shall not pass!!!"));
        }
        final ClassicHttpRequest request2 = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request2.setHeader(HttpHeaders.AUTHORIZATION, "let me pass");
        request2.setVersion(HttpVersion.HTTP_1_0);
        request2.setEntity(new ByteArrayEntity(stuff, ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response2 = requester.execute(target, request2, TIMEOUT, context)) {
            MatcherAssert.assertThat(response2.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body1 = EntityUtils.toString(response2.getEntity());
            MatcherAssert.assertThat(body1, CoreMatchers.equalTo(new String(stuff, StandardCharsets.US_ASCII)));
        }
    }

}

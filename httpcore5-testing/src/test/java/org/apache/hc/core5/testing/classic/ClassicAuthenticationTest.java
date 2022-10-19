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

import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.charset.StandardCharsets;
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
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.StandardFilter;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.AbstractHttpServerAuthFilter;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.testing.classic.extension.HttpRequesterResource;
import org.apache.hc.core5.testing.classic.extension.HttpServerResource;
import org.apache.hc.core5.util.Timeout;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class ClassicAuthenticationTest {

    private static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private HttpServerResource serverResource;
    @RegisterExtension
    private HttpRequesterResource clientResource;

    public ClassicAuthenticationTest(final Boolean respondImmediately) {
        this.serverResource = new HttpServerResource(URIScheme.HTTP, bootstrap -> bootstrap
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
        );
        this.clientResource = new HttpRequesterResource(bootstrap -> bootstrap
                .setSocketConfig(SocketConfig.custom()
                        .setSoTimeout(TIMEOUT)
                        .build())
        );
    }

    @Test
    public void testGetRequestAuthentication() throws Exception {
        final HttpServer server = serverResource.start();
        final HttpRequester requester = clientResource.start();

        final HttpHost target = new HttpHost("localhost", server.getLocalPort());
        final HttpCoreContext context = HttpCoreContext.create();
        final ClassicHttpRequest request1 = new BasicClassicHttpRequest(Method.GET, "/stuff");
        try (final ClassicHttpResponse response1 = requester.execute(target, request1, TIMEOUT, context)) {
            assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_UNAUTHORIZED));
            final String body1 = EntityUtils.toString(response1.getEntity());
            assertThat(body1, CoreMatchers.equalTo("You shall not pass!!!"));
        }
        final ClassicHttpRequest request2 = new BasicClassicHttpRequest(Method.GET, "/stuff");
        request2.setHeader(HttpHeaders.AUTHORIZATION, "let me pass");
        try (final ClassicHttpResponse response2 = requester.execute(target, request2, TIMEOUT, context)) {
            assertThat(response2.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body1 = EntityUtils.toString(response2.getEntity());
            assertThat(body1, CoreMatchers.equalTo(""));
        }
    }

    @Test
    public void testPostRequestAuthentication() throws Exception {
        final HttpServer server = serverResource.start();
        final HttpRequester requester = clientResource.start();

        final HttpHost target = new HttpHost("localhost", server.getLocalPort());
        final HttpCoreContext context = HttpCoreContext.create();
        final Random rnd = new Random();
        final byte[] stuff = new byte[10240];
        for (int i = 0; i < stuff.length; i++) {
            stuff[i] = (byte) ('a' + rnd.nextInt(10));
        }
        final ClassicHttpRequest request1 = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request1.setEntity(new ByteArrayEntity(stuff, ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response1 = requester.execute(target, request1, TIMEOUT, context)) {
            assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_UNAUTHORIZED));
            final String body1 = EntityUtils.toString(response1.getEntity());
            assertThat(body1, CoreMatchers.equalTo("You shall not pass!!!"));
        }
        final ClassicHttpRequest request2 = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request2.setHeader(HttpHeaders.AUTHORIZATION, "let me pass");
        request2.setEntity(new ByteArrayEntity(stuff, ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response2 = requester.execute(target, request2, TIMEOUT, context)) {
            assertThat(response2.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body1 = EntityUtils.toString(response2.getEntity());
            assertThat(body1, CoreMatchers.equalTo(new String(stuff, StandardCharsets.US_ASCII)));
        }
    }

    @Test
    public void testPostRequestAuthenticationNoExpectContinue() throws Exception {
        final HttpServer server = serverResource.start();
        final HttpRequester requester = clientResource.start();

        final HttpHost target = new HttpHost("localhost", server.getLocalPort());
        final HttpCoreContext context = HttpCoreContext.create();
        final Random rnd = new Random();
        final byte[] stuff = new byte[10240];
        for (int i = 0; i < stuff.length; i++) {
            stuff[i] = (byte) ('a' + rnd.nextInt(10));
        }
        final ClassicHttpRequest request1 = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request1.setVersion(HttpVersion.HTTP_1_0);
        request1.setEntity(new ByteArrayEntity(stuff, ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response1 = requester.execute(target, request1, TIMEOUT, context)) {
            assertThat(response1.getCode(), CoreMatchers.equalTo(HttpStatus.SC_UNAUTHORIZED));
            final String body1 = EntityUtils.toString(response1.getEntity());
            assertThat(body1, CoreMatchers.equalTo("You shall not pass!!!"));
        }
        final ClassicHttpRequest request2 = new BasicClassicHttpRequest(Method.POST, "/stuff");
        request2.setHeader(HttpHeaders.AUTHORIZATION, "let me pass");
        request2.setVersion(HttpVersion.HTTP_1_0);
        request2.setEntity(new ByteArrayEntity(stuff, ContentType.TEXT_PLAIN));
        try (final ClassicHttpResponse response2 = requester.execute(target, request2, TIMEOUT, context)) {
            assertThat(response2.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
            final String body1 = EntityUtils.toString(response2.getEntity());
            assertThat(body1, CoreMatchers.equalTo(new String(stuff, StandardCharsets.US_ASCII)));
        }
    }

}

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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Future;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.nio.BasicAsyncRequestHandler;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.HttpAsyncExchange;
import org.apache.hc.core5.http.nio.HttpAsyncExpectationVerifier;
import org.apache.hc.core5.http.nio.entity.NStringEntity;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.testserver.nio.HttpCoreNIOTestBase;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * HttpCore NIO integration tests for async handlers.
 */
@RunWith(Parameterized.class)
public class TestHttpAsyncHandlersBrokenExpectContinue extends HttpCoreNIOTestBase {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> protocols() {
        return Arrays.asList(new Object[][]{
                { ProtocolScheme.http },
                { ProtocolScheme.https },
        });
    }

    public TestHttpAsyncHandlersBrokenExpectContinue(final ProtocolScheme scheme) {
        super(scheme);
    }

    @Before
    public void setUp() throws Exception {
        initServer();
        initClient();
    }

    @After
    public void tearDown() throws Exception {
        shutDownClient();
        shutDownServer();
    }

    private HttpHost start() throws IOException, InterruptedException {
        this.server.start();
        this.client.start();

        final ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();

        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
        return new HttpHost("localhost", address.getPort(), getScheme().name());
    }

    private static String createRequestUri(final String pattern, final int count) {
        return pattern + "x" + count;
    }

    private static String createExpectedString(final String pattern, final int count) {
        final StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < count; i++) {
            buffer.append(pattern);
        }
        return buffer.toString();
    }

    @Test
    public void testHttpPostsWithExpectationVerificationSendWithoutAck() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler()));
        this.server.setExpectationVerifier(new HttpAsyncExpectationVerifier() {

            @Override
            public void verify(
                    final HttpAsyncExchange httpexchange,
                    final HttpContext context) throws HttpException {
                try {
                    Thread.sleep(1200);
                } catch (final InterruptedException ignore) {
                }

                httpexchange.submitResponse();
            }

        });

        this.client.setMaxPerRoute(1);
        this.client.setMaxTotal(1);
        // Do not wait for continue
        this.client.setWaitForContinue(1);

        final HttpHost target = start();

        for (int i = 0; i < 2; i++) {

            final BasicHttpRequest request1 = new BasicHttpRequest("POST", createRequestUri("AAAAA", 10));
            final HttpEntity entity1 = new NStringEntity(createExpectedString("AAAAA", 1000));
            request1.setEntity(entity1);

            final HttpContext context = new BasicHttpContext();
            final Future<HttpResponse> future1 = this.client.execute(target, request1, context);
            final HttpResponse response1 = future1.get();
            Assert.assertEquals(HttpStatus.SC_OK, response1.getCode());

            final BasicHttpRequest request2 = new BasicHttpRequest("POST", createRequestUri("BBBBB", 10));
            final NStringEntity entity2 = new NStringEntity(createExpectedString("BBBBB", 1000));
            entity2.setChunked(true);
            request2.setEntity(entity2);

            final Future<HttpResponse> future2 = this.client.execute(target, request2, context);
            final HttpResponse response2 = future2.get();
            Assert.assertEquals(HttpStatus.SC_OK, response2.getCode());
        }
    }

}

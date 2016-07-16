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

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.impl.nio.DefaultNHttpClientConnectionFactory;
import org.apache.hc.core5.http.impl.nio.HttpAsyncRequestExecutor;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.ImmutableHttpProcessor;
import org.apache.hc.core5.http.protocol.RequestConnControl;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.apache.hc.core5.http.testserver.nio.HttpClientNio;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for handling out of sequence responses.
 */
public class TestClientOutOfSequenceResponse {

    private ServerSocket server;
    private HttpClientNio client;

    @Before
    public void setup() throws Exception {
        server = new ServerSocket(0, 1);
        final HttpProcessor httpProcessor = new ImmutableHttpProcessor(
                new RequestContent(),
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent("TEST-CLIENT/1.1"));

        final IOReactorConfig reactorConfig = IOReactorConfig.custom()
                .setConnectTimeout(5000)
                .setSoTimeout(5000)
                .build();

        client = new HttpClientNio(
                httpProcessor,
                new HttpAsyncRequestExecutor(),
                DefaultNHttpClientConnectionFactory.INSTANCE,
                reactorConfig);
    }

    @After
    public void cleanup() throws Exception {
        if (client != null) {
            client.shutdown();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    public void testOutOfSequenceResponse() throws Exception {
        client.setMaxPerRoute(1);
        client.setMaxTotal(1);

        client.start();
        final HttpHost target = new HttpHost("localhost", server.getLocalPort());
        final HttpRequest get1 = new BasicHttpRequest("GET", "/");
        final Future<HttpResponse> future1 = client.execute(target, get1);
        final HttpRequest get2 = new BasicHttpRequest("GET", "/");
        final Future<HttpResponse> future2 = client.execute(target, get2);

        final Socket socket = server.accept();
        Thread.sleep(100);
        for (int i = 0; i < 3; ++i) {
            socket.getOutputStream().write((
                    "HTTP/1.1 200 OK\r\n" +
                    "Content-Length: 0\r\n" +
                    "Connection: keep-alive\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
        }

        final HttpResponse response1 = future1.get();
        Assert.assertEquals(200, response1.getCode());

        try {
            final HttpResponse response2 = future2.get();
            Assert.assertEquals(200, response2.getCode());
        } catch (final ExecutionException ex) {
            Assert.assertTrue(ex.getCause() instanceof HttpException);
        }
    }

}

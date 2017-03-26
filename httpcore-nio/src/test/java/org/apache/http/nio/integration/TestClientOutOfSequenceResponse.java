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

package org.apache.http.nio.integration;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.http.Consts;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.impl.nio.pool.BasicNIOConnFactory;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.nio.testserver.HttpClientNio;
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
        client = new HttpClientNio(new BasicNIOConnFactory(ConnectionConfig.DEFAULT));
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
        final HttpRequest get1 = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        final Future<HttpResponse> future1 = client.execute(target, get1);
        final HttpRequest get2 = new BasicHttpRequest("GET", "/", HttpVersion.HTTP_1_1);
        final Future<HttpResponse> future2 = client.execute(target, get2);

        final Socket socket = server.accept();
        Thread.sleep(100);
        for (int i = 0; i < 3; ++i) {
            socket.getOutputStream().write((
                    "HTTP/1.1 200 OK\r\n" +
                    "Content-Length: 0\r\n" +
                    "Connection: keep-alive\r\n\r\n").getBytes(Consts.UTF_8));
            socket.getOutputStream().flush();
        }

        final HttpResponse response1 = future1.get();
        Assert.assertEquals(200, response1.getStatusLine().getStatusCode());

        try {
            final HttpResponse response2 = future2.get();
            Assert.assertEquals(200, response2.getStatusLine().getStatusCode());
        } catch (final ExecutionException ex) {
            Assert.assertTrue(ex.getCause() instanceof HttpException);
        }
    }

}

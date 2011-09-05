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

package org.apache.http.nio.protocol;

import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;

import org.apache.http.HttpCoreNIOTestBase;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.LoggingClientConnectionFactory;
import org.apache.http.LoggingServerConnectionFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.nio.NHttpClientIOTarget;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.NHttpServerIOTarget;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * HttpCore NIO integration tests for async handlers.
 */
public class TestHttpAsyncHandlers extends HttpCoreNIOTestBase {

    @Before
    public void setUp() throws Exception {
        initServer();
        initClient();
        initConnPool();
    }

    @After
    public void tearDown() throws Exception {
        shutDownConnPool();
        shutDownClient();
        shutDownServer();
    }

    @Override
    protected NHttpConnectionFactory<NHttpServerIOTarget> createServerConnectionFactory(
            final HttpParams params) throws Exception {
        return new LoggingServerConnectionFactory(params);
    }

    @Override
    protected NHttpConnectionFactory<NHttpClientIOTarget> createClientConnectionFactory(
            final HttpParams params) throws Exception {
        return new LoggingClientConnectionFactory(params);
    }

    private InetSocketAddress start() throws Exception {
        HttpAsyncServiceHandler serviceHandler = new HttpAsyncServiceHandler(
                new RequestHandlerResolver(new BufferingAsyncRequestHandler(new RequestHandler())),
                this.serverHttpProc,
                new DefaultConnectionReuseStrategy(),
                this.serverParams);
        HttpAsyncClientProtocolHandler clientHandler = new HttpAsyncClientProtocolHandler();
        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();

        Assert.assertEquals("Test server status", IOReactorStatus.ACTIVE, this.server.getStatus());
        return (InetSocketAddress) endpoint.getAddress();
    }

    @Test
    public void testHttpGets() throws Exception {
        InetSocketAddress address = start();

        this.connpool.setDefaultMaxPerRoute(3);
        this.connpool.setMaxTotal(3);

        String pattern = RndTestPatternGenerator.generateText();
        int count = RndTestPatternGenerator.generateCount(1000);

        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < count; i++) {
            buffer.append(pattern);
        }
        String expectedPattern = buffer.toString();

        HttpHost target = new HttpHost("localhost", address.getPort());

        Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            BasicHttpRequest request = new BasicHttpRequest(
                    "GET", pattern + "x" + count);
            Future<HttpResponse> future = this.executor.execute(
                    new BasicAsyncRequestProducer(target, request),
                    new BasicAsyncResponseConsumer(),
                    this.connpool);
            queue.add(future);
        }

        Assert.assertEquals("Test client status", IOReactorStatus.ACTIVE, this.client.getStatus());

        while (!queue.isEmpty()) {
            Future<HttpResponse> future = queue.remove();
            HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(expectedPattern, EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testHttpPostsWithContentLength() throws Exception {
        InetSocketAddress address = start();

        this.connpool.setDefaultMaxPerRoute(3);
        this.connpool.setMaxTotal(3);

        String pattern = RndTestPatternGenerator.generateText();
        int count = RndTestPatternGenerator.generateCount(1000);

        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < count; i++) {
            buffer.append(pattern);
        }
        String expectedPattern = buffer.toString();

        HttpHost target = new HttpHost("localhost", address.getPort());

        Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                    "POST", pattern + "x" + count);
            NStringEntity entity = NStringEntity.create(expectedPattern, ContentType.DEFAULT_TEXT);
            request.setEntity(entity);
            Future<HttpResponse> future = this.executor.execute(
                    new BasicAsyncRequestProducer(target, request),
                    new BasicAsyncResponseConsumer(),
                    this.connpool);
            queue.add(future);
        }

        Assert.assertEquals("Test client status", IOReactorStatus.ACTIVE, this.client.getStatus());

        while (!queue.isEmpty()) {
            Future<HttpResponse> future = queue.remove();
            HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(expectedPattern, EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testHttpPostsChunked() throws Exception {
        InetSocketAddress address = start();

        this.connpool.setDefaultMaxPerRoute(3);
        this.connpool.setMaxTotal(3);

        String pattern = RndTestPatternGenerator.generateText();
        int count = RndTestPatternGenerator.generateCount(1000);

        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < count; i++) {
            buffer.append(pattern);
        }
        String expectedPattern = buffer.toString();

        HttpHost target = new HttpHost("localhost", address.getPort());

        Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                    "POST", pattern + "x" + count);
            NStringEntity entity = NStringEntity.create(expectedPattern, ContentType.DEFAULT_TEXT);
            entity.setChunked(true);
            request.setEntity(entity);
            Future<HttpResponse> future = this.executor.execute(
                    new BasicAsyncRequestProducer(target, request),
                    new BasicAsyncResponseConsumer(),
                    this.connpool);
            queue.add(future);
        }

        Assert.assertEquals("Test client status", IOReactorStatus.ACTIVE, this.client.getStatus());

        while (!queue.isEmpty()) {
            Future<HttpResponse> future = queue.remove();
            HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(expectedPattern, EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testHttpPostsHTTP10() throws Exception {
        InetSocketAddress address = start();

        this.connpool.setDefaultMaxPerRoute(3);
        this.connpool.setMaxTotal(3);

        String pattern = RndTestPatternGenerator.generateText();
        int count = RndTestPatternGenerator.generateCount(1000);

        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < count; i++) {
            buffer.append(pattern);
        }
        String expectedPattern = buffer.toString();

        HttpHost target = new HttpHost("localhost", address.getPort());

        Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                    "POST", pattern + "x" + count, HttpVersion.HTTP_1_0);
            NStringEntity entity = NStringEntity.create(expectedPattern, ContentType.DEFAULT_TEXT);
            request.setEntity(entity);
            Future<HttpResponse> future = this.executor.execute(
                    new BasicAsyncRequestProducer(target, request),
                    new BasicAsyncResponseConsumer(),
                    this.connpool);
            queue.add(future);
        }

        Assert.assertEquals("Test client status", IOReactorStatus.ACTIVE, this.client.getStatus());

        while (!queue.isEmpty()) {
            Future<HttpResponse> future = queue.remove();
            HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(expectedPattern, EntityUtils.toString(response.getEntity()));
        }
    }

    @Test
    public void testHttpPostsWithExpectContinue() throws Exception {
        InetSocketAddress address = start();

        this.connpool.setDefaultMaxPerRoute(3);
        this.connpool.setMaxTotal(3);

        String pattern = RndTestPatternGenerator.generateText();
        int count = RndTestPatternGenerator.generateCount(1000);

        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < count; i++) {
            buffer.append(pattern);
        }
        String expectedPattern = buffer.toString();

        HttpHost target = new HttpHost("localhost", address.getPort());

        Queue<Future<HttpResponse>> queue = new ConcurrentLinkedQueue<Future<HttpResponse>>();
        for (int i = 0; i < 30; i++) {
            BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(
                    "POST", pattern + "x" + count);
            request.getParams().setBooleanParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, true);
            NStringEntity entity = NStringEntity.create(expectedPattern, ContentType.DEFAULT_TEXT);
            request.setEntity(entity);
            Future<HttpResponse> future = this.executor.execute(
                    new BasicAsyncRequestProducer(target, request),
                    new BasicAsyncResponseConsumer(),
                    this.connpool);
            queue.add(future);
        }

        Assert.assertEquals("Test client status", IOReactorStatus.ACTIVE, this.client.getStatus());

        while (!queue.isEmpty()) {
            Future<HttpResponse> future = queue.remove();
            HttpResponse response = future.get();
            Assert.assertNotNull(response);
            Assert.assertEquals(expectedPattern, EntityUtils.toString(response.getEntity()));
        }
    }

}

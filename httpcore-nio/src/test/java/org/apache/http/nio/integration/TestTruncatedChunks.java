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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.MalformedChunkCodingException;
import org.apache.http.TruncatedChunkException;
import org.apache.http.entity.ContentLengthStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.nio.DefaultNHttpServerConnection;
import org.apache.http.impl.nio.codecs.AbstractContentEncoder;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.entity.ContentInputStream;
import org.apache.http.nio.protocol.AbstractAsyncResponseConsumer;
import org.apache.http.nio.protocol.BasicAsyncRequestHandler;
import org.apache.http.nio.protocol.BasicAsyncRequestProducer;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.nio.reactor.SessionOutputBuffer;
import org.apache.http.nio.testserver.HttpCoreNIOTestBase;
import org.apache.http.nio.testserver.LoggingNHttpServerConnection;
import org.apache.http.nio.testserver.ServerConnectionFactory;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.nio.util.SimpleInputBuffer;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.CharArrayBuffer;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for handling truncated chunks.
 */
public class TestTruncatedChunks extends HttpCoreNIOTestBase {

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

    @Override
    protected ServerConnectionFactory createServerConnectionFactory() throws Exception {
        return new CustomServerConnectionFactory();
    }

    private static final byte[] GARBAGE = new byte[] {'1', '2', '3', '4', '5' };

    static class BrokenChunkEncoder extends AbstractContentEncoder {

        private final CharArrayBuffer lineBuffer;
        private boolean done;

        public BrokenChunkEncoder(
                final WritableByteChannel channel,
                final SessionOutputBuffer buffer,
                final HttpTransportMetricsImpl metrics) {
            super(channel, buffer, metrics);
            this.lineBuffer = new CharArrayBuffer(16);
        }

        @Override
        public void complete() throws IOException {
            super.complete();
        }

        @Override
        public int write(final ByteBuffer src) throws IOException {
            final int chunk;
            if (!this.done) {
                this.lineBuffer.clear();
                this.lineBuffer.append(Integer.toHexString(GARBAGE.length * 10));
                this.buffer.writeLine(this.lineBuffer);
                this.buffer.write(ByteBuffer.wrap(GARBAGE));
                this.done = true;
                chunk = GARBAGE.length;
            } else {
                chunk = 0;
            }
            final long bytesWritten = this.buffer.flush(this.channel);
            if (bytesWritten > 0) {
                this.metrics.incrementBytesTransferred(bytesWritten);
            }
            if (!this.buffer.hasData()) {
                this.channel.close();
            }
            return chunk;
        }

    }

    static class CustomServerConnectionFactory extends ServerConnectionFactory {

        public CustomServerConnectionFactory() {
            super();
        }

        @Override
        public DefaultNHttpServerConnection createConnection(final IOSession session) {
            return new LoggingNHttpServerConnection(session) {

                @Override
                protected ContentEncoder createContentEncoder(
                        final long len,
                        final WritableByteChannel channel,
                        final SessionOutputBuffer buffer,
                        final HttpTransportMetricsImpl metrics) {
                    if (len == ContentLengthStrategy.CHUNKED) {
                        return new BrokenChunkEncoder(channel, buffer, metrics);
                    } else {
                        return super.createContentEncoder(len, channel, buffer, metrics);
                    }
                }

            };
        }

    }

    @Test
    public void testTruncatedChunkException() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler(true)));
        this.server.start();
        this.client.start();

        final ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final HttpHost target = new HttpHost("localhost", ((InetSocketAddress)endpoint.getAddress()).getPort());
        final BasicHttpRequest request = new BasicHttpRequest("GET", pattern + "x" + count);
        final Future<HttpResponse> future = this.client.execute(target, request);
        try {
            future.get();
            Assert.fail("ExecutionException should have been thrown");
        } catch (final ExecutionException ex) {
            final Throwable cause = ex.getCause();
            Assert.assertTrue(cause instanceof MalformedChunkCodingException);
        }
    }

    static class LenientAsyncResponseConsumer extends AbstractAsyncResponseConsumer<HttpResponse> {

        private final SimpleInputBuffer buffer;
        private volatile HttpResponse response;

        public LenientAsyncResponseConsumer() {
            super();
            this.buffer = new SimpleInputBuffer(2048, HeapByteBufferAllocator.INSTANCE);
        }

        @Override
        protected void onResponseReceived(final HttpResponse response) {
            this.response = response;
        }

        @Override
        protected void onEntityEnclosed(final HttpEntity entity, final ContentType contentType) {
        }

        @Override
        protected void onContentReceived(
                final ContentDecoder decoder, final IOControl ioctrl) throws IOException {
            boolean finished = false;
            try {
                this.buffer.consumeContent(decoder);
                if (decoder.isCompleted()) {
                    finished = true;
                }
            } catch (final TruncatedChunkException ex) {
                this.buffer.shutdown();
                finished = true;
            }
            if (finished) {
                this.response.setEntity(
                        new InputStreamEntity(new ContentInputStream(this.buffer), -1));
            }
        }

        @Override
        protected void releaseResources() {
        }

        @Override
        protected HttpResponse buildResult(final HttpContext context) {
            return this.response;
        }

    }

    @Test
    public void testIgnoreTruncatedChunkException() throws Exception {
        this.server.registerHandler("*", new BasicAsyncRequestHandler(new SimpleRequestHandler(true)));
        this.server.start();
        this.client.start();

        final ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();

        final String pattern = RndTestPatternGenerator.generateText();
        final int count = RndTestPatternGenerator.generateCount(1000);

        final HttpHost target = new HttpHost("localhost", ((InetSocketAddress)endpoint.getAddress()).getPort());
        final BasicHttpRequest request = new BasicHttpRequest("GET", pattern + "x" + count);
        final Future<HttpResponse> future = this.client.execute(
                new BasicAsyncRequestProducer(target, request),
                new LenientAsyncResponseConsumer(),
                null, null);

        final HttpResponse response = future.get();
        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
        Assert.assertEquals(new String(GARBAGE, Consts.ISO_8859_1.name()),
                EntityUtils.toString(response.getEntity()));
    }

}

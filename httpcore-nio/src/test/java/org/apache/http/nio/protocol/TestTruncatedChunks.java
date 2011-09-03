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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.http.HttpCoreNIOTestBase;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestFactory;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.MalformedChunkCodingException;
import org.apache.http.TruncatedChunkException;
import org.apache.http.entity.ContentLengthStrategy;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.nio.DefaultNHttpClientConnectionFactory;
import org.apache.http.impl.nio.DefaultNHttpServerConnection;
import org.apache.http.impl.nio.DefaultNHttpServerConnectionFactory;
import org.apache.http.impl.nio.codecs.AbstractContentEncoder;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.NHttpClientIOTarget;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.NHttpServerIOTarget;
import org.apache.http.nio.entity.ConsumingNHttpEntity;
import org.apache.http.nio.entity.ContentInputStream;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.nio.reactor.SessionOutputBuffer;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.nio.util.SimpleInputBuffer;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.testserver.SimpleEventListener;
import org.apache.http.testserver.SimpleNHttpRequestHandlerResolver;
import org.apache.http.util.CharArrayBuffer;
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
    protected NHttpConnectionFactory<NHttpServerIOTarget> createServerConnectionFactory(
            final HttpParams params) {
        return new CustomServerConnectionFactory(params);
    }

    @Override
    protected NHttpConnectionFactory<NHttpClientIOTarget> createClientConnectionFactory(
            final HttpParams params) {
        return new DefaultNHttpClientConnectionFactory(params);
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
            this.completed = true;
        }

        public int write(ByteBuffer src) throws IOException {
            int chunk;
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
            long bytesWritten = this.buffer.flush(this.channel);
            if (bytesWritten > 0) {
                this.metrics.incrementBytesTransferred(bytesWritten);
            }
            if (!this.buffer.hasData()) {
                this.channel.close();
            }
            return chunk;
        }

    }

    static class CustomServerConnectionFactory extends DefaultNHttpServerConnectionFactory {

        public CustomServerConnectionFactory(final HttpParams params) {
            super(params);
        }

        @Override
        protected NHttpServerIOTarget createConnection(
                final IOSession session,
                final HttpRequestFactory requestFactory,
                final ByteBufferAllocator allocator,
                final HttpParams params) {

            return new DefaultNHttpServerConnection(session, requestFactory, allocator, params) {

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

        NHttpRequestExecutionHandler requestExecutionHandler = new RequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(Job testjob) {
                String s = testjob.getPattern() + "x" + testjob.getCount();
                return new BasicHttpRequest("GET", s);
            }

        };

        Job testjob = new Job(2000);
        Queue<Job> queue = new ConcurrentLinkedQueue<Job>();
        queue.add(testjob);

        AsyncNHttpServiceHandler serviceHandler = new AsyncNHttpServiceHandler(
                this.serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.serverParams);

        serviceHandler.setHandlerResolver(
                new SimpleNHttpRequestHandlerResolver(new RequestHandler(true)));
        serviceHandler.setEventListener(
                new SimpleEventListener());

        AsyncNHttpClientHandler clientHandler = new AsyncNHttpClientHandler(
                this.clientHttpProc,
                requestExecutionHandler,
                new DefaultConnectionReuseStrategy(),
                this.clientParams);

        clientHandler.setEventListener(
                new SimpleEventListener() {

                    @Override
                    public void fatalIOException(
                            final IOException ex,
                            final NHttpConnection conn) {
                        HttpContext context = conn.getContext();
                        Job testjob = (Job) context.getAttribute("job");
                        testjob.fail(ex.getMessage(), ex);
                    }

                });

        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        this.client.openConnection(
                new InetSocketAddress("localhost", serverAddress.getPort()),
                queue);

        testjob.waitFor();
        Assert.assertFalse(testjob.isSuccessful());
        Assert.assertNotNull(testjob.getException());
        Assert.assertTrue(testjob.getException() instanceof MalformedChunkCodingException);
    }

    static class LenientNHttpEntity extends HttpEntityWrapper implements ConsumingNHttpEntity {

        private final static int BUFFER_SIZE = 2048;

        private final SimpleInputBuffer buffer;
        private boolean finished;
        private boolean consumed;

        public LenientNHttpEntity(
                final HttpEntity httpEntity,
                final ByteBufferAllocator allocator) {
            super(httpEntity);
            this.buffer = new SimpleInputBuffer(BUFFER_SIZE, allocator);
        }

        public void consumeContent(
                final ContentDecoder decoder,
                final IOControl ioctrl) throws IOException {
            try {
                this.buffer.consumeContent(decoder);
                if (decoder.isCompleted()) {
                    this.finished = true;
                }
            } catch (TruncatedChunkException ex) {
                this.buffer.shutdown();
                this.finished = true;
            }
        }

        public void finish() {
            this.finished = true;
        }

        @Override
        public void consumeContent() throws IOException {
        }

        @Override
        public InputStream getContent() throws IOException {
            if (!this.finished) {
                throw new IllegalStateException("Entity content has not been fully received");
            }
            if (this.consumed) {
                throw new IllegalStateException("Entity content has been consumed");
            }
            this.consumed = true;
            return new ContentInputStream(this.buffer);
        }

        @Override
        public boolean isRepeatable() {
            return false;
        }

        @Override
        public boolean isStreaming() {
            return true;
        }

        @Override
        public void writeTo(final OutputStream outstream) throws IOException {
            if (outstream == null) {
                throw new IllegalArgumentException("Output stream may not be null");
            }
            InputStream instream = getContent();
            byte[] buffer = new byte[BUFFER_SIZE];
            int l;
            // consume until EOF
            while ((l = instream.read(buffer)) != -1) {
                outstream.write(buffer, 0, l);
            }
        }

    }

    @Test
    public void testIgnoreTruncatedChunkException() throws Exception {

        NHttpRequestExecutionHandler requestExecutionHandler = new RequestExecutionHandler() {

            @Override
            protected HttpRequest generateRequest(final Job testjob) {
                String s = testjob.getPattern() + "x" + testjob.getCount();
                return new BasicHttpRequest("GET", s);
            }

            @Override
            public ConsumingNHttpEntity responseEntity(
                    final HttpResponse response,
                    final HttpContext context) throws IOException {
                return new LenientNHttpEntity(response.getEntity(),
                        new HeapByteBufferAllocator());
            }

        };

        Job testjob = new Job(2000);
        Queue<Job> queue = new ConcurrentLinkedQueue<Job>();
        queue.add(testjob);

        AsyncNHttpServiceHandler serviceHandler = new AsyncNHttpServiceHandler(
                this.serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.serverParams);

        serviceHandler.setHandlerResolver(
                new SimpleNHttpRequestHandlerResolver(new RequestHandler(true)));
        serviceHandler.setEventListener(
                new SimpleEventListener());

        AsyncNHttpClientHandler clientHandler = new AsyncNHttpClientHandler(
                this.clientHttpProc,
                requestExecutionHandler,
                new DefaultConnectionReuseStrategy(),
                this.clientParams);

        clientHandler.setEventListener(
                new SimpleEventListener());

        this.server.start(serviceHandler);
        this.client.start(clientHandler);

        ListenerEndpoint endpoint = this.server.getListenerEndpoint();
        endpoint.waitFor();
        InetSocketAddress serverAddress = (InetSocketAddress) endpoint.getAddress();

        this.client.openConnection(
                new InetSocketAddress("localhost", serverAddress.getPort()),
                queue);

        testjob.waitFor();
        if (testjob.isSuccessful()) {
            Assert.assertEquals(HttpStatus.SC_OK, testjob.getStatusCode());
            Assert.assertEquals(new String(GARBAGE, "US-ASCII"), testjob.getResult());
        } else {
            Assert.fail(testjob.getFailureMessage());
        }
    }

}

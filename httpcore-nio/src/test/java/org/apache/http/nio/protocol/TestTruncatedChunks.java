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
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import junit.framework.TestCase;

import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpStatus;
import org.apache.http.MalformedChunkCodingException;
import org.apache.http.TruncatedChunkException;
import org.apache.http.entity.ContentLengthStrategy;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.nio.DefaultNHttpServerConnection;
import org.apache.http.impl.nio.DefaultServerIOEventDispatch;
import org.apache.http.impl.nio.codecs.AbstractContentEncoder;
import org.apache.http.impl.nio.reactor.ExceptionEvent;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.mockup.SimpleEventListener;
import org.apache.http.mockup.SimpleNHttpRequestHandlerResolver;
import org.apache.http.mockup.HttpClientNio;
import org.apache.http.mockup.HttpServerNio;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.NHttpServerIOTarget;
import org.apache.http.nio.NHttpServiceHandler;
import org.apache.http.nio.entity.ConsumingNHttpEntity;
import org.apache.http.nio.entity.ContentInputStream;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.nio.reactor.SessionOutputBuffer;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.nio.util.SimpleInputBuffer;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.util.CharArrayBuffer;

/**
 * Tests for handling truncated chunks.
 */
public class TestTruncatedChunks extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestTruncatedChunks(String testName) {
        super(testName);
    }

    // ------------------------------------------------------- TestCase Methods

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

    static class CustomServerIOEventDispatch extends DefaultServerIOEventDispatch {

        public CustomServerIOEventDispatch(
                final NHttpServiceHandler handler,
                final HttpParams params) {
            super(handler, params);
        }

        @Override
        protected NHttpServerIOTarget createConnection(final IOSession session) {

            return new DefaultNHttpServerConnection(
                    session,
                    createHttpRequestFactory(),
                    this.allocator,
                    this.params) {

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

    static class CustomTestHttpServer extends HttpServerNio {

        public CustomTestHttpServer(final HttpParams params) throws IOException {
            super(params);
        }

        @Override
        protected IOEventDispatch createIOEventDispatch(
                NHttpServiceHandler serviceHandler, HttpParams params) {
            return new CustomServerIOEventDispatch(serviceHandler, params);
        }

    }

    protected CustomTestHttpServer server;
    protected HttpClientNio client;

    @Override
    protected void setUp() throws Exception {
        HttpParams serverParams = new SyncBasicHttpParams();
        serverParams
            .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 60000)
            .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
            .setParameter(CoreProtocolPNames.ORIGIN_SERVER, "TEST-SERVER/1.1");

        this.server = new CustomTestHttpServer(serverParams);

        HttpParams clientParams = new SyncBasicHttpParams();
        clientParams
            .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 60000)
            .setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 30000)
            .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
            .setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
            .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
            .setParameter(CoreProtocolPNames.USER_AGENT, "TEST-CLIENT/1.1");

        this.client = new HttpClientNio(clientParams);
    }

    @Override
    protected void tearDown() {
        try {
            this.client.shutdown();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        List<ExceptionEvent> clogs = this.client.getAuditLog();
        if (clogs != null) {
            for (ExceptionEvent clog: clogs) {
                Throwable cause = clog.getCause();
                cause.printStackTrace();
            }
        }

        try {
            this.server.shutdown();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        List<ExceptionEvent> slogs = this.server.getAuditLog();
        if (slogs != null) {
            for (ExceptionEvent slog: slogs) {
                Throwable cause = slog.getCause();
                cause.printStackTrace();
            }
        }
    }

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

        HttpProcessor serverHttpProc = new ImmutableHttpProcessor(new HttpResponseInterceptor[] {
                new ResponseDate(),
                new ResponseServer(),
                new ResponseContent(),
                new ResponseConnControl()
        });

        AsyncNHttpServiceHandler serviceHandler = new AsyncNHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.server.getParams());

        serviceHandler.setHandlerResolver(
                new SimpleNHttpRequestHandlerResolver(new RequestHandler(true)));
        serviceHandler.setEventListener(
                new SimpleEventListener());

        HttpProcessor clientHttpProc = new ImmutableHttpProcessor(new HttpRequestInterceptor[] {
                new RequestContent(),
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent(),
                new RequestExpectContinue()});

        AsyncNHttpClientHandler clientHandler = new AsyncNHttpClientHandler(
                clientHttpProc,
                requestExecutionHandler,
                new DefaultConnectionReuseStrategy(),
                this.client.getParams());

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
        assertFalse(testjob.isSuccessful());
        assertNotNull(testjob.getException());
        assertTrue(testjob.getException() instanceof MalformedChunkCodingException);
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

        HttpProcessor serverHttpProc = new ImmutableHttpProcessor(new HttpResponseInterceptor[] {
                new ResponseDate(),
                new ResponseServer(),
                new ResponseContent(),
                new ResponseConnControl()
        });

        AsyncNHttpServiceHandler serviceHandler = new AsyncNHttpServiceHandler(
                serverHttpProc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                this.server.getParams());

        serviceHandler.setHandlerResolver(
                new SimpleNHttpRequestHandlerResolver(new RequestHandler(true)));
        serviceHandler.setEventListener(
                new SimpleEventListener());

        HttpProcessor clientHttpProc = new ImmutableHttpProcessor(new HttpRequestInterceptor[] {
                new RequestContent(),
                new RequestTargetHost(),
                new RequestConnControl(),
                new RequestUserAgent(),
                new RequestExpectContinue()});

        AsyncNHttpClientHandler clientHandler = new AsyncNHttpClientHandler(
                clientHttpProc,
                requestExecutionHandler,
                new DefaultConnectionReuseStrategy(),
                this.client.getParams());

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
            assertEquals(HttpStatus.SC_OK, testjob.getStatusCode());
            assertEquals(new String(GARBAGE, "US-ASCII"), testjob.getResult());
        } else {
            fail(testjob.getFailureMessage());
        }
    }

}

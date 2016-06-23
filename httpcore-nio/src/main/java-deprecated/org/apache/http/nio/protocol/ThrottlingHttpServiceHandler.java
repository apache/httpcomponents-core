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
import java.io.OutputStream;
import java.util.concurrent.Executor;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.ProtocolException;
import org.apache.http.ProtocolVersion;
import org.apache.http.UnsupportedHttpVersionException;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.annotation.Contract;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.NHttpServiceHandler;
import org.apache.http.nio.entity.ContentBufferEntity;
import org.apache.http.nio.entity.ContentOutputStream;
import org.apache.http.nio.params.NIOReactorPNames;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.ContentInputBuffer;
import org.apache.http.nio.util.ContentOutputBuffer;
import org.apache.http.nio.util.DirectByteBufferAllocator;
import org.apache.http.nio.util.SharedInputBuffer;
import org.apache.http.nio.util.SharedOutputBuffer;
import org.apache.http.params.DefaultedHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExpectationVerifier;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerResolver;
import org.apache.http.util.Args;
import org.apache.http.util.EncodingUtils;
import org.apache.http.util.EntityUtils;

/**
 * Service protocol handler implementation that provide compatibility with
 * the blocking I/O by utilizing shared content buffers and a fairly small pool
 * of worker threads. The throttling protocol handler allocates input / output
 * buffers of a constant length upon initialization and controls the rate of
 * I/O events in order to ensure those content buffers do not ever get
 * overflown. This helps ensure nearly constant memory footprint for HTTP
 * connections and avoid the out of memory condition while streaming content
 * in and out. The {@link HttpRequestHandler#handle(HttpRequest, HttpResponse, HttpContext)}
 * method will fire immediately when a message is received. The protocol handler
 * delegate the task of processing requests and generating response content to
 * an {@link Executor}, which is expected to perform those tasks using
 * dedicated worker threads in order to avoid blocking the I/O thread.
 * <p>
 * Usually throttling protocol handlers need only a modest number of worker
 * threads, much fewer than the number of concurrent connections. If the length
 * of the message is smaller or about the size of the shared content buffer
 * worker thread will just store content in the buffer and terminate almost
 * immediately without blocking. The I/O dispatch thread in its turn will take
 * care of sending out the buffered content asynchronously. The worker thread
 * will have to block only when processing large messages and the shared buffer
 * fills up. It is generally advisable to allocate shared buffers of a size of
 * an average content body for optimal performance.
 * <p>
 * The following parameters can be used to customize the behavior of this
 * class:
 * <ul>
 *  <li>{@link org.apache.http.nio.params.NIOReactorPNames#CONTENT_BUFFER_SIZE}</li>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#WAIT_FOR_CONTINUE}</li>
 * </ul>
 *
 * @since 4.0
 *
 * @deprecated (4.2) use {@link HttpAsyncService}
 */
@Deprecated
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
public class ThrottlingHttpServiceHandler extends NHttpHandlerBase
                                          implements NHttpServiceHandler {

    protected final HttpResponseFactory responseFactory;
    protected final Executor executor;

    protected HttpRequestHandlerResolver handlerResolver;
    protected HttpExpectationVerifier expectationVerifier;

    private final int bufsize;

    public ThrottlingHttpServiceHandler(
            final HttpProcessor httpProcessor,
            final HttpResponseFactory responseFactory,
            final ConnectionReuseStrategy connStrategy,
            final ByteBufferAllocator allocator,
            final Executor executor,
            final HttpParams params) {
        super(httpProcessor, connStrategy, allocator, params);
        Args.notNull(responseFactory, "Response factory");
        Args.notNull(executor, "Executor");
        this.responseFactory = responseFactory;
        this.executor = executor;
        this.bufsize = this.params.getIntParameter(NIOReactorPNames.CONTENT_BUFFER_SIZE, 20480);
    }

    public ThrottlingHttpServiceHandler(
            final HttpProcessor httpProcessor,
            final HttpResponseFactory responseFactory,
            final ConnectionReuseStrategy connStrategy,
            final Executor executor,
            final HttpParams params) {
        this(httpProcessor, responseFactory, connStrategy,
                DirectByteBufferAllocator.INSTANCE, executor, params);
    }

    public void setHandlerResolver(final HttpRequestHandlerResolver handlerResolver) {
        this.handlerResolver = handlerResolver;
    }

    public void setExpectationVerifier(final HttpExpectationVerifier expectationVerifier) {
        this.expectationVerifier = expectationVerifier;
    }

    @Override
    public void connected(final NHttpServerConnection conn) {
        final HttpContext context = conn.getContext();

        final ServerConnState connState = new ServerConnState(this.bufsize, conn, allocator);
        context.setAttribute(CONN_STATE, connState);

        if (this.eventListener != null) {
            this.eventListener.connectionOpen(conn);
        }
    }

    @Override
    public void closed(final NHttpServerConnection conn) {
        final HttpContext context = conn.getContext();
        final ServerConnState connState = (ServerConnState) context.getAttribute(CONN_STATE);

        if (connState != null) {
            synchronized (connState) {
                connState.close();
                connState.notifyAll();
            }
        }

        if (this.eventListener != null) {
            this.eventListener.connectionClosed(conn);
        }
    }

    @Override
    public void exception(final NHttpServerConnection conn, final HttpException httpex) {
        if (conn.isResponseSubmitted()) {
            if (eventListener != null) {
                eventListener.fatalProtocolException(httpex, conn);
            }
            return;
        }

        final HttpContext context = conn.getContext();

        final ServerConnState connState = (ServerConnState) context.getAttribute(CONN_STATE);

        try {

            final HttpResponse response = this.responseFactory.newHttpResponse(
                    HttpVersion.HTTP_1_0,
                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    context);
            response.setParams(
                    new DefaultedHttpParams(response.getParams(), this.params));
            handleException(httpex, response);
            response.setEntity(null);

            this.httpProcessor.process(response, context);

            synchronized (connState) {
                connState.setResponse(response);
                // Response is ready to be committed
                conn.requestOutput();
            }

        } catch (final IOException ex) {
            shutdownConnection(conn, ex);
            if (eventListener != null) {
                eventListener.fatalIOException(ex, conn);
            }
        } catch (final HttpException ex) {
            closeConnection(conn, ex);
            if (eventListener != null) {
                eventListener.fatalProtocolException(ex, conn);
            }
        }
    }

    @Override
    public void exception(final NHttpServerConnection conn, final IOException ex) {
        shutdownConnection(conn, ex);

        if (this.eventListener != null) {
            this.eventListener.fatalIOException(ex, conn);
        }
    }

    @Override
    public void timeout(final NHttpServerConnection conn) {
        handleTimeout(conn);
    }

    @Override
    public void requestReceived(final NHttpServerConnection conn) {
        final HttpContext context = conn.getContext();

        final HttpRequest request = conn.getHttpRequest();
        final ServerConnState connState = (ServerConnState) context.getAttribute(CONN_STATE);

        synchronized (connState) {
            boolean contentExpected = false;
            if (request instanceof HttpEntityEnclosingRequest) {
                final HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
                if (entity != null) {
                    contentExpected = true;
                }
            }

            if (!contentExpected) {
                conn.suspendInput();
            }

            this.executor.execute(new Runnable() {

                @Override
                public void run() {
                    try {

                        handleRequest(request, connState, conn);

                    } catch (final IOException ex) {
                        shutdownConnection(conn, ex);
                        if (eventListener != null) {
                            eventListener.fatalIOException(ex, conn);
                        }
                    } catch (final HttpException ex) {
                        shutdownConnection(conn, ex);
                        if (eventListener != null) {
                            eventListener.fatalProtocolException(ex, conn);
                        }
                    }
                }

            });

            connState.notifyAll();
        }

    }

    @Override
    public void inputReady(final NHttpServerConnection conn, final ContentDecoder decoder) {
        final HttpContext context = conn.getContext();

        final ServerConnState connState = (ServerConnState) context.getAttribute(CONN_STATE);

        try {

            synchronized (connState) {
                final ContentInputBuffer buffer = connState.getInbuffer();

                buffer.consumeContent(decoder);
                if (decoder.isCompleted()) {
                    connState.setInputState(ServerConnState.REQUEST_BODY_DONE);
                } else {
                    connState.setInputState(ServerConnState.REQUEST_BODY_STREAM);
                }

                connState.notifyAll();
            }

        } catch (final IOException ex) {
            shutdownConnection(conn, ex);
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex, conn);
            }
        }

    }

    @Override
    public void responseReady(final NHttpServerConnection conn) {
        final HttpContext context = conn.getContext();

        final ServerConnState connState = (ServerConnState) context.getAttribute(CONN_STATE);

        try {

            synchronized (connState) {
                if (connState.isExpectationFailed()) {
                    // Server expection failed
                    // Well-behaved client will not be sending
                    // a request body
                    conn.resetInput();
                    connState.setExpectationFailed(false);
                }

                final HttpResponse response = connState.getResponse();
                if (connState.getOutputState() == ServerConnState.READY
                        && response != null
                        && !conn.isResponseSubmitted()) {

                    conn.submitResponse(response);
                    final int statusCode = response.getStatusLine().getStatusCode();
                    final HttpEntity entity = response.getEntity();

                    if (statusCode >= 200 && entity == null) {
                        connState.setOutputState(ServerConnState.RESPONSE_DONE);

                        if (!this.connStrategy.keepAlive(response, context)) {
                            conn.close();
                        }
                    } else {
                        connState.setOutputState(ServerConnState.RESPONSE_SENT);
                    }
                }

                connState.notifyAll();
            }

        } catch (final IOException ex) {
            shutdownConnection(conn, ex);
            if (eventListener != null) {
                eventListener.fatalIOException(ex, conn);
            }
        } catch (final HttpException ex) {
            closeConnection(conn, ex);
            if (eventListener != null) {
                eventListener.fatalProtocolException(ex, conn);
            }
        }
    }

    @Override
    public void outputReady(final NHttpServerConnection conn, final ContentEncoder encoder) {
        final HttpContext context = conn.getContext();

        final ServerConnState connState = (ServerConnState) context.getAttribute(CONN_STATE);

        try {

            synchronized (connState) {
                final HttpResponse response = connState.getResponse();
                final ContentOutputBuffer buffer = connState.getOutbuffer();

                buffer.produceContent(encoder);
                if (encoder.isCompleted()) {
                    connState.setOutputState(ServerConnState.RESPONSE_BODY_DONE);

                    if (!this.connStrategy.keepAlive(response, context)) {
                        conn.close();
                    }
                } else {
                    connState.setOutputState(ServerConnState.RESPONSE_BODY_STREAM);
                }

                connState.notifyAll();
            }

        } catch (final IOException ex) {
            shutdownConnection(conn, ex);
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex, conn);
            }
        }
    }

    private void handleException(final HttpException ex, final HttpResponse response) {
        if (ex instanceof MethodNotSupportedException) {
            response.setStatusCode(HttpStatus.SC_NOT_IMPLEMENTED);
        } else if (ex instanceof UnsupportedHttpVersionException) {
            response.setStatusCode(HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED);
        } else if (ex instanceof ProtocolException) {
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        } else {
            response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        final byte[] msg = EncodingUtils.getAsciiBytes(ex.getMessage());
        final ByteArrayEntity entity = new ByteArrayEntity(msg);
        entity.setContentType("text/plain; charset=US-ASCII");
        response.setEntity(entity);
    }

    private void handleRequest(
            final HttpRequest request,
            final ServerConnState connState,
            final NHttpServerConnection conn) throws HttpException, IOException {

        final HttpContext context = conn.getContext();

        // Block until previous request is fully processed and
        // the worker thread no longer holds the shared buffer
        synchronized (connState) {
            try {
                for (;;) {
                    final int currentState = connState.getOutputState();
                    if (currentState == ServerConnState.READY) {
                        break;
                    }
                    if (currentState == ServerConnState.SHUTDOWN) {
                        return;
                    }
                    connState.wait();
                }
            } catch (final InterruptedException ex) {
                connState.shutdown();
                return;
            }
            connState.setInputState(ServerConnState.REQUEST_RECEIVED);
            connState.setRequest(request);
        }

        request.setParams(new DefaultedHttpParams(request.getParams(), this.params));

        context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        context.setAttribute(ExecutionContext.HTTP_REQUEST, request);

        ProtocolVersion ver = request.getRequestLine().getProtocolVersion();

        if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
            // Downgrade protocol version if greater than HTTP/1.1
            ver = HttpVersion.HTTP_1_1;
        }

        HttpResponse response = null;

        if (request instanceof HttpEntityEnclosingRequest) {
            final HttpEntityEnclosingRequest eeRequest = (HttpEntityEnclosingRequest) request;

            if (eeRequest.expectContinue()) {
                response = this.responseFactory.newHttpResponse(
                        ver,
                        HttpStatus.SC_CONTINUE,
                        context);
                response.setParams(
                        new DefaultedHttpParams(response.getParams(), this.params));
                if (this.expectationVerifier != null) {
                    try {
                        this.expectationVerifier.verify(request, response, context);
                    } catch (final HttpException ex) {
                        response = this.responseFactory.newHttpResponse(HttpVersion.HTTP_1_0,
                                HttpStatus.SC_INTERNAL_SERVER_ERROR, context);
                        response.setParams(
                                new DefaultedHttpParams(response.getParams(), this.params));
                        handleException(ex, response);
                    }
                }

                synchronized (connState) {
                    if (response.getStatusLine().getStatusCode() < 200) {
                        // Send 1xx response indicating the server expections
                        // have been met
                        connState.setResponse(response);
                        conn.requestOutput();

                        // Block until 1xx response is sent to the client
                        try {
                            for (;;) {
                                final int currentState = connState.getOutputState();
                                if (currentState == ServerConnState.RESPONSE_SENT) {
                                    break;
                                }
                                if (currentState == ServerConnState.SHUTDOWN) {
                                    return;
                                }
                                connState.wait();
                            }
                        } catch (final InterruptedException ex) {
                            connState.shutdown();
                            return;
                        }
                        connState.resetOutput();
                        response = null;
                    } else {
                        // Discard request entity
                        eeRequest.setEntity(null);
                        conn.suspendInput();
                        connState.setExpectationFailed(true);
                    }
                }
            }

            // Create a wrapper entity instead of the original one
            if (eeRequest.getEntity() != null) {
                eeRequest.setEntity(new ContentBufferEntity(
                        eeRequest.getEntity(),
                        connState.getInbuffer()));
            }

        }

        if (response == null) {
            response = this.responseFactory.newHttpResponse(
                    ver,
                    HttpStatus.SC_OK,
                    context);
            response.setParams(
                    new DefaultedHttpParams(response.getParams(), this.params));

            context.setAttribute(ExecutionContext.HTTP_RESPONSE, response);

            try {

                this.httpProcessor.process(request, context);

                HttpRequestHandler handler = null;
                if (this.handlerResolver != null) {
                    final String requestURI = request.getRequestLine().getUri();
                    handler = this.handlerResolver.lookup(requestURI);
                }
                if (handler != null) {
                    handler.handle(request, response, context);
                } else {
                    response.setStatusCode(HttpStatus.SC_NOT_IMPLEMENTED);
                }

            } catch (final HttpException ex) {
                response = this.responseFactory.newHttpResponse(HttpVersion.HTTP_1_0,
                        HttpStatus.SC_INTERNAL_SERVER_ERROR, context);
                response.setParams(
                        new DefaultedHttpParams(response.getParams(), this.params));
                handleException(ex, response);
            }
        }

        if (request instanceof HttpEntityEnclosingRequest) {
            final HttpEntityEnclosingRequest eeRequest = (HttpEntityEnclosingRequest) request;
            final HttpEntity entity = eeRequest.getEntity();
            EntityUtils.consume(entity);
        }

        // It should be safe to reset the input state at this point
        connState.resetInput();

        this.httpProcessor.process(response, context);

        if (!canResponseHaveBody(request, response)) {
            response.setEntity(null);
        }

        connState.setResponse(response);
        // Response is ready to be committed
        conn.requestOutput();

        if (response.getEntity() != null) {
            final ContentOutputBuffer buffer = connState.getOutbuffer();
            final OutputStream outstream = new ContentOutputStream(buffer);

            final HttpEntity entity = response.getEntity();
            entity.writeTo(outstream);
            outstream.flush();
            outstream.close();
        }

        synchronized (connState) {
            try {
                for (;;) {
                    final int currentState = connState.getOutputState();
                    if (currentState == ServerConnState.RESPONSE_DONE) {
                        break;
                    }
                    if (currentState == ServerConnState.SHUTDOWN) {
                        return;
                    }
                    connState.wait();
                }
            } catch (final InterruptedException ex) {
                connState.shutdown();
                return;
            }
            connState.resetOutput();
            conn.requestInput();
            connState.notifyAll();
        }
    }

    @Override
    protected void shutdownConnection(final NHttpConnection conn, final Throwable cause) {
        final HttpContext context = conn.getContext();

        final ServerConnState connState = (ServerConnState) context.getAttribute(CONN_STATE);

        super.shutdownConnection(conn, cause);

        if (connState != null) {
            connState.shutdown();
        }
    }

    static class ServerConnState {

        public static final int SHUTDOWN                   = -1;
        public static final int READY                      = 0;
        public static final int REQUEST_RECEIVED           = 1;
        public static final int REQUEST_BODY_STREAM        = 2;
        public static final int REQUEST_BODY_DONE          = 4;
        public static final int RESPONSE_SENT              = 8;
        public static final int RESPONSE_BODY_STREAM       = 16;
        public static final int RESPONSE_BODY_DONE         = 32;
        public static final int RESPONSE_DONE              = 32;

        private final SharedInputBuffer inbuffer;
        private final SharedOutputBuffer outbuffer;

        private volatile int inputState;
        private volatile int outputState;

        private volatile HttpRequest request;
        private volatile HttpResponse response;

        private volatile boolean expectationFailure;

        public ServerConnState(
                final int bufsize,
                final IOControl ioControl,
                final ByteBufferAllocator allocator) {
            super();
            this.inbuffer = new SharedInputBuffer(bufsize, ioControl, allocator);
            this.outbuffer = new SharedOutputBuffer(bufsize, ioControl, allocator);
            this.inputState = READY;
            this.outputState = READY;
        }

        public ContentInputBuffer getInbuffer() {
            return this.inbuffer;
        }

        public ContentOutputBuffer getOutbuffer() {
            return this.outbuffer;
        }

        public int getInputState() {
            return this.inputState;
        }

        public void setInputState(final int inputState) {
            this.inputState = inputState;
        }

        public int getOutputState() {
            return this.outputState;
        }

        public void setOutputState(final int outputState) {
            this.outputState = outputState;
        }

        public HttpRequest getRequest() {
            return this.request;
        }

        public void setRequest(final HttpRequest request) {
            this.request = request;
        }

        public HttpResponse getResponse() {
            return this.response;
        }

        public void setResponse(final HttpResponse response) {
            this.response = response;
        }

        public boolean isExpectationFailed() {
            return expectationFailure;
        }

        public void setExpectationFailed(final boolean b) {
            this.expectationFailure = b;
        }

        public void close() {
            this.inbuffer.close();
            this.outbuffer.close();
            this.inputState = SHUTDOWN;
            this.outputState = SHUTDOWN;
        }

        public void shutdown() {
            this.inbuffer.shutdown();
            this.outbuffer.shutdown();
            this.inputState = SHUTDOWN;
            this.outputState = SHUTDOWN;
        }

        public void resetInput() {
            this.inbuffer.reset();
            this.request = null;
            this.inputState = READY;
        }

        public void resetOutput() {
            this.outbuffer.reset();
            this.response = null;
            this.outputState = READY;
            this.expectationFailure = false;
        }

    }

}

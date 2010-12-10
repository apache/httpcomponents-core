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
import org.apache.http.HttpStatus;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.entity.ContentBufferEntity;
import org.apache.http.nio.entity.ContentOutputStream;
import org.apache.http.nio.params.NIOReactorPNames;
import org.apache.http.nio.protocol.ThrottlingHttpServiceHandler.ServerConnState;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.ContentInputBuffer;
import org.apache.http.nio.util.ContentOutputBuffer;
import org.apache.http.nio.util.DirectByteBufferAllocator;
import org.apache.http.nio.util.SharedInputBuffer;
import org.apache.http.nio.util.SharedOutputBuffer;
import org.apache.http.params.HttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.DefaultedHttpParams;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;

/**
 * Client protocol handler implementation that provide compatibility with
 * the blocking I/O by utilizing shared content buffers and a fairly small pool
 * of worker threads. The throttling protocol handler allocates input / output
 * buffers of a constant length upon initialization and controls the rate of
 * I/O events in order to ensure those content buffers do not ever get
 * overflown. This helps ensure nearly constant memory footprint for HTTP
 * connections and avoid the out of memory condition while streaming content
 * in and out. The {@link HttpRequestExecutionHandler#handleResponse(HttpResponse, HttpContext)}
 * method will fire immediately when a message is received. The protocol handler
 * delegate the task of processing requests and generating response content to
 * an {@link Executor}, which is expected to perform those tasks using
 * dedicated worker threads in order to avoid blocking the I/O thread.
 * <p/>
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
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#WAIT_FOR_CONTINUE}</li>
 *  <li>{@link org.apache.http.nio.params.NIOReactorPNames#CONTENT_BUFFER_SIZE}</li>
 * </ul>
 *
 * @since 4.0
 */
public class ThrottlingHttpClientHandler extends NHttpHandlerBase
                                         implements NHttpClientHandler {

    protected HttpRequestExecutionHandler execHandler;
    protected final Executor executor;

    public ThrottlingHttpClientHandler(
            final HttpProcessor httpProcessor,
            final HttpRequestExecutionHandler execHandler,
            final ConnectionReuseStrategy connStrategy,
            final ByteBufferAllocator allocator,
            final Executor executor,
            final HttpParams params) {
        super(httpProcessor, connStrategy, allocator, params);
        if (execHandler == null) {
            throw new IllegalArgumentException("HTTP request execution handler may not be null.");
        }
        if (executor == null) {
            throw new IllegalArgumentException("Executor may not be null");
        }
        this.execHandler = execHandler;
        this.executor = executor;
    }

    public ThrottlingHttpClientHandler(
            final HttpProcessor httpProcessor,
            final HttpRequestExecutionHandler execHandler,
            final ConnectionReuseStrategy connStrategy,
            final Executor executor,
            final HttpParams params) {
        this(httpProcessor, execHandler, connStrategy,
                new DirectByteBufferAllocator(), executor, params);
    }

    public void connected(final NHttpClientConnection conn, final Object attachment) {
        HttpContext context = conn.getContext();

        initialize(conn, attachment);

        int bufsize = this.params.getIntParameter(
                NIOReactorPNames.CONTENT_BUFFER_SIZE, 20480);
        ClientConnState connState = new ClientConnState(bufsize, conn, this.allocator);
        context.setAttribute(CONN_STATE, connState);

        if (this.eventListener != null) {
            this.eventListener.connectionOpen(conn);
        }

        requestReady(conn);
    }

    public void closed(final NHttpClientConnection conn) {
        HttpContext context = conn.getContext();
        ClientConnState connState = (ClientConnState) context.getAttribute(CONN_STATE);

        if (connState != null) {
            synchronized (connState) {
                connState.close();
                connState.notifyAll();
            }
        }
        this.execHandler.finalizeContext(context);

        if (this.eventListener != null) {
            this.eventListener.connectionClosed(conn);
        }
    }

    public void exception(final NHttpClientConnection conn, final HttpException ex) {
        closeConnection(conn, ex);
        if (this.eventListener != null) {
            this.eventListener.fatalProtocolException(ex, conn);
        }
    }

    public void exception(final NHttpClientConnection conn, final IOException ex) {
        shutdownConnection(conn, ex);
        if (this.eventListener != null) {
            this.eventListener.fatalIOException(ex, conn);
        }
    }


    public void requestReady(final NHttpClientConnection conn) {
        HttpContext context = conn.getContext();

        ClientConnState connState = (ClientConnState) context.getAttribute(CONN_STATE);

        try {

            synchronized (connState) {
                if (connState.getOutputState() != ClientConnState.READY) {
                    return;
                }

                HttpRequest request = this.execHandler.submitRequest(context);
                if (request == null) {
                    return;
                }

                request.setParams(
                        new DefaultedHttpParams(request.getParams(), this.params));

                context.setAttribute(ExecutionContext.HTTP_REQUEST, request);
                this.httpProcessor.process(request, context);
                connState.setRequest(request);
                conn.submitRequest(request);
                connState.setOutputState(ClientConnState.REQUEST_SENT);

                conn.requestInput();

                if (request instanceof HttpEntityEnclosingRequest) {
                    if (((HttpEntityEnclosingRequest) request).expectContinue()) {
                        int timeout = conn.getSocketTimeout();
                        connState.setTimeout(timeout);
                        timeout = this.params.getIntParameter(
                                CoreProtocolPNames.WAIT_FOR_CONTINUE, 3000);
                        conn.setSocketTimeout(timeout);
                        connState.setOutputState(ClientConnState.EXPECT_CONTINUE);
                    } else {
                        sendRequestBody(
                                (HttpEntityEnclosingRequest) request,
                                connState,
                                conn);
                    }
                }

                connState.notifyAll();
            }

        } catch (IOException ex) {
            shutdownConnection(conn, ex);
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex, conn);
            }
        } catch (HttpException ex) {
            closeConnection(conn, ex);
            if (this.eventListener != null) {
                this.eventListener.fatalProtocolException(ex, conn);
            }
        }
    }

    public void outputReady(final NHttpClientConnection conn, final ContentEncoder encoder) {
        HttpContext context = conn.getContext();

        ClientConnState connState = (ClientConnState) context.getAttribute(CONN_STATE);

        try {

            synchronized (connState) {
                if (connState.getOutputState() == ClientConnState.EXPECT_CONTINUE) {
                    conn.suspendOutput();
                    return;
                }
                ContentOutputBuffer buffer = connState.getOutbuffer();
                buffer.produceContent(encoder);
                if (encoder.isCompleted()) {
                    connState.setInputState(ClientConnState.REQUEST_BODY_DONE);
                } else {
                    connState.setInputState(ClientConnState.REQUEST_BODY_STREAM);
                }

                connState.notifyAll();
            }

        } catch (IOException ex) {
            shutdownConnection(conn, ex);
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex, conn);
            }
        }
    }

    public void responseReceived(final NHttpClientConnection conn) {
        HttpContext context = conn.getContext();
        ClientConnState connState = (ClientConnState) context.getAttribute(CONN_STATE);

        try {

            synchronized (connState) {
                HttpResponse response = conn.getHttpResponse();
                response.setParams(
                        new DefaultedHttpParams(response.getParams(), this.params));

                HttpRequest request = connState.getRequest();

                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode < HttpStatus.SC_OK) {
                    // 1xx intermediate response
                    if (statusCode == HttpStatus.SC_CONTINUE
                            && connState.getOutputState() == ClientConnState.EXPECT_CONTINUE) {
                        connState.setOutputState(ClientConnState.REQUEST_SENT);
                        continueRequest(conn, connState);
                    }
                    return;
                } else {
                    connState.setResponse(response);
                    connState.setInputState(ClientConnState.RESPONSE_RECEIVED);

                    if (connState.getOutputState() == ClientConnState.EXPECT_CONTINUE) {
                        int timeout = connState.getTimeout();
                        conn.setSocketTimeout(timeout);
                        conn.resetOutput();
                    }
                }

                if (!canResponseHaveBody(request, response)) {
                    conn.resetInput();
                    response.setEntity(null);
                    connState.setInputState(ClientConnState.RESPONSE_DONE);

                    if (!this.connStrategy.keepAlive(response, context)) {
                        conn.close();
                    }
                }

                if (response.getEntity() != null) {
                    response.setEntity(new ContentBufferEntity(
                            response.getEntity(),
                            connState.getInbuffer()));
                }

                context.setAttribute(ExecutionContext.HTTP_RESPONSE, response);

                this.httpProcessor.process(response, context);

                handleResponse(response, connState, conn);

                connState.notifyAll();
            }

        } catch (IOException ex) {
            shutdownConnection(conn, ex);
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex, conn);
            }
        } catch (HttpException ex) {
            closeConnection(conn, ex);
            if (this.eventListener != null) {
                this.eventListener.fatalProtocolException(ex, conn);
            }
        }
    }

    public void inputReady(final NHttpClientConnection conn, final ContentDecoder decoder) {
        HttpContext context = conn.getContext();

        ClientConnState connState = (ClientConnState) context.getAttribute(CONN_STATE);
        try {

            synchronized (connState) {
                HttpResponse response = connState.getResponse();
                ContentInputBuffer buffer = connState.getInbuffer();

                buffer.consumeContent(decoder);
                if (decoder.isCompleted()) {
                    connState.setInputState(ClientConnState.RESPONSE_BODY_DONE);

                    if (!this.connStrategy.keepAlive(response, context)) {
                        conn.close();
                    }
                } else {
                    connState.setInputState(ClientConnState.RESPONSE_BODY_STREAM);
                }

                connState.notifyAll();
            }

        } catch (IOException ex) {
            shutdownConnection(conn, ex);
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex, conn);
            }
        }
    }

    public void timeout(final NHttpClientConnection conn) {
        HttpContext context = conn.getContext();
        ClientConnState connState = (ClientConnState) context.getAttribute(CONN_STATE);

        try {

            synchronized (connState) {
                if (connState.getOutputState() == ClientConnState.EXPECT_CONTINUE) {
                    connState.setOutputState(ClientConnState.REQUEST_SENT);
                    continueRequest(conn, connState);

                    connState.notifyAll();
                    return;
                }
            }

        } catch (IOException ex) {
            shutdownConnection(conn, ex);
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex, conn);
            }
        }

        handleTimeout(conn);
    }

    private void initialize(
            final NHttpClientConnection conn,
            final Object attachment) {
        HttpContext context = conn.getContext();

        context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        this.execHandler.initalizeContext(context, attachment);
    }

    private void continueRequest(
            final NHttpClientConnection conn,
            final ClientConnState connState) throws IOException {

        HttpRequest request = connState.getRequest();

        int timeout = connState.getTimeout();
        conn.setSocketTimeout(timeout);

        sendRequestBody(
                (HttpEntityEnclosingRequest) request,
                connState,
                conn);
    }

    /**
     * @throws IOException - not thrown currently
     */
    private void sendRequestBody(
            final HttpEntityEnclosingRequest request,
            final ClientConnState connState,
            final NHttpClientConnection conn) throws IOException {
        HttpEntity entity = request.getEntity();
        if (entity != null) {

            this.executor.execute(new Runnable() {

                public void run() {
                    try {

                        // Block until previous request is fully processed and
                        // the worker thread no longer holds the shared buffer
                        synchronized (connState) {
                            try {
                                for (;;) {
                                    int currentState = connState.getOutputState();
                                    if (!connState.isWorkerRunning()) {
                                        break;
                                    }
                                    if (currentState == ServerConnState.SHUTDOWN) {
                                        return;
                                    }
                                    connState.wait();
                                }
                            } catch (InterruptedException ex) {
                                connState.shutdown();
                                return;
                            }
                            connState.setWorkerRunning(true);
                        }

                        HttpEntity entity = request.getEntity();
                        OutputStream outstream = new ContentOutputStream(
                                connState.getOutbuffer());
                        entity.writeTo(outstream);
                        outstream.flush();
                        outstream.close();

                        synchronized (connState) {
                            connState.setWorkerRunning(false);
                            connState.notifyAll();
                        }

                    } catch (IOException ex) {
                        shutdownConnection(conn, ex);
                        if (eventListener != null) {
                            eventListener.fatalIOException(ex, conn);
                        }
                    }
                }

            });
        }
    }

    private void handleResponse(
            final HttpResponse response,
            final ClientConnState connState,
            final NHttpClientConnection conn) {

        final HttpContext context = conn.getContext();

        this.executor.execute(new Runnable() {

            public void run() {
                try {

                    // Block until previous request is fully processed and
                    // the worker thread no longer holds the shared buffer
                    synchronized (connState) {
                        try {
                            for (;;) {
                                int currentState = connState.getOutputState();
                                if (!connState.isWorkerRunning()) {
                                    break;
                                }
                                if (currentState == ServerConnState.SHUTDOWN) {
                                    return;
                                }
                                connState.wait();
                            }
                        } catch (InterruptedException ex) {
                            connState.shutdown();
                            return;
                        }
                        connState.setWorkerRunning(true);
                    }

                    execHandler.handleResponse(response, context);

                    synchronized (connState) {

                        try {
                            for (;;) {
                                int currentState = connState.getInputState();
                                if (currentState == ClientConnState.RESPONSE_DONE) {
                                    break;
                                }
                                if (currentState == ServerConnState.SHUTDOWN) {
                                    return;
                                }
                                connState.wait();
                            }
                        } catch (InterruptedException ex) {
                            connState.shutdown();
                        }

                        connState.resetInput();
                        connState.resetOutput();
                        if (conn.isOpen()) {
                            conn.requestOutput();
                        }
                        connState.setWorkerRunning(false);
                        connState.notifyAll();
                    }

                } catch (IOException ex) {
                    shutdownConnection(conn, ex);
                    if (eventListener != null) {
                        eventListener.fatalIOException(ex, conn);
                    }
                }
            }

        });

    }

    static class ClientConnState {

        public static final int SHUTDOWN                   = -1;
        public static final int READY                      = 0;
        public static final int REQUEST_SENT               = 1;
        public static final int EXPECT_CONTINUE            = 2;
        public static final int REQUEST_BODY_STREAM        = 4;
        public static final int REQUEST_BODY_DONE          = 8;
        public static final int RESPONSE_RECEIVED          = 16;
        public static final int RESPONSE_BODY_STREAM       = 32;
        public static final int RESPONSE_BODY_DONE         = 64;
        public static final int RESPONSE_DONE              = 64;

        private final SharedInputBuffer inbuffer;
        private final SharedOutputBuffer outbuffer;

        private volatile int inputState;
        private volatile int outputState;

        private volatile HttpRequest request;
        private volatile HttpResponse response;

        private volatile int timeout;

        private volatile boolean workerRunning;

        public ClientConnState(
                int bufsize,
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

        public void setInputState(int inputState) {
            this.inputState = inputState;
        }

        public int getOutputState() {
            return this.outputState;
        }

        public void setOutputState(int outputState) {
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

        public int getTimeout() {
            return this.timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        public boolean isWorkerRunning() {
            return this.workerRunning;
        }

        public void setWorkerRunning(boolean b) {
            this.workerRunning = b;
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
        }

    }

}

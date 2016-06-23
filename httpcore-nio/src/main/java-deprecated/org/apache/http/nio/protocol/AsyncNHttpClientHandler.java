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

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.annotation.Contract;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.entity.ConsumingNHttpEntity;
import org.apache.http.nio.entity.NHttpEntityWrapper;
import org.apache.http.nio.entity.ProducingNHttpEntity;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.DefaultedHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.util.Args;

/**
 * Fully asynchronous HTTP client side protocol handler that implements the
 * essential requirements of the HTTP protocol for the server side message
 * processing as described by RFC 2616. It is capable of executing HTTP requests
 * with nearly constant memory footprint. Only HTTP message heads are stored in
 * memory, while content of message bodies is streamed directly from the entity
 * to the underlying channel (and vice versa) using {@link ConsumingNHttpEntity}
 * and {@link ProducingNHttpEntity} interfaces.
 *
 * When using this implementation, it is important to ensure that entities
 * supplied for writing implement {@link ProducingNHttpEntity}. Doing so will allow
 * the entity to be written out asynchronously. If entities supplied for writing
 * do not implement the {@link ProducingNHttpEntity} interface, a delegate is
 * added that buffers the entire contents in memory. Additionally, the
 * buffering might take place in the I/O dispatch thread, which could cause I/O
 * to block temporarily. For best results, one must ensure that all entities
 * set on {@link HttpRequest}s from {@link NHttpRequestExecutionHandler}
 * implement {@link ProducingNHttpEntity}.
 *
 * If incoming responses enclose a content entity,
 * {@link NHttpRequestExecutionHandler} are expected to return a
 * {@link ConsumingNHttpEntity} for reading the content. After the entity is
 * finished reading the data,
 * {@link NHttpRequestExecutionHandler#handleResponse(HttpResponse, HttpContext)}
 * method is called to process the response.
 * <p>
 * The following parameters can be used to customize the behavior of this
 * class:
 * <ul>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#WAIT_FOR_CONTINUE}</li>
 * </ul>
 *
 * @since 4.0
 *
 * @deprecated (4.2) use {@link HttpAsyncRequestExecutor} and {@link HttpAsyncRequester}
 */
@Deprecated
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class AsyncNHttpClientHandler extends NHttpHandlerBase
                                     implements NHttpClientHandler {

    protected NHttpRequestExecutionHandler execHandler;

    public AsyncNHttpClientHandler(
            final HttpProcessor httpProcessor,
            final NHttpRequestExecutionHandler execHandler,
            final ConnectionReuseStrategy connStrategy,
            final ByteBufferAllocator allocator,
            final HttpParams params) {
        super(httpProcessor, connStrategy, allocator, params);
        this.execHandler = Args.notNull(execHandler, "HTTP request execution handler");
    }

    public AsyncNHttpClientHandler(
            final HttpProcessor httpProcessor,
            final NHttpRequestExecutionHandler execHandler,
            final ConnectionReuseStrategy connStrategy,
            final HttpParams params) {
        this(httpProcessor, execHandler, connStrategy, HeapByteBufferAllocator.INSTANCE, params);
    }

    @Override
    public void connected(final NHttpClientConnection conn, final Object attachment) {
        final HttpContext context = conn.getContext();

        initialize(conn, attachment);

        final ClientConnState connState = new ClientConnState();
        context.setAttribute(CONN_STATE, connState);

        if (this.eventListener != null) {
            this.eventListener.connectionOpen(conn);
        }

        requestReady(conn);
    }

    @Override
    public void closed(final NHttpClientConnection conn) {
        final HttpContext context = conn.getContext();

        final ClientConnState connState = (ClientConnState) context.getAttribute(CONN_STATE);
        try {
            connState.reset();
        } catch (final IOException ex) {
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex, conn);
            }
        }

        this.execHandler.finalizeContext(context);

        if (this.eventListener != null) {
            this.eventListener.connectionClosed(conn);
        }
    }

    @Override
    public void exception(final NHttpClientConnection conn, final HttpException ex) {
        closeConnection(conn, ex);
        if (this.eventListener != null) {
            this.eventListener.fatalProtocolException(ex, conn);
        }
    }

    @Override
    public void exception(final NHttpClientConnection conn, final IOException ex) {
        shutdownConnection(conn, ex);
        if (this.eventListener != null) {
            this.eventListener.fatalIOException(ex, conn);
        }
    }

    @Override
    public void requestReady(final NHttpClientConnection conn) {
        final HttpContext context = conn.getContext();

        final ClientConnState connState = (ClientConnState) context.getAttribute(CONN_STATE);
        if (connState.getOutputState() != ClientConnState.READY) {
            return;
        }

        try {

            final HttpRequest request = this.execHandler.submitRequest(context);
            if (request == null) {
                return;
            }

            request.setParams(
                    new DefaultedHttpParams(request.getParams(), this.params));

            context.setAttribute(ExecutionContext.HTTP_REQUEST, request);
            this.httpProcessor.process(request, context);

            HttpEntityEnclosingRequest entityReq = null;
            HttpEntity entity = null;

            if (request instanceof HttpEntityEnclosingRequest) {
                entityReq = (HttpEntityEnclosingRequest) request;
                entity = entityReq.getEntity();
            }

            if (entity instanceof ProducingNHttpEntity) {
                connState.setProducingEntity((ProducingNHttpEntity) entity);
            } else if (entity != null) {
                connState.setProducingEntity(new NHttpEntityWrapper(entity));
            }

            connState.setRequest(request);
            conn.submitRequest(request);
            connState.setOutputState(ClientConnState.REQUEST_SENT);

            if (entityReq != null && entityReq.expectContinue()) {
                int timeout = conn.getSocketTimeout();
                connState.setTimeout(timeout);
                timeout = this.params.getIntParameter(
                        CoreProtocolPNames.WAIT_FOR_CONTINUE, 3000);
                conn.setSocketTimeout(timeout);
                connState.setOutputState(ClientConnState.EXPECT_CONTINUE);
            } else if (connState.getProducingEntity() != null) {
                connState.setOutputState(ClientConnState.REQUEST_BODY_STREAM);
            }

        } catch (final IOException ex) {
            shutdownConnection(conn, ex);
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex, conn);
            }
        } catch (final HttpException ex) {
            closeConnection(conn, ex);
            if (this.eventListener != null) {
                this.eventListener.fatalProtocolException(ex, conn);
            }
        }
    }

    @Override
    public void inputReady(final NHttpClientConnection conn, final ContentDecoder decoder) {
        final HttpContext context = conn.getContext();

        final ClientConnState connState = (ClientConnState) context.getAttribute(CONN_STATE);

        final ConsumingNHttpEntity consumingEntity = connState.getConsumingEntity();

        try {
            consumingEntity.consumeContent(decoder, conn);
            if (decoder.isCompleted()) {
                processResponse(conn, connState);
            }

        } catch (final IOException ex) {
            shutdownConnection(conn, ex);
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex, conn);
            }
        } catch (final HttpException ex) {
            closeConnection(conn, ex);
            if (this.eventListener != null) {
                this.eventListener.fatalProtocolException(ex, conn);
            }
        }
    }

    @Override
    public void outputReady(final NHttpClientConnection conn, final ContentEncoder encoder) {
        final HttpContext context = conn.getContext();
        final ClientConnState connState = (ClientConnState) context.getAttribute(CONN_STATE);

        try {
            if (connState.getOutputState() == ClientConnState.EXPECT_CONTINUE) {
                conn.suspendOutput();
                return;
            }

            final ProducingNHttpEntity entity = connState.getProducingEntity();

            entity.produceContent(encoder, conn);
            if (encoder.isCompleted()) {
                connState.setOutputState(ClientConnState.REQUEST_BODY_DONE);
            }
        } catch (final IOException ex) {
            shutdownConnection(conn, ex);
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex, conn);
            }
        }
    }

    @Override
    public void responseReceived(final NHttpClientConnection conn) {
        final HttpContext context = conn.getContext();
        final ClientConnState connState = (ClientConnState) context.getAttribute(CONN_STATE);

        final HttpResponse response = conn.getHttpResponse();
        response.setParams(
                new DefaultedHttpParams(response.getParams(), this.params));

        final HttpRequest request = connState.getRequest();
        try {

            final int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode < HttpStatus.SC_OK) {
                // 1xx intermediate response
                if (statusCode == HttpStatus.SC_CONTINUE
                        && connState.getOutputState() == ClientConnState.EXPECT_CONTINUE) {
                    continueRequest(conn, connState);
                }
                return;
            } else {
                connState.setResponse(response);
                if (connState.getOutputState() == ClientConnState.EXPECT_CONTINUE) {
                    cancelRequest(conn, connState);
                } else if (connState.getOutputState() == ClientConnState.REQUEST_BODY_STREAM) {
                    // Early response
                    cancelRequest(conn, connState);
                    connState.invalidate();
                    conn.suspendOutput();
                }
            }

            context.setAttribute(ExecutionContext.HTTP_RESPONSE, response);

            if (!canResponseHaveBody(request, response)) {
                conn.resetInput();
                response.setEntity(null);
                this.httpProcessor.process(response, context);
                processResponse(conn, connState);
            } else {
                final HttpEntity entity = response.getEntity();
                if (entity != null) {
                    ConsumingNHttpEntity consumingEntity = this.execHandler.responseEntity(
                            response, context);
                    if (consumingEntity == null) {
                        consumingEntity = new NullNHttpEntity(entity);
                    }
                    response.setEntity(consumingEntity);
                    connState.setConsumingEntity(consumingEntity);
                    this.httpProcessor.process(response, context);
                }
            }


        } catch (final IOException ex) {
            shutdownConnection(conn, ex);
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex, conn);
            }
        } catch (final HttpException ex) {
            closeConnection(conn, ex);
            if (this.eventListener != null) {
                this.eventListener.fatalProtocolException(ex, conn);
            }
        }
    }

    @Override
    public void timeout(final NHttpClientConnection conn) {
        final HttpContext context = conn.getContext();
        final ClientConnState connState = (ClientConnState) context.getAttribute(CONN_STATE);

        try {

            if (connState.getOutputState() == ClientConnState.EXPECT_CONTINUE) {
                continueRequest(conn, connState);
                return;
            }

        } catch (final IOException ex) {
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
        final HttpContext context = conn.getContext();

        context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        this.execHandler.initalizeContext(context, attachment);
    }

    /**
     * @throws IOException - not thrown currently
     */
    private void continueRequest(
            final NHttpClientConnection conn,
            final ClientConnState connState) throws IOException {

        final int timeout = connState.getTimeout();
        conn.setSocketTimeout(timeout);

        conn.requestOutput();
        connState.setOutputState(ClientConnState.REQUEST_BODY_STREAM);
    }

    private void cancelRequest(
            final NHttpClientConnection conn,
            final ClientConnState connState) throws IOException {

        final int timeout = connState.getTimeout();
        conn.setSocketTimeout(timeout);

        conn.resetOutput();
        connState.resetOutput();
    }

    /**
     * @throws HttpException - not thrown currently
     */
    private void processResponse(
            final NHttpClientConnection conn,
            final ClientConnState connState) throws IOException, HttpException {

        if (!connState.isValid()) {
            conn.close();
        }

        final HttpContext context = conn.getContext();
        final HttpResponse response = connState.getResponse();
        this.execHandler.handleResponse(response, context);
        if (!this.connStrategy.keepAlive(response, context)) {
            conn.close();
        }

        if (conn.isOpen()) {
            // Ready for another request
            connState.resetInput();
            connState.resetOutput();
            conn.requestOutput();
        }
    }

    protected static class ClientConnState {

        public static final int READY                      = 0;
        public static final int REQUEST_SENT               = 1;
        public static final int EXPECT_CONTINUE            = 2;
        public static final int REQUEST_BODY_STREAM        = 4;
        public static final int REQUEST_BODY_DONE          = 8;
        public static final int RESPONSE_RECEIVED          = 16;
        public static final int RESPONSE_BODY_STREAM       = 32;
        public static final int RESPONSE_BODY_DONE         = 64;

        private int outputState;

        private HttpRequest request;
        private HttpResponse response;
        private ConsumingNHttpEntity consumingEntity;
        private ProducingNHttpEntity producingEntity;
        private boolean valid;
        private int timeout;

        public ClientConnState() {
            super();
            this.valid = true;
        }

        public void setConsumingEntity(final ConsumingNHttpEntity consumingEntity) {
            this.consumingEntity = consumingEntity;
        }

        public void setProducingEntity(final ProducingNHttpEntity producingEntity) {
            this.producingEntity = producingEntity;
        }

        public ProducingNHttpEntity getProducingEntity() {
            return producingEntity;
        }

        public ConsumingNHttpEntity getConsumingEntity() {
            return consumingEntity;
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

        public int getTimeout() {
            return this.timeout;
        }

        public void setTimeout(final int timeout) {
            this.timeout = timeout;
        }

        public void resetInput() throws IOException {
            this.response = null;
            if (this.consumingEntity != null) {
                this.consumingEntity.finish();
                this.consumingEntity = null;
            }
        }

        public void resetOutput() throws IOException {
            this.request = null;
            if (this.producingEntity != null) {
                this.producingEntity.finish();
                this.producingEntity = null;
            }
            this.outputState = READY;
        }

        public void reset() throws IOException {
            resetInput();
            resetOutput();
        }

        public boolean isValid() {
            return this.valid;
        }

        public void invalidate() {
            this.valid = false;
        }

    }

}

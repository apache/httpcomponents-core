/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.entity.ConsumingNHttpEntity;
import org.apache.http.nio.entity.ConsumingNHttpEntityTemplate;
import org.apache.http.nio.entity.ProducingNHttpEntity;
import org.apache.http.nio.entity.SkipContentListener;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.DefaultedHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;

/**
 * HTTP client handler implementation that asynchronously reads & writes out the
 * content of messages.
 *
 * @see ConsumingNHttpEntity
 * @see ProducingNHttpEntity
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * @author <a href="mailto:sberlin at gmail.com">Sam Berlin</a>
 *
 */
public class AsyncNHttpClientHandler extends AbstractNHttpClientHandler {

    protected NHttpRequestExecutionHandler execHandler;

    public AsyncNHttpClientHandler(
            final HttpProcessor httpProcessor,
            final NHttpRequestExecutionHandler execHandler,
            final ConnectionReuseStrategy connStrategy,
            final ByteBufferAllocator allocator,
            final HttpParams params) {
        super(httpProcessor, connStrategy, allocator, params);
        if (execHandler == null) {
            throw new IllegalArgumentException("HTTP request execution handler may not be null.");
        }
        this.execHandler = execHandler;
    }

    public AsyncNHttpClientHandler(
            final HttpProcessor httpProcessor,
            final NHttpRequestExecutionHandler execHandler,
            final ConnectionReuseStrategy connStrategy,
            final HttpParams params) {
        this(httpProcessor, execHandler, connStrategy,
                new HeapByteBufferAllocator(), params);
    }

    public void connected(final NHttpClientConnection conn, final Object attachment) {
        HttpContext context = conn.getContext();

        initialize(conn, attachment);

        ClientConnState connState = new ClientConnState();
        context.setAttribute(CONN_STATE, connState);

        if (this.eventListener != null) {
            this.eventListener.connectionOpen(conn);
        }

        requestReady(conn);
    }

    @Override
    public void closed(final NHttpClientConnection conn) {
        HttpContext context = conn.getContext();

        ClientConnState connState = (ClientConnState) context.getAttribute(CONN_STATE);
        connState.reset();

        this.execHandler.finalizeContext(context);

        if (this.eventListener != null) {
            this.eventListener.connectionClosed(conn);
        }
    }

    public void requestReady(final NHttpClientConnection conn) {
        HttpContext context = conn.getContext();

        ClientConnState connState = (ClientConnState) context.getAttribute(CONN_STATE);
        if (connState.getOutputState() != ClientConnState.READY) {
            return;
        }

        try {

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

            if (request instanceof HttpEntityEnclosingRequest) {
                HttpEntityEnclosingRequest entityReq = (HttpEntityEnclosingRequest)request;
                ProducingNHttpEntity entity = (ProducingNHttpEntity)entityReq.getEntity();
                connState.setProducingEntity(entity);

                if (entityReq.expectContinue()) {
                    int timeout = conn.getSocketTimeout();
                    connState.setTimeout(timeout);
                    timeout = this.params.getIntParameter(
                            CoreProtocolPNames.WAIT_FOR_CONTINUE, 3000);
                    conn.setSocketTimeout(timeout);
                    connState.setOutputState(ClientConnState.EXPECT_CONTINUE);
                }
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

        ConsumingNHttpEntity consumingEntity = connState.getConsumingEntity();

        try {
            consumingEntity.consumeContent(decoder, conn);
            if (decoder.isCompleted()) {
                processResponse(conn, connState);
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
            if (connState.getOutputState() == ClientConnState.EXPECT_CONTINUE) {
                conn.suspendOutput();
                return;
            }

            ProducingNHttpEntity entity = connState.getProducingEntity();

            entity.produceContent(encoder, conn);
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

        HttpResponse response = conn.getHttpResponse();
        response.setParams(
                new DefaultedHttpParams(response.getParams(), this.params));

        HttpRequest request = connState.getRequest();
        try {

            int statusCode = response.getStatusLine().getStatusCode();
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
                }
            }
            if (!canResponseHaveBody(request, response)) {
                conn.resetInput();
                response.setEntity(null);
                processResponse(conn, connState);
            } else {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    ConsumingNHttpEntity consumingEntity = this.execHandler.responseEntity(
                            response, context);
                    if (consumingEntity == null) {
                        consumingEntity = new ConsumingNHttpEntityTemplate(
                                entity, new SkipContentListener(this.allocator));
                    }
                    response.setEntity(consumingEntity);
                    connState.setConsumingEntity(consumingEntity);
                }
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

    public void timeout(final NHttpClientConnection conn) {
        HttpContext context = conn.getContext();
        ClientConnState connState = (ClientConnState) context.getAttribute(CONN_STATE);

        try {

            if (connState.getOutputState() == ClientConnState.EXPECT_CONTINUE) {
                continueRequest(conn, connState);
                return;
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

        int timeout = connState.getTimeout();
        conn.setSocketTimeout(timeout);

        conn.requestOutput();
        connState.setOutputState(ClientConnState.REQUEST_SENT);
    }

    private void cancelRequest(
            final NHttpClientConnection conn,
            final ClientConnState connState) throws IOException {

        int timeout = connState.getTimeout();
        conn.setSocketTimeout(timeout);

        conn.resetOutput();
        connState.resetOutput();
    }

    private void processResponse(
            final NHttpClientConnection conn,
            final ClientConnState connState) throws IOException, HttpException {

        HttpContext context = conn.getContext();
        HttpResponse response = connState.getResponse();

        context.setAttribute(ExecutionContext.HTTP_RESPONSE, response);

        this.httpProcessor.process(response, context);

        this.execHandler.handleResponse(response, context);

        if (!this.connStrategy.keepAlive(response, context)) {
            conn.close();
        } else {
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

        private int timeout;

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

        public void resetInput() {
            this.response = null;
            if (this.consumingEntity != null) {
                this.consumingEntity.finish();
                this.consumingEntity = null;
            }
        }

        public void resetOutput() {
            this.request = null;
            if (this.producingEntity != null) {
                this.producingEntity.finish();
                this.producingEntity = null;
            }
            this.outputState = READY;
        }

        public void reset() {
            resetInput();
            resetOutput();
        }
    }

}

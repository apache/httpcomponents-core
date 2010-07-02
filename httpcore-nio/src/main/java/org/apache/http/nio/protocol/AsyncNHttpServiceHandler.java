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
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.ProtocolException;
import org.apache.http.ProtocolVersion;
import org.apache.http.UnsupportedHttpVersionException;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.NHttpServiceHandler;
import org.apache.http.nio.entity.ConsumingNHttpEntity;
import org.apache.http.nio.entity.ConsumingNHttpEntityTemplate;
import org.apache.http.nio.entity.NByteArrayEntity;
import org.apache.http.nio.entity.NHttpEntityWrapper;
import org.apache.http.nio.entity.ProducingNHttpEntity;
import org.apache.http.nio.entity.SkipContentListener;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.params.DefaultedHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExpectationVerifier;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.util.EncodingUtils;

/**
 * Fully asynchronous HTTP server side protocol handler implementation that
 * implements the essential requirements of the HTTP protocol for the server
 * side message processing as described by RFC 2616. It is capable of processing
 * HTTP requests with nearly constant memory footprint. Only HTTP message heads
 * are stored in memory, while content of message bodies is streamed directly
 * from the entity to the underlying channel (and vice versa)
 * {@link ConsumingNHttpEntity} and {@link ProducingNHttpEntity} interfaces.
 * <p/>
 * When using this class, it is important to ensure that entities supplied for
 * writing implement {@link ProducingNHttpEntity}. Doing so will allow the
 * entity to be written out asynchronously. If entities supplied for writing do
 * not implement {@link ProducingNHttpEntity}, a delegate is added that buffers
 * the entire contents in memory. Additionally, the buffering might take place
 * in the I/O thread, which could cause I/O to block temporarily. For best
 * results, ensure that all entities set on {@link HttpResponse}s from
 * {@link NHttpRequestHandler}s implement {@link ProducingNHttpEntity}.
 * <p/>
 * If incoming requests enclose a content entity, {@link NHttpRequestHandler}s
 * are expected to return a {@link ConsumingNHttpEntity} for reading the
 * content. After the entity is finished reading the data,
 * {@link NHttpRequestHandler#handle(HttpRequest, HttpResponse, NHttpResponseTrigger, HttpContext)}
 * is called to generate a response.
 * <p/>
 * Individual {@link NHttpRequestHandler}s do not have to submit a response
 * immediately. They can defer transmission of the HTTP response back to the
 * client without blocking the I/O thread and to delegate the processing the
 * HTTP request to a worker thread. The worker thread in its turn can use an
 * instance of {@link NHttpResponseTrigger} passed as a parameter to submit
 * a response as at a later point of time once the response becomes available.
 *
 * @see ConsumingNHttpEntity
 * @see ProducingNHttpEntity
 *
 * @since 4.0
 */
public class AsyncNHttpServiceHandler extends NHttpHandlerBase
                                      implements NHttpServiceHandler {

    protected final HttpResponseFactory responseFactory;

    protected NHttpRequestHandlerResolver handlerResolver;
    protected HttpExpectationVerifier expectationVerifier;

    public AsyncNHttpServiceHandler(
            final HttpProcessor httpProcessor,
            final HttpResponseFactory responseFactory,
            final ConnectionReuseStrategy connStrategy,
            final ByteBufferAllocator allocator,
            final HttpParams params) {
        super(httpProcessor, connStrategy, allocator, params);
        if (responseFactory == null) {
            throw new IllegalArgumentException("Response factory may not be null");
        }
        this.responseFactory = responseFactory;
    }

    public AsyncNHttpServiceHandler(
            final HttpProcessor httpProcessor,
            final HttpResponseFactory responseFactory,
            final ConnectionReuseStrategy connStrategy,
            final HttpParams params) {
        this(httpProcessor, responseFactory, connStrategy,
                new HeapByteBufferAllocator(), params);
    }

    public void setExpectationVerifier(final HttpExpectationVerifier expectationVerifier) {
        this.expectationVerifier = expectationVerifier;
    }

    public void setHandlerResolver(final NHttpRequestHandlerResolver handlerResolver) {
        this.handlerResolver = handlerResolver;
    }

    public void connected(final NHttpServerConnection conn) {
        HttpContext context = conn.getContext();

        ServerConnState connState = new ServerConnState();
        context.setAttribute(CONN_STATE, connState);
        context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);

        if (this.eventListener != null) {
            this.eventListener.connectionOpen(conn);
        }
    }

    public void requestReceived(final NHttpServerConnection conn) {
        HttpContext context = conn.getContext();

        ServerConnState connState = (ServerConnState) context.getAttribute(CONN_STATE);

        HttpRequest request = conn.getHttpRequest();
        request.setParams(new DefaultedHttpParams(request.getParams(), this.params));

        connState.setRequest(request);

        NHttpRequestHandler requestHandler = getRequestHandler(request);
        connState.setRequestHandler(requestHandler);

        ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
        if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
            // Downgrade protocol version if greater than HTTP/1.1
            ver = HttpVersion.HTTP_1_1;
        }

        HttpResponse response;

        try {

            if (request instanceof HttpEntityEnclosingRequest) {
                HttpEntityEnclosingRequest entityRequest = (HttpEntityEnclosingRequest) request;
                if (entityRequest.expectContinue()) {
                    response = this.responseFactory.newHttpResponse(
                            ver, HttpStatus.SC_CONTINUE, context);
                    response.setParams(
                            new DefaultedHttpParams(response.getParams(), this.params));

                    if (this.expectationVerifier != null) {
                        try {
                            this.expectationVerifier.verify(request, response, context);
                        } catch (HttpException ex) {
                            response = this.responseFactory.newHttpResponse(
                                    HttpVersion.HTTP_1_0,
                                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                                    context);
                            response.setParams(
                                    new DefaultedHttpParams(response.getParams(), this.params));
                            handleException(ex, response);
                        }
                    }

                    if (response.getStatusLine().getStatusCode() < 200) {
                        // Send 1xx response indicating the server expections
                        // have been met
                        conn.submitResponse(response);
                    } else {
                        conn.resetInput();
                        sendResponse(conn, request, response);
                    }
                }
                // Request content is expected.
                ConsumingNHttpEntity consumingEntity = null;

                // Lookup request handler for this request
                if (requestHandler != null) {
                    consumingEntity = requestHandler.entityRequest(entityRequest, context);
                }
                if (consumingEntity == null) {
                    consumingEntity = new ConsumingNHttpEntityTemplate(
                            entityRequest.getEntity(),
                            new SkipContentListener(this.allocator));
                }
                entityRequest.setEntity(consumingEntity);
                connState.setConsumingEntity(consumingEntity);

            } else {
                // No request content is expected.
                // Process request right away
                conn.suspendInput();
                processRequest(conn, request);
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

    public void closed(final NHttpServerConnection conn) {
        HttpContext context = conn.getContext();

        ServerConnState connState = (ServerConnState) context.getAttribute(CONN_STATE);
        try {
            connState.reset();
        } catch (IOException ex) {
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex, conn);
            }
        }
        if (this.eventListener != null) {
            this.eventListener.connectionClosed(conn);
        }
    }

    public void exception(final NHttpServerConnection conn, final HttpException httpex) {
        if (conn.isResponseSubmitted()) {
            // There is not much that we can do if a response head
            // has already been submitted
            closeConnection(conn, httpex);
            if (eventListener != null) {
                eventListener.fatalProtocolException(httpex, conn);
            }
            return;
        }

        HttpContext context = conn.getContext();
        try {
            HttpResponse response = this.responseFactory.newHttpResponse(
                    HttpVersion.HTTP_1_0, HttpStatus.SC_INTERNAL_SERVER_ERROR, context);
            response.setParams(
                    new DefaultedHttpParams(response.getParams(), this.params));
            handleException(httpex, response);
            response.setEntity(null);
            sendResponse(conn, null, response);

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

    public void exception(final NHttpServerConnection conn, final IOException ex) {
        shutdownConnection(conn, ex);

        if (this.eventListener != null) {
            this.eventListener.fatalIOException(ex, conn);
        }
    }

    public void timeout(final NHttpServerConnection conn) {
        handleTimeout(conn);
    }

    public void inputReady(final NHttpServerConnection conn, final ContentDecoder decoder) {
        HttpContext context = conn.getContext();
        ServerConnState connState = (ServerConnState) context.getAttribute(CONN_STATE);

        HttpRequest request = connState.getRequest();
        ConsumingNHttpEntity consumingEntity = connState.getConsumingEntity();

        try {

            consumingEntity.consumeContent(decoder, conn);
            if (decoder.isCompleted()) {
                conn.suspendInput();
                processRequest(conn, request);
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

    public void responseReady(final NHttpServerConnection conn) {
        HttpContext context = conn.getContext();
        ServerConnState connState = (ServerConnState) context.getAttribute(CONN_STATE);

        if (connState.isHandled()) {
            return;
        }

        HttpRequest request = connState.getRequest();

        try {

            IOException ioex = connState.getIOException();
            if (ioex != null) {
                throw ioex;
            }

            HttpException httpex = connState.getHttpException();
            if (httpex != null) {
                HttpResponse response = this.responseFactory.newHttpResponse(HttpVersion.HTTP_1_0,
                        HttpStatus.SC_INTERNAL_SERVER_ERROR, context);
                response.setParams(
                        new DefaultedHttpParams(response.getParams(), this.params));
                handleException(httpex, response);
                connState.setResponse(response);
            }

            HttpResponse response = connState.getResponse();
            if (response != null) {
                connState.setHandled(true);
                sendResponse(conn, request, response);
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

    public void outputReady(final NHttpServerConnection conn, final ContentEncoder encoder) {
        HttpContext context = conn.getContext();
        ServerConnState connState = (ServerConnState) context.getAttribute(CONN_STATE);

        HttpResponse response = conn.getHttpResponse();

        try {
            ProducingNHttpEntity entity = connState.getProducingEntity();
            entity.produceContent(encoder, conn);

            if (encoder.isCompleted()) {
                connState.finishOutput();
                if (!this.connStrategy.keepAlive(response, context)) {
                    conn.close();
                } else {
                    // Ready to process new request
                    connState.reset();
                    conn.requestInput();
                }
                responseComplete(response, context);
            }

        } catch (IOException ex) {
            shutdownConnection(conn, ex);
            if (this.eventListener != null) {
                this.eventListener.fatalIOException(ex, conn);
            }
        }
    }

    private void handleException(final HttpException ex, final HttpResponse response) {
        int code = HttpStatus.SC_INTERNAL_SERVER_ERROR;
        if (ex instanceof MethodNotSupportedException) {
            code = HttpStatus.SC_NOT_IMPLEMENTED;
        } else if (ex instanceof UnsupportedHttpVersionException) {
            code = HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED;
        } else if (ex instanceof ProtocolException) {
            code = HttpStatus.SC_BAD_REQUEST;
        }
        response.setStatusCode(code);

        byte[] msg = EncodingUtils.getAsciiBytes(ex.getMessage());
        NByteArrayEntity entity = new NByteArrayEntity(msg);
        entity.setContentType("text/plain; charset=US-ASCII");
        response.setEntity(entity);
    }

    /**
     * @throws HttpException - not thrown currently
     */
    private void processRequest(
            final NHttpServerConnection conn,
            final HttpRequest request) throws IOException, HttpException {

        HttpContext context = conn.getContext();
        ServerConnState connState = (ServerConnState) context.getAttribute(CONN_STATE);

        ProtocolVersion ver = request.getRequestLine().getProtocolVersion();

        if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
            // Downgrade protocol version if greater than HTTP/1.1
            ver = HttpVersion.HTTP_1_1;
        }

        NHttpResponseTrigger trigger = new ResponseTriggerImpl(connState, conn);
        try {
            this.httpProcessor.process(request, context);

            NHttpRequestHandler handler = connState.getRequestHandler();
            if (handler != null) {
                HttpResponse response = this.responseFactory.newHttpResponse(
                        ver, HttpStatus.SC_OK, context);
                response.setParams(
                        new DefaultedHttpParams(response.getParams(), this.params));

                handler.handle(
                        request,
                        response,
                        trigger,
                        context);
            } else {
                HttpResponse response = this.responseFactory.newHttpResponse(ver,
                        HttpStatus.SC_NOT_IMPLEMENTED, context);
                response.setParams(
                        new DefaultedHttpParams(response.getParams(), this.params));
                trigger.submitResponse(response);
            }

        } catch (HttpException ex) {
            trigger.handleException(ex);
        }
    }

    private void sendResponse(
            final NHttpServerConnection conn,
            final HttpRequest request,
            final HttpResponse response) throws IOException, HttpException {
        HttpContext context = conn.getContext();
        ServerConnState connState = (ServerConnState) context.getAttribute(CONN_STATE);

        // Now that a response is ready, we can cleanup the listener for the request.
        connState.finishInput();

        // Some processers need the request that generated this response.
        context.setAttribute(ExecutionContext.HTTP_REQUEST, request);
        this.httpProcessor.process(response, context);
        context.setAttribute(ExecutionContext.HTTP_REQUEST, null);

        if (response.getEntity() != null && !canResponseHaveBody(request, response)) {
            response.setEntity(null);
        }

        HttpEntity entity = response.getEntity();
        if (entity != null) {
            if (entity instanceof ProducingNHttpEntity) {
                connState.setProducingEntity((ProducingNHttpEntity) entity);
            } else {
                connState.setProducingEntity(new NHttpEntityWrapper(entity));
            }
        }

        conn.submitResponse(response);

        if (entity == null) {
            if (!this.connStrategy.keepAlive(response, context)) {
                conn.close();
            } else {
                // Ready to process new request
                connState.reset();
                conn.requestInput();
            }
            responseComplete(response, context);
        }
    }

    /**
     * Signals that this response has been fully sent. This will be called after
     * submitting the response to a connection, if there is no entity in the
     * response. If there is an entity, it will be called after the entity has
     * completed.
     */
    protected void responseComplete(HttpResponse response, HttpContext context) {
    }

    private NHttpRequestHandler getRequestHandler(HttpRequest request) {
        NHttpRequestHandler handler = null;
         if (this.handlerResolver != null) {
             String requestURI = request.getRequestLine().getUri();
             handler = this.handlerResolver.lookup(requestURI);
         }

         return handler;
    }

    protected static class ServerConnState {

        private volatile NHttpRequestHandler requestHandler;
        private volatile HttpRequest request;
        private volatile ConsumingNHttpEntity consumingEntity;
        private volatile HttpResponse response;
        private volatile ProducingNHttpEntity producingEntity;
        private volatile IOException ioex;
        private volatile HttpException httpex;
        private volatile boolean handled;

        public void finishInput() throws IOException {
            if (this.consumingEntity != null) {
                this.consumingEntity.finish();
                this.consumingEntity = null;
            }
        }

        public void finishOutput() throws IOException {
            if (this.producingEntity != null) {
                this.producingEntity.finish();
                this.producingEntity = null;
            }
        }

        public void reset() throws IOException {
            finishInput();
            this.request = null;
            finishOutput();
            this.handled = false;
            this.response = null;
            this.ioex = null;
            this.httpex = null;
            this.requestHandler = null;
        }

        public NHttpRequestHandler getRequestHandler() {
            return this.requestHandler;
        }

        public void setRequestHandler(final NHttpRequestHandler requestHandler) {
            this.requestHandler = requestHandler;
        }

        public HttpRequest getRequest() {
            return this.request;
        }

        public void setRequest(final HttpRequest request) {
            this.request = request;
        }

        public ConsumingNHttpEntity getConsumingEntity() {
            return this.consumingEntity;
        }

        public void setConsumingEntity(final ConsumingNHttpEntity consumingEntity) {
            this.consumingEntity = consumingEntity;
        }

        public HttpResponse getResponse() {
            return this.response;
        }

        public void setResponse(final HttpResponse response) {
            this.response = response;
        }

        public ProducingNHttpEntity getProducingEntity() {
            return this.producingEntity;
        }

        public void setProducingEntity(final ProducingNHttpEntity producingEntity) {
            this.producingEntity = producingEntity;
        }

        public IOException getIOException() {
            return this.ioex;
        }

        @Deprecated
        public IOException getIOExepction() {
            return this.ioex;
        }

        public void setIOException(final IOException ex) {
            this.ioex = ex;
        }

        @Deprecated
        public void setIOExepction(final IOException ex) {
            this.ioex = ex;
        }

        public HttpException getHttpException() {
            return this.httpex;
        }

        @Deprecated
        public HttpException getHttpExepction() {
            return this.httpex;
        }

        public void setHttpException(final HttpException ex) {
            this.httpex = ex;
        }

        @Deprecated
        public void setHttpExepction(final HttpException ex) {
            this.httpex = ex;
        }

        public boolean isHandled() {
            return this.handled;
        }

        public void setHandled(boolean handled) {
            this.handled = handled;
        }

    }

    private static class ResponseTriggerImpl implements NHttpResponseTrigger {

        private final ServerConnState connState;
        private final IOControl iocontrol;

        private volatile boolean triggered;

        public ResponseTriggerImpl(final ServerConnState connState, final IOControl iocontrol) {
            super();
            this.connState = connState;
            this.iocontrol = iocontrol;
        }

        public void submitResponse(final HttpResponse response) {
            if (response == null) {
                throw new IllegalArgumentException("Response may not be null");
            }
            if (this.triggered) {
                throw new IllegalStateException("Response already triggered");
            }
            this.triggered = true;
            this.connState.setResponse(response);
            this.iocontrol.requestOutput();
        }

        public void handleException(final HttpException ex) {
            if (this.triggered) {
                throw new IllegalStateException("Response already triggered");
            }
            this.triggered = true;
            this.connState.setHttpException(ex);
            this.iocontrol.requestOutput();
        }

        public void handleException(final IOException ex) {
            if (this.triggered) {
                throw new IllegalStateException("Response already triggered");
            }
            this.triggered = true;
            this.connState.setIOException(ex);
            this.iocontrol.requestOutput();
        }

    }

}

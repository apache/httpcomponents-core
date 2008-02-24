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
import org.apache.http.nio.entity.ProducingNHttpEntity;
import org.apache.http.nio.entity.SkipContentListener;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.params.DefaultedHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.util.EncodingUtils;

/**
 * HTTP service handler implementation that works with
 * {@link ConsumingNHttpEntity} and {@link ProducingNHttpEntity}. The contents
 * of HTTP headers are stored in memory, HTTP entities are streamed directly
 * from the entities to the underlying channel (and vice versa).
 * <p>
 * When using this, it is important to ensure that entities supplied for writing
 * implement ProducingNHttpEntity. Doing so will allow the entity to be written
 * out asynchronously. If entities supplied for writing do not implement
 * ProducingNHttpEntity, a delegate is added that buffers the entire contents in
 * memory. Additionally, the buffering might take place in the I/O thread, which
 * could cause I/O to block temporarily. For best results, ensure that all
 * entities set on {@link HttpResponse HttpResponses} from
 * {@link NHttpRequestHandler NHttpRequestHandlers} implement
 * ProducingNHttpEntity.
 * <p>
 * If incoming requests are entity requests, NHttpRequestHandlers are expected
 * to return a ConsumingNHttpEntity for reading the content. After the entity is
 * finished reading the data,
 * {@link NHttpRequestHandler#handle(HttpRequest, HttpResponse, NHttpResponseTrigger, HttpContext)}
 * is called to generate a response.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 * @author <a href="mailto:sberlin at gmail.com">Sam Berlin</a>
 * @author Steffen Pingel
 */
public class AsyncNHttpServiceHandler extends AbstractNHttpServiceHandler
                                         implements NHttpServiceHandler {

    protected NHttpRequestHandlerResolver handlerResolver;

    public AsyncNHttpServiceHandler(
            final HttpProcessor httpProcessor,
            final HttpResponseFactory responseFactory,
            final ConnectionReuseStrategy connStrategy,
            final ByteBufferAllocator allocator,
            final HttpParams params) {
        super(httpProcessor, responseFactory, connStrategy, allocator, params);
    }

    public AsyncNHttpServiceHandler(
            final HttpProcessor httpProcessor,
            final HttpResponseFactory responseFactory,
            final ConnectionReuseStrategy connStrategy,
            final HttpParams params) {
        this(httpProcessor, responseFactory, connStrategy,
                new HeapByteBufferAllocator(), params);
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
                if (((HttpEntityEnclosingRequest) request).expectContinue()) {
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
                HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();

                // Lookup request handler for this request
                if (requestHandler != null) {
                    ConsumingNHttpEntity consumingEntity = requestHandler.entityRequest(
                            (HttpEntityEnclosingRequest) request, context);
                    if (consumingEntity == null) {
                        consumingEntity = new ConsumingNHttpEntityTemplate(
                                entity,
                                new SkipContentListener(this.allocator));
                    }
                    ((HttpEntityEnclosingRequest) request).setEntity(consumingEntity);
                    connState.setConsumingEntity(consumingEntity);
                }

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
        connState.reset();

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
        HttpResponse response = null;

        try {

            IOException ioex = connState.getIOExepction();
            if (ioex != null) {
                throw ioex;
            }

            HttpException httpex = connState.getHttpExepction();
            if (httpex != null) {
                response = this.responseFactory.newHttpResponse(HttpVersion.HTTP_1_0,
                        HttpStatus.SC_INTERNAL_SERVER_ERROR, context);
                response.setParams(
                        new DefaultedHttpParams(response.getParams(), this.params));
                handleException(httpex, response);
                connState.setResponse(response);
            }

            response = connState.getResponse();
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
            ProducingNHttpEntity entity = (ProducingNHttpEntity) response.getEntity();
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

        HttpResponse response = null;
        try {

            this.httpProcessor.process(request, context);

            NHttpRequestHandler handler = connState.getRequestHandler();
            if (handler != null) {
                response = this.responseFactory.newHttpResponse(
                        ver, HttpStatus.SC_OK, context);
                response.setParams(
                        new DefaultedHttpParams(response.getParams(), this.params));

                handler.handle(
                        request,
                        response,
                        new ResponseTriggerImpl(connState, conn),
                        context);
            } else {
                response = this.responseFactory.newHttpResponse(ver,
                        HttpStatus.SC_NOT_IMPLEMENTED, context);
                response.setParams(
                        new DefaultedHttpParams(response.getParams(), this.params));
            }

        } catch (HttpException ex) {
            response = this.responseFactory.newHttpResponse(HttpVersion.HTTP_1_0,
                    HttpStatus.SC_INTERNAL_SERVER_ERROR, context);
            response.setParams(
                    new DefaultedHttpParams(response.getParams(), this.params));
            handleException(ex, response);
        }
        if (response != null) {
            connState.setResponse(response);
            sendResponse(conn, request, response);
            connState.setHandled(true);
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
            ProducingNHttpEntity producingEntity = (ProducingNHttpEntity) entity;
            connState.setProducingEntity(producingEntity);
        } else {
            if (!this.connStrategy.keepAlive(response, context)) {
                conn.close();
            } else {
                // Ready to process new request
                connState.reset();
                conn.requestInput();
            }
        }

        conn.submitResponse(response);
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

        public void finishInput() {
            if (this.consumingEntity != null) {
                this.consumingEntity.finish();
                this.consumingEntity = null;
            }
        }

        public void finishOutput() {
            if (this.producingEntity != null) {
                this.producingEntity.finish();
                this.producingEntity = null;
            }
        }

        public void reset() {
            finishInput();
            this.request = null;
            finishOutput();
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

        public IOException getIOExepction() {
            return this.ioex;
        }

        public void setIOExepction(final IOException ex) {
            this.ioex = ex;
        }

        public HttpException getHttpExepction() {
            return this.httpex;
        }

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
            this.connState.setHttpExepction(ex);
            this.iocontrol.requestOutput();
        }

        public void handleException(final IOException ex) {
            if (this.triggered) {
                throw new IllegalStateException("Response already triggered");
            }
            this.triggered = true;
            this.connState.setIOExepction(ex);
            this.iocontrol.requestOutput();
        }

    }

}

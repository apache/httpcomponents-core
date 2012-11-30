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

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketTimeoutException;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpConnection;
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
import org.apache.http.UnsupportedHttpVersionException;
import org.apache.http.annotation.Immutable;
import org.apache.http.concurrent.Cancellable;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.NHttpServerEventHandler;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.params.DefaultedHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;

/**
 * <tt>HttpAsyncService</tt> is a fully asynchronous HTTP server side protocol
 * handler based on the non-blocking (NIO) I/O model.
 * <tt>HttpAsyncServerProtocolHandler</tt> translates individual events fired
 * through the {@link NHttpServerEventHandler} interface into logically related
 * HTTP message exchanges.
 * <p/>
 * Upon receiving an incoming request <tt>HttpAsyncService</tt> verifies
 * the message for compliance with the server expectations using
 * {@link HttpAsyncExpectationVerifier}, if provided, and then
 * {@link HttpAsyncRequestHandlerResolver} is used to resolve the request URI
 * to a particular {@link HttpAsyncRequestHandler} intended to handle
 * the request with the given URI. The protocol handler uses the selected
 * {@link HttpAsyncRequestHandler} instance to process the incoming request
 * and to generate an outgoing response.
 * <p/>
 * <tt>HttpAsyncService</tt> relies on {@link HttpProcessor} to generate
 * mandatory protocol headers for all outgoing messages and apply common,
 * cross-cutting message transformations to all incoming and outgoing messages,
 * whereas individual {@link HttpAsyncRequestHandler}s are expected
 * to implement application specific content generation and processing.
 * <p/>
 * Individual {@link HttpAsyncRequestHandler}s do not have to submit a response
 * immediately. They can defer transmission of an HTTP response back to
 * the client without blocking the I/O thread by delegating the process of
 * request handling to another service or a worker thread. HTTP response can
 * be submitted as a later a later point of time once response content becomes
 * available.
 * <p/>
 * The following parameters can be used to customize the behavior of this
 * class:
 * <ul>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#SO_TIMEOUT}</li>
 * </ul>
 *
 * @since 4.2
 */
@Immutable // provided injected dependencies are immutable
public class HttpAsyncService implements NHttpServerEventHandler {

    static final String HTTP_EXCHANGE_STATE = "http.nio.http-exchange-state";

    private final HttpProcessor httpProcessor;
    private final ConnectionReuseStrategy connStrategy;
    private final HttpResponseFactory responseFactory;
    private final HttpAsyncRequestHandlerResolver handlerResolver;
    private final HttpAsyncExpectationVerifier expectationVerifier;
    private final HttpParams params;

    /**
     * Creates an instance of <tt>HttpAsyncServerProtocolHandler</tt>.
     *
     * @param httpProcessor HTTP protocol processor (required).
     * @param connStrategy Connection re-use strategy (required).
     * @param responseFactory HTTP response factory (required).
     * @param handlerResolver Request handler resolver.
     * @param expectationVerifier Request expectation verifier (optional).
     * @param params HTTP parameters (required).
     */
    public HttpAsyncService(
            final HttpProcessor httpProcessor,
            final ConnectionReuseStrategy connStrategy,
            final HttpResponseFactory responseFactory,
            final HttpAsyncRequestHandlerResolver handlerResolver,
            final HttpAsyncExpectationVerifier expectationVerifier,
            final HttpParams params) {
        super();
        if (httpProcessor == null) {
            throw new IllegalArgumentException("HTTP processor may not be null.");
        }
        if (connStrategy == null) {
            throw new IllegalArgumentException("Connection reuse strategy may not be null");
        }
        if (responseFactory == null) {
            throw new IllegalArgumentException("Response factory may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.httpProcessor = httpProcessor;
        this.connStrategy = connStrategy;
        this.responseFactory = responseFactory;
        this.handlerResolver = handlerResolver;
        this.expectationVerifier = expectationVerifier;
        this.params = params;
    }

    /**
     * Creates an instance of <tt>HttpAsyncServerProtocolHandler</tt>.
     *
     * @param httpProcessor HTTP protocol processor (required).
     * @param connStrategy Connection re-use strategy (required).
     * @param handlerResolver Request handler resolver.
     * @param params HTTP parameters (required).
     */
    public HttpAsyncService(
            final HttpProcessor httpProcessor,
            final ConnectionReuseStrategy connStrategy,
            final HttpAsyncRequestHandlerResolver handlerResolver,
            final HttpParams params) {
        this(httpProcessor, connStrategy, new DefaultHttpResponseFactory(),
                handlerResolver, null, params);
    }

    public void connected(final NHttpServerConnection conn) {
        State state = new State();
        conn.getContext().setAttribute(HTTP_EXCHANGE_STATE, state);
    }

    public void closed(final NHttpServerConnection conn) {
        State state = getState(conn);
        if (state != null) {
            state.setTerminated();
            closeHandlers(state);
            Cancellable cancellable = state.getCancellable();
            if (cancellable != null) {
                cancellable.cancel();
            }
            state.reset();
        }
    }

    public void exception(
            final NHttpServerConnection conn, final Exception cause) {
        State state = ensureNotNull(getState(conn));
        if (state != null) {
            state.setTerminated();
            closeHandlers(state, cause);
            Cancellable cancellable = state.getCancellable();
            if (cancellable != null) {
                cancellable.cancel();
            }
            if (cause instanceof HttpException) {
                if (conn.isResponseSubmitted()
                        || state.getResponseState().compareTo(MessageState.INIT) > 0) {
                    // There is not much that we can do if a response
                    // has already been submitted
                    closeConnection(conn);
                    log(cause);
                } else {
                    HttpContext context = state.getContext();
                    HttpAsyncResponseProducer responseProducer = handleException(
                            cause, context);
                    state.setResponseProducer(responseProducer);
                    try {
                        HttpResponse response = responseProducer.generateResponse();
                        state.setResponse(response);
                        commitFinalResponse(conn, state);
                    } catch (Exception ex) {
                        shutdownConnection(conn);
                        closeHandlers(state);
                        if (ex instanceof RuntimeException) {
                            throw (RuntimeException) ex;
                        } else {
                            log(ex);
                        }
                    }
                }
            } else {
                shutdownConnection(conn);
            }
        } else {
            shutdownConnection(conn);
            log(cause);
        }
    }

    public void requestReceived(
            final NHttpServerConnection conn) throws IOException, HttpException {
        State state = ensureNotNull(getState(conn));
        if (state.getResponseState() != MessageState.READY) {
            throw new ProtocolException("Out of sequence request message detected (pipelining is not supported)");
        }
        HttpRequest request = conn.getHttpRequest();
        HttpContext context = state.getContext();
        request.setParams(new DefaultedHttpParams(request.getParams(), this.params));

        context.setAttribute(ExecutionContext.HTTP_REQUEST, request);
        context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        this.httpProcessor.process(request, context);

        state.setRequest(request);
        HttpAsyncRequestHandler<Object> requestHandler = getRequestHandler(request);
        state.setRequestHandler(requestHandler);
        HttpAsyncRequestConsumer<Object> consumer = requestHandler.processRequest(request, context);
        state.setRequestConsumer(consumer);

        consumer.requestReceived(request);

        if (request instanceof HttpEntityEnclosingRequest) {
            if (((HttpEntityEnclosingRequest) request).expectContinue()) {
                state.setRequestState(MessageState.ACK_EXPECTED);
                HttpResponse ack = this.responseFactory.newHttpResponse(HttpVersion.HTTP_1_1,
                        HttpStatus.SC_CONTINUE, context);
                if (this.expectationVerifier != null) {
                    conn.suspendInput();
                    HttpAsyncExchange httpex = new Exchange(
                            request, ack, state, conn);
                    this.expectationVerifier.verify(httpex, context);
                } else {
                    conn.submitResponse(ack);
                    state.setRequestState(MessageState.BODY_STREAM);
                }
            } else {
                state.setRequestState(MessageState.BODY_STREAM);
            }
        } else {
            // No request content is expected.
            // Process request right away
            processRequest(conn, state);
        }
    }

    public void inputReady(
            final NHttpServerConnection conn,
            final ContentDecoder decoder) throws IOException, HttpException {
        State state = ensureNotNull(getState(conn));
        HttpAsyncRequestConsumer<?> consumer = ensureNotNull(state.getRequestConsumer());
        consumer.consumeContent(decoder, conn);
        state.setRequestState(MessageState.BODY_STREAM);
        if (decoder.isCompleted()) {
            processRequest(conn, state);
        }
    }

    public void responseReady(
            final NHttpServerConnection conn) throws IOException, HttpException {
        State state = ensureNotNull(getState(conn));
        if (state.getResponse() != null) {
            return;
        }
        HttpAsyncResponseProducer responseProducer = state.getResponseProducer();
        if (responseProducer == null) {
            return;
        }
        HttpContext context = state.getContext();
        HttpResponse response = responseProducer.generateResponse();
        int status = response.getStatusLine().getStatusCode();
        if (state.getRequestState() == MessageState.ACK_EXPECTED) {
            if (status == 100) {
                try {
                    // Make sure 100 response has no entity
                    response.setEntity(null);
                    conn.requestInput();
                    state.setRequestState(MessageState.BODY_STREAM);
                    conn.submitResponse(response);
                    responseProducer.responseCompleted(context);
                } finally {
                    state.setResponseProducer(null);
                    responseProducer.close();
                }
            } else if (status >= 400) {
                conn.resetInput();
                state.setRequestState(MessageState.COMPLETED);
                state.setResponse(response);
                commitFinalResponse(conn, state);
            } else {
                throw new HttpException("Invalid response: " + response.getStatusLine());
            }
        } else {
            if (status >= 200) {
                state.setResponse(response);
                commitFinalResponse(conn, state);
            } else {
                throw new HttpException("Invalid response: " + response.getStatusLine());
            }
        }
    }

    public void outputReady(
            final NHttpServerConnection conn,
            final ContentEncoder encoder) throws IOException {
        State state = ensureNotNull(getState(conn));
        HttpAsyncResponseProducer responseProducer = state.getResponseProducer();
        HttpContext context = state.getContext();
        HttpResponse response = state.getResponse();

        responseProducer.produceContent(encoder, conn);
        state.setResponseState(MessageState.BODY_STREAM);
        if (encoder.isCompleted()) {
            responseProducer.responseCompleted(context);
            if (!this.connStrategy.keepAlive(response, context)) {
                conn.close();
            } else {
                conn.requestInput();
            }
            closeHandlers(state);
            state.reset();
            conn.setSocketTimeout(HttpConnectionParams.getSoTimeout(this.params));
        }
    }

    public void endOfInput(final NHttpServerConnection conn) throws IOException {
        // Closing connection in an orderly manner and
        // waiting for output buffer to get flushed.
        // Do not want to wait indefinitely, though, in case
        // the opposite end is not reading
        if (conn.getSocketTimeout() <= 0) {
            conn.setSocketTimeout(1000);
        }
        conn.close();
    }

    public void timeout(final NHttpServerConnection conn) throws IOException {
        State state = getState(conn);
        if (state != null) {
            closeHandlers(state, new SocketTimeoutException());
        }
        if (conn.getStatus() == NHttpConnection.ACTIVE) {
            conn.close();
            if (conn.getStatus() == NHttpConnection.CLOSING) {
                // Give the connection some grace time to
                // close itself nicely
                conn.setSocketTimeout(250);
            }
        } else {
            conn.shutdown();
        }
    }

    private State getState(final NHttpConnection conn) {
        return (State) conn.getContext().getAttribute(HTTP_EXCHANGE_STATE);
    }

    private State ensureNotNull(final State state) {
        if (state == null) {
            throw new IllegalStateException("HTTP exchange state is null");
        }
        return state;
    }

    private HttpAsyncRequestConsumer<Object> ensureNotNull(final HttpAsyncRequestConsumer<Object> requestConsumer) {
        if (requestConsumer == null) {
            throw new IllegalStateException("Request consumer is null");
        }
        return requestConsumer;
    }

    /**
     * This method can be used to log I/O exception thrown while closing {@link Closeable}
     * objects (such as {@link HttpConnection}).
     *
     * @param ex I/O exception thrown by {@link Closeable#close()}
     */
    protected void log(final Exception ex) {
    }

    private void closeConnection(final NHttpConnection conn) {
        try {
            conn.close();
        } catch (IOException ex) {
            log(ex);
        }
    }

    private void shutdownConnection(final NHttpConnection conn) {
        try {
            conn.shutdown();
        } catch (IOException ex) {
            log(ex);
        }
    }

    private void closeHandlers(final State state, final Exception ex) {
        HttpAsyncRequestConsumer<Object> consumer = state.getRequestConsumer();
        if (consumer != null) {
            try {
                consumer.failed(ex);
            } finally {
                try {
                    consumer.close();
                } catch (IOException ioex) {
                    log(ioex);
                }
            }
        }
        HttpAsyncResponseProducer producer = state.getResponseProducer();
        if (producer != null) {
            try {
                producer.failed(ex);
            } finally {
                try {
                    producer.close();
                } catch (IOException ioex) {
                    log(ioex);
                }
            }
        }
    }

    private void closeHandlers(final State state) {
        HttpAsyncRequestConsumer<Object> consumer = state.getRequestConsumer();
        if (consumer != null) {
            try {
                consumer.close();
            } catch (IOException ioex) {
                log(ioex);
            }
        }
        HttpAsyncResponseProducer producer = state.getResponseProducer();
        if (producer != null) {
            try {
                producer.close();
            } catch (IOException ioex) {
                log(ioex);
            }
        }
    }

    protected HttpAsyncResponseProducer handleException(
            final Exception ex, final HttpContext context) {
        int code = HttpStatus.SC_INTERNAL_SERVER_ERROR;
        if (ex instanceof MethodNotSupportedException) {
            code = HttpStatus.SC_NOT_IMPLEMENTED;
        } else if (ex instanceof UnsupportedHttpVersionException) {
            code = HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED;
        } else if (ex instanceof ProtocolException) {
            code = HttpStatus.SC_BAD_REQUEST;
        }
        String message = ex.getMessage();
        if (message == null) {
            message = ex.toString();
        }
        HttpResponse response = this.responseFactory.newHttpResponse(HttpVersion.HTTP_1_1,
                code, context);
        return new ErrorResponseProducer(response, 
                new NStringEntity(message, ContentType.DEFAULT_TEXT), false);
    }

    private boolean canResponseHaveBody(final HttpRequest request, final HttpResponse response) {
        if (request != null && "HEAD".equalsIgnoreCase(request.getRequestLine().getMethod())) {
            return false;
        }
        int status = response.getStatusLine().getStatusCode();
        return status >= HttpStatus.SC_OK
            && status != HttpStatus.SC_NO_CONTENT
            && status != HttpStatus.SC_NOT_MODIFIED
            && status != HttpStatus.SC_RESET_CONTENT;
    }

    private void processRequest(
            final NHttpServerConnection conn,
            final State state) throws HttpException, IOException {
        HttpAsyncRequestHandler<Object> handler = state.getRequestHandler();
        HttpContext context = state.getContext();
        HttpAsyncRequestConsumer<?> consumer = state.getRequestConsumer();
        consumer.requestCompleted(context);
        state.setRequestState(MessageState.COMPLETED);
        state.setResponseState(MessageState.INIT);
        Exception exception = consumer.getException();
        if (exception != null) {
            HttpAsyncResponseProducer responseProducer = handleException(exception, context);
            state.setResponseProducer(responseProducer);
            conn.requestOutput();
        } else {
            HttpRequest request = state.getRequest();
            Object result = consumer.getResult();
            HttpResponse response = this.responseFactory.newHttpResponse(HttpVersion.HTTP_1_1,
                    HttpStatus.SC_OK, context);
            Exchange httpexchange = new Exchange(request, response, state, conn);
            try {
                handler.handle(result, httpexchange, context);
            } catch (HttpException ex) {
                HttpAsyncResponseProducer responseProducer = handleException(ex, context);
                state.setResponseProducer(responseProducer);
                conn.requestOutput();
            }
        }
    }

    private void commitFinalResponse(
            final NHttpServerConnection conn,
            final State state) throws IOException, HttpException {
        HttpContext context = state.getContext();
        HttpRequest request = state.getRequest();
        HttpResponse response = state.getResponse();

        response.setParams(new DefaultedHttpParams(response.getParams(), this.params));
        context.setAttribute(ExecutionContext.HTTP_RESPONSE, response);
        this.httpProcessor.process(response, context);

        HttpEntity entity = response.getEntity();
        if (entity != null && !canResponseHaveBody(request, response)) {
            response.setEntity(null);
            entity = null;
        }

        conn.submitResponse(response);

        if (entity == null) {
            HttpAsyncResponseProducer responseProducer = state.getResponseProducer();
            responseProducer.responseCompleted(context);
            if (!this.connStrategy.keepAlive(response, context)) {
                conn.close();
            } else {
                // Ready to process new request
                conn.requestInput();
            }
            closeHandlers(state);
            state.reset();
            conn.setSocketTimeout(HttpConnectionParams.getSoTimeout(this.params));
        } else {
            state.setResponseState(MessageState.BODY_STREAM);
        }
    }

    @SuppressWarnings("unchecked")
    private HttpAsyncRequestHandler<Object> getRequestHandler(final HttpRequest request) {
        HttpAsyncRequestHandler<Object> handler = null;
        if (this.handlerResolver != null) {
            String requestURI = request.getRequestLine().getUri();
            handler = (HttpAsyncRequestHandler<Object>) this.handlerResolver.lookup(requestURI);
        }
        if (handler == null) {
            handler = new NullRequestHandler();
        }
        return handler;
    }

    static class State {

        private final BasicHttpContext context;
        private volatile boolean terminated;
        private volatile HttpAsyncRequestHandler<Object> requestHandler;
        private volatile MessageState requestState;
        private volatile MessageState responseState;
        private volatile HttpAsyncRequestConsumer<Object> requestConsumer;
        private volatile HttpAsyncResponseProducer responseProducer;
        private volatile HttpRequest request;
        private volatile HttpResponse response;
        private volatile Cancellable cancellable;

        State() {
            super();
            this.context = new BasicHttpContext();
            this.requestState = MessageState.READY;
            this.responseState = MessageState.READY;
        }

        public HttpContext getContext() {
            return this.context;
        }

        public boolean isTerminated() {
            return this.terminated;
        }

        public void setTerminated() {
            this.terminated = true;
        }

        public HttpAsyncRequestHandler<Object> getRequestHandler() {
            return this.requestHandler;
        }

        public void setRequestHandler(final HttpAsyncRequestHandler<Object> requestHandler) {
            this.requestHandler = requestHandler;
        }

        public MessageState getRequestState() {
            return this.requestState;
        }

        public void setRequestState(final MessageState state) {
            this.requestState = state;
        }

        public MessageState getResponseState() {
            return this.responseState;
        }

        public void setResponseState(final MessageState state) {
            this.responseState = state;
        }

        public HttpAsyncRequestConsumer<Object> getRequestConsumer() {
            return this.requestConsumer;
        }

        public void setRequestConsumer(final HttpAsyncRequestConsumer<Object> requestConsumer) {
            this.requestConsumer = requestConsumer;
        }

        public HttpAsyncResponseProducer getResponseProducer() {
            return this.responseProducer;
        }

        public void setResponseProducer(final HttpAsyncResponseProducer responseProducer) {
            this.responseProducer = responseProducer;
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

        public Cancellable getCancellable() {
            return this.cancellable;
        }

        public void setCancellable(final Cancellable cancellable) {
            this.cancellable = cancellable;
        }

        public void reset() {
            this.context.clear();
            this.responseState = MessageState.READY;
            this.requestState = MessageState.READY;
            this.requestHandler = null;
            this.requestConsumer = null;
            this.responseProducer = null;
            this.request = null;
            this.response = null;
            this.cancellable = null;
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("request state: ");
            buf.append(this.requestState);
            buf.append("; request: ");
            if (this.request != null) {
                buf.append(this.request.getRequestLine());
            }
            buf.append("; response state: ");
            buf.append(this.responseState);
            buf.append("; response: ");
            if (this.response != null) {
                buf.append(this.response.getStatusLine());
            }
            buf.append(";");
            return buf.toString();
        }

    }

    static class Exchange implements HttpAsyncExchange {

        private final HttpRequest request;
        private final HttpResponse response;
        private final State state;
        private final NHttpServerConnection conn;

        private volatile boolean completed;

        public Exchange(
                final HttpRequest request,
                final HttpResponse response,
                final State state,
                final NHttpServerConnection conn) {
            super();
            this.request = request;
            this.response = response;
            this.state = state;
            this.conn = conn;
        }

        public HttpRequest getRequest() {
            return this.request;
        }

        public HttpResponse getResponse() {
            return this.response;
        }

        public void setCallback(final Cancellable cancellable) {
            synchronized (this) {
                if (this.completed) {
                    throw new IllegalStateException("Response already submitted");
                }
                if (this.state.isTerminated() && cancellable != null) {
                    cancellable.cancel();
                } else {
                    this.state.setCancellable(cancellable);
                    this.conn.requestInput();
                }
            }
        }

        public void submitResponse(final HttpAsyncResponseProducer responseProducer) {
            if (responseProducer == null) {
                throw new IllegalArgumentException("Response producer may not be null");
            }
            synchronized (this) {
                if (this.completed) {
                    throw new IllegalStateException("Response already submitted");
                }
                this.completed = true;
                if (!this.state.isTerminated()) {
                    this.state.setResponseProducer(responseProducer);
                    this.state.setCancellable(null);
                    this.conn.requestOutput();
                } else {
                    try {
                        responseProducer.close();
                    } catch (IOException ex) {
                    }
                }
            }
        }

        public void submitResponse() {
            submitResponse(new BasicAsyncResponseProducer(this.response));
        }

        public boolean isCompleted() {
            return this.completed;
        }

        public void setTimeout(int timeout) {
            this.conn.setSocketTimeout(timeout);
        }

        public int getTimeout() {
            return this.conn.getSocketTimeout();
        }

    }

}

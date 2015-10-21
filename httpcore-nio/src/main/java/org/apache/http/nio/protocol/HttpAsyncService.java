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
import java.net.SocketTimeoutException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.ExceptionLogger;
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
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.NHttpServerEventHandler;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.nio.reactor.SessionBufferStatus;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.util.Args;
import org.apache.http.util.Asserts;

/**
 * {@code HttpAsyncService} is a fully asynchronous HTTP server side protocol
 * handler based on the non-blocking (NIO) I/O model.
 * {@code HttpAsyncServerProtocolHandler} translates individual events fired
 * through the {@link NHttpServerEventHandler} interface into logically related
 * HTTP message exchanges.
 * <p>
 * Upon receiving an incoming request {@code HttpAsyncService} verifies
 * the message for compliance with the server expectations using
 * {@link HttpAsyncExpectationVerifier}, if provided, and then
 * {@link HttpAsyncRequestHandlerMapper} is used to map the request
 * to a particular {@link HttpAsyncRequestHandler} intended to handle
 * the request with the given URI. The protocol handler uses the selected
 * {@link HttpAsyncRequestHandler} instance to process the incoming request
 * and to generate an outgoing response.
 * <p>
 * {@code HttpAsyncService} relies on {@link HttpProcessor} to generate
 * mandatory protocol headers for all outgoing messages and apply common,
 * cross-cutting message transformations to all incoming and outgoing messages,
 * whereas individual {@link HttpAsyncRequestHandler}s are expected
 * to implement application specific content generation and processing.
 * <p>
 * Individual {@link HttpAsyncRequestHandler}s do not have to submit a response
 * immediately. They can defer transmission of an HTTP response back to
 * the client without blocking the I/O thread by delegating the process of
 * request handling to another service or a worker thread. HTTP response can
 * be submitted as a later a later point of time once response content becomes
 * available.
 *
 * @since 4.2
 */
@SuppressWarnings("deprecation")
@Immutable // provided injected dependencies are immutable
public class HttpAsyncService implements NHttpServerEventHandler {

    static final String HTTP_EXCHANGE_STATE = "http.nio.http-exchange-state";

    private final HttpProcessor httpProcessor;
    private final ConnectionReuseStrategy connStrategy;
    private final HttpResponseFactory responseFactory;
    private final HttpAsyncRequestHandlerMapper handlerMapper;
    private final HttpAsyncExpectationVerifier expectationVerifier;
    private final ExceptionLogger exceptionLogger;

    /**
     * Creates new instance of {@code HttpAsyncServerProtocolHandler}.
     *
     * @param httpProcessor HTTP protocol processor (required).
     * @param connStrategy Connection re-use strategy (required).
     * @param responseFactory HTTP response factory (required).
     * @param handlerResolver Request handler resolver.
     * @param expectationVerifier Request expectation verifier (optional).
     * @param params HTTP parameters (required).
     *
     * @deprecated (4.3) use {@link HttpAsyncService#HttpAsyncService(HttpProcessor,
     *  ConnectionReuseStrategy, HttpResponseFactory, HttpAsyncRequestHandlerMapper,
     *    HttpAsyncExpectationVerifier)}
     */
    @Deprecated
    public HttpAsyncService(
            final HttpProcessor httpProcessor,
            final ConnectionReuseStrategy connStrategy,
            final HttpResponseFactory responseFactory,
            final HttpAsyncRequestHandlerResolver handlerResolver,
            final HttpAsyncExpectationVerifier expectationVerifier,
            final HttpParams params) {
        this(httpProcessor,
             connStrategy,
             responseFactory,
             new HttpAsyncRequestHandlerResolverAdapter(handlerResolver),
             expectationVerifier);
    }

    /**
     * Creates new instance of {@code HttpAsyncServerProtocolHandler}.
     *
     * @param httpProcessor HTTP protocol processor (required).
     * @param connStrategy Connection re-use strategy (required).
     * @param handlerResolver Request handler resolver.
     * @param params HTTP parameters (required).
     *
     * @deprecated (4.3) use {@link HttpAsyncService#HttpAsyncService(HttpProcessor,
     *   ConnectionReuseStrategy, HttpResponseFactory, HttpAsyncRequestHandlerMapper,
     *   HttpAsyncExpectationVerifier)}
     */
    @Deprecated
    public HttpAsyncService(
            final HttpProcessor httpProcessor,
            final ConnectionReuseStrategy connStrategy,
            final HttpAsyncRequestHandlerResolver handlerResolver,
            final HttpParams params) {
        this(httpProcessor,
             connStrategy,
             DefaultHttpResponseFactory.INSTANCE,
             new HttpAsyncRequestHandlerResolverAdapter(handlerResolver),
             null);
    }

    /**
     * Creates new instance of {@code HttpAsyncServerProtocolHandler}.
     *
     * @param httpProcessor HTTP protocol processor.
     * @param connStrategy Connection re-use strategy. If {@code null}
     *   {@link DefaultConnectionReuseStrategy#INSTANCE} will be used.
     * @param responseFactory HTTP response factory. If {@code null}
     *   {@link DefaultHttpResponseFactory#INSTANCE} will be used.
     * @param handlerMapper Request handler mapper.
     * @param expectationVerifier Request expectation verifier. May be {@code null}.
     *
     * @since 4.3
     */
    public HttpAsyncService(
            final HttpProcessor httpProcessor,
            final ConnectionReuseStrategy connStrategy,
            final HttpResponseFactory responseFactory,
            final HttpAsyncRequestHandlerMapper handlerMapper,
            final HttpAsyncExpectationVerifier expectationVerifier) {
        this(httpProcessor, connStrategy, responseFactory, handlerMapper, expectationVerifier, null);
    }

    /**
     * Creates new instance of {@code HttpAsyncServerProtocolHandler}.
     *
     * @param httpProcessor HTTP protocol processor.
     * @param connStrategy Connection re-use strategy. If {@code null}
     *   {@link DefaultConnectionReuseStrategy#INSTANCE} will be used.
     * @param responseFactory HTTP response factory. If {@code null}
     *   {@link DefaultHttpResponseFactory#INSTANCE} will be used.
     * @param handlerMapper Request handler mapper.
     * @param expectationVerifier Request expectation verifier. May be {@code null}.
     * @param exceptionLogger Exception logger. If {@code null}
     *   {@link ExceptionLogger#NO_OP} will be used. Please note that the exception
     *   logger will be only used to log I/O exception thrown while closing
     *   {@link java.io.Closeable} objects (such as {@link org.apache.http.HttpConnection}).
     *
     * @since 4.4
     */
    public HttpAsyncService(
            final HttpProcessor httpProcessor,
            final ConnectionReuseStrategy connStrategy,
            final HttpResponseFactory responseFactory,
            final HttpAsyncRequestHandlerMapper handlerMapper,
            final HttpAsyncExpectationVerifier expectationVerifier,
            final ExceptionLogger exceptionLogger) {
        super();
        this.httpProcessor = Args.notNull(httpProcessor, "HTTP processor");
        this.connStrategy = connStrategy != null ? connStrategy :
                DefaultConnectionReuseStrategy.INSTANCE;
        this.responseFactory = responseFactory != null ? responseFactory :
                DefaultHttpResponseFactory.INSTANCE;
        this.handlerMapper = handlerMapper;
        this.expectationVerifier = expectationVerifier;
        this.exceptionLogger = exceptionLogger != null ? exceptionLogger : ExceptionLogger.NO_OP;
    }

    /**
     * Creates new instance of {@code HttpAsyncServerProtocolHandler}.
     *
     * @param httpProcessor HTTP protocol processor.
     * @param handlerMapper Request handler mapper.
     *
     * @since 4.3
     */
    public HttpAsyncService(
            final HttpProcessor httpProcessor,
            final HttpAsyncRequestHandlerMapper handlerMapper) {
        this(httpProcessor, null, null, handlerMapper, null);
    }

    /**
     * Creates new instance of {@code HttpAsyncServerProtocolHandler}.
     *
     * @param httpProcessor HTTP protocol processor.
     * @param handlerMapper Request handler mapper.
     * @param exceptionLogger Exception logger. If {@code null}
     *   {@link ExceptionLogger#NO_OP} will be used. Please note that the exception
     *   logger will be only used to log I/O exception thrown while closing
     *   {@link java.io.Closeable} objects (such as {@link org.apache.http.HttpConnection}).
     *
     * @since 4.4
     */
    public HttpAsyncService(
            final HttpProcessor httpProcessor,
            final HttpAsyncRequestHandlerMapper handlerMapper,
            final ExceptionLogger exceptionLogger) {
        this(httpProcessor, null, null, handlerMapper, null, exceptionLogger);
    }

    @Override
    public void connected(final NHttpServerConnection conn) {
        final State state = new State();
        conn.getContext().setAttribute(HTTP_EXCHANGE_STATE, state);
    }

    @Override
    public void closed(final NHttpServerConnection conn) {
        final State state = (State) conn.getContext().removeAttribute(HTTP_EXCHANGE_STATE);
        if (state != null) {
            state.setTerminated();
            closeHandlers(state);
            final Cancellable cancellable = state.getCancellable();
            if (cancellable != null) {
                cancellable.cancel();
            }
        }
    }

    @Override
    public void exception(
            final NHttpServerConnection conn, final Exception cause) {
        final State state = getState(conn);
        if (state == null) {
            shutdownConnection(conn);
            log(cause);
            return;
        }
        state.setTerminated();
        closeHandlers(state, cause);
        final Cancellable cancellable = state.getCancellable();
        if (cancellable != null) {
            cancellable.cancel();
        }
        final Queue<PipelineEntry> pipeline = state.getPipeline();
        if (!pipeline.isEmpty()
                || conn.isResponseSubmitted()
                || state.getResponseState().compareTo(MessageState.INIT) > 0) {
            // There is not much that we can do if a response
            // has already been submitted or pipelining is being used.
            shutdownConnection(conn);
        } else {
            try {
                final Incoming incoming = state.getIncoming();
                final HttpRequest request = incoming != null ? incoming.getRequest() : null;
                final HttpContext context = incoming != null ? incoming.getContext() : new BasicHttpContext();
                final HttpAsyncResponseProducer responseProducer = handleException(cause, context);
                final HttpResponse response = responseProducer.generateResponse();
                final Outgoing outgoing = new Outgoing(request, response, responseProducer, context);
                state.setResponseState(MessageState.INIT);
                state.setOutgoing(outgoing);
                commitFinalResponse(conn, state);
            } catch (final Exception ex) {
                shutdownConnection(conn);
                closeHandlers(state);
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException) ex;
                } else {
                    log(ex);
                }
            }
        }
    }

    @Override
    public void requestReceived(
            final NHttpServerConnection conn) throws IOException, HttpException {
        final State state = getState(conn);
        Asserts.notNull(state, "Connection state");
        Asserts.check(state.getRequestState() == MessageState.READY,
                "Unexpected request state %s", state.getRequestState());

        final HttpRequest request = conn.getHttpRequest();
        final HttpContext context = new BasicHttpContext();

        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
        context.setAttribute(HttpCoreContext.HTTP_CONNECTION, conn);
        this.httpProcessor.process(request, context);

        final HttpAsyncRequestHandler<Object> requestHandler = getRequestHandler(request);
        final HttpAsyncRequestConsumer<Object> consumer = requestHandler.processRequest(request, context);
        consumer.requestReceived(request);

        final Incoming incoming = new Incoming(request, requestHandler, consumer, context);
        state.setIncoming(incoming);

        if (request instanceof HttpEntityEnclosingRequest) {

            // If 100-continue is expected make sure
            // there is no pending response data, no pipelined requests or buffered input
            if (((HttpEntityEnclosingRequest) request).expectContinue()
                        && state.getResponseState() == MessageState.READY
                        && state.getPipeline().isEmpty()
                        && !(conn instanceof SessionBufferStatus && ((SessionBufferStatus) conn).hasBufferedInput())) {

                state.setRequestState(MessageState.ACK_EXPECTED);
                final HttpResponse ack = this.responseFactory.newHttpResponse(HttpVersion.HTTP_1_1,
                        HttpStatus.SC_CONTINUE, context);
                if (this.expectationVerifier != null) {
                    conn.suspendInput();
                    conn.suspendOutput();
                    final HttpAsyncExchange httpAsyncExchange = new HttpAsyncExchangeImpl(
                            request, ack, state, conn, context);
                    this.expectationVerifier.verify(httpAsyncExchange, context);
                } else {
                    conn.submitResponse(ack);
                    state.setRequestState(MessageState.BODY_STREAM);
                }
            } else {
                state.setRequestState(MessageState.BODY_STREAM);
            }
        } else {
            // No request content is expected. Process request right away
            completeRequest(incoming, conn, state);
        }
    }

    @Override
    public void inputReady(
            final NHttpServerConnection conn,
            final ContentDecoder decoder) throws IOException, HttpException {
        final State state = getState(conn);
        Asserts.notNull(state, "Connection state");
        Asserts.check(state.getRequestState() == MessageState.BODY_STREAM,
                "Unexpected request state %s", state.getRequestState());

        final Incoming incoming = state.getIncoming();
        Asserts.notNull(incoming, "Incoming request");
        final HttpAsyncRequestConsumer<?> consumer = incoming.getConsumer();
        consumer.consumeContent(decoder, conn);
        if (decoder.isCompleted()) {
            completeRequest(incoming, conn, state);
        }
    }

    @Override
    public void responseReady(
            final NHttpServerConnection conn) throws IOException, HttpException {
        final State state = getState(conn);
        Asserts.notNull(state, "Connection state");
        Asserts.check(state.getResponseState() == MessageState.READY ||
                        state.getResponseState() == MessageState.INIT,
                "Unexpected response state %s", state.getResponseState());

        if (state.getRequestState() == MessageState.ACK_EXPECTED) {
            final Outgoing outgoing = state.getOutgoing();
            Asserts.notNull(outgoing, "Outgoing response");

            final HttpResponse response = outgoing.getResponse();
            final int status = response.getStatusLine().getStatusCode();
            if (status == 100) {
                final HttpContext context = outgoing.getContext();
                final HttpAsyncResponseProducer responseProducer = outgoing.getProducer();
                try {
                    // Make sure 100 response has no entity
                    response.setEntity(null);
                    conn.requestInput();
                    state.setRequestState(MessageState.BODY_STREAM);
                    state.setOutgoing(null);
                    conn.submitResponse(response);
                    responseProducer.responseCompleted(context);
                } finally {
                    responseProducer.close();
                }
            } else if (status >= 400) {
                conn.resetInput();
                state.setRequestState(MessageState.READY);
                commitFinalResponse(conn, state);
            } else {
                throw new HttpException("Invalid response: " + response.getStatusLine());
            }
        } else {
            if (state.getResponseState() == MessageState.READY) {
                final Queue<PipelineEntry> pipeline = state.getPipeline();
                final PipelineEntry pipelineEntry = pipeline.poll();
                if (pipelineEntry == null) {
                    conn.suspendOutput();
                    return;
                }
                state.setResponseState(MessageState.INIT);
                final Object result = pipelineEntry.getResult();
                final HttpRequest request = pipelineEntry.getRequest();
                final HttpContext context = pipelineEntry.getContext();
                if (result != null) {
                    final HttpResponse response = this.responseFactory.newHttpResponse(HttpVersion.HTTP_1_1,
                            HttpStatus.SC_OK, context);
                    final HttpAsyncExchangeImpl httpExchange = new HttpAsyncExchangeImpl(
                            request, response, state, conn, context);
                    final HttpAsyncRequestHandler<Object> handler = pipelineEntry.getHandler();
                    conn.suspendOutput();
                    try {
                        handler.handle(result, httpExchange, context);
                    } catch (RuntimeException ex) {
                        throw ex;
                    } catch (Exception ex) {
                        pipeline.add(new PipelineEntry(
                            request,
                            null,
                            ex,
                            handler,
                            context));
                        state.setResponseState(MessageState.READY);
                        responseReady(conn);
                        return;
                    }
                } else {
                    final Exception exception = pipelineEntry.getException();
                    final HttpAsyncResponseProducer responseProducer = handleException(
                            exception != null ? exception : new HttpException("Internal error processing request"),
                            context);
                    final HttpResponse error = responseProducer.generateResponse();
                    state.setOutgoing(new Outgoing(request, error, responseProducer, context));
                }
            }
            if (state.getResponseState() == MessageState.INIT) {
                final Outgoing outgoing;
                synchronized (state) {
                    outgoing = state.getOutgoing();
                    if (outgoing == null) {
                        conn.suspendOutput();
                        return;
                    }
                }
                final HttpResponse response = outgoing.getResponse();
                final int status = response.getStatusLine().getStatusCode();
                if (status >= 200) {
                    commitFinalResponse(conn, state);
                } else {
                    throw new HttpException("Invalid response: " + response.getStatusLine());
                }
            }
        }
    }

    @Override
    public void outputReady(
            final NHttpServerConnection conn,
            final ContentEncoder encoder) throws HttpException, IOException {
        final State state = getState(conn);
        Asserts.notNull(state, "Connection state");
        Asserts.check(state.getResponseState() == MessageState.BODY_STREAM,
                "Unexpected response state %s", state.getResponseState());

        final Outgoing outgoing = state.getOutgoing();
        Asserts.notNull(outgoing, "Outgoing response");
        final HttpAsyncResponseProducer responseProducer = outgoing.getProducer();

        responseProducer.produceContent(encoder, conn);

        if (encoder.isCompleted()) {
            completeResponse(outgoing, conn, state);
        }
    }

    @Override
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

    @Override
    public void timeout(final NHttpServerConnection conn) throws IOException {
        final State state = getState(conn);
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

    /**
     * This method can be used to log I/O exception thrown while closing
     * {@link java.io.Closeable} objects (such as
     * {@link org.apache.http.HttpConnection}).
     *
     * @param ex I/O exception thrown by {@link java.io.Closeable#close()}
     */
    protected void log(final Exception ex) {
        this.exceptionLogger.log(ex);
    }

    private void shutdownConnection(final NHttpConnection conn) {
        try {
            conn.shutdown();
        } catch (final IOException ex) {
            log(ex);
        }
    }

    private void closeHandlers(final State state, final Exception ex) {
        final HttpAsyncRequestConsumer<Object> consumer =
                state.getIncoming() != null ? state.getIncoming().getConsumer() : null;
        if (consumer != null) {
            try {
                consumer.failed(ex);
            } finally {
                try {
                    consumer.close();
                } catch (final IOException ioex) {
                    log(ioex);
                }
            }
        }
        final HttpAsyncResponseProducer producer =
                state.getOutgoing() != null ? state.getOutgoing().getProducer() : null;
        if (producer != null) {
            try {
                producer.failed(ex);
            } finally {
                try {
                    producer.close();
                } catch (final IOException ioex) {
                    log(ioex);
                }
            }
        }
    }

    private void closeHandlers(final State state) {
        final HttpAsyncRequestConsumer<Object> consumer =
                state.getIncoming() != null ? state.getIncoming().getConsumer() : null;
        if (consumer != null) {
            try {
                consumer.close();
            } catch (final IOException ioex) {
                log(ioex);
            }
        }
        final HttpAsyncResponseProducer producer =
                state.getOutgoing() != null ? state.getOutgoing().getProducer() : null;
        if (producer != null) {
            try {
                producer.close();
            } catch (final IOException ioex) {
                log(ioex);
            }
        }
    }

    protected HttpAsyncResponseProducer handleException(
            final Exception ex, final HttpContext context) {
        final int code;
        if (ex instanceof MethodNotSupportedException) {
            code = HttpStatus.SC_NOT_IMPLEMENTED;
        } else if (ex instanceof UnsupportedHttpVersionException) {
            code = HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED;
        } else if (ex instanceof ProtocolException) {
            code = HttpStatus.SC_BAD_REQUEST;
        } else {
            code = HttpStatus.SC_INTERNAL_SERVER_ERROR;
        }
        String message = ex.getMessage();
        if (message == null) {
            message = ex.toString();
        }
        final HttpResponse response = this.responseFactory.newHttpResponse(HttpVersion.HTTP_1_1,
                code, context);
        return new ErrorResponseProducer(response,
                new NStringEntity(message, ContentType.DEFAULT_TEXT), false);
    }

    /**
     * This method can be used to handle callback set up happened after
     * response submission.
     *
     * @param cancellable Request cancellation callback.
     * @param context Request context.
     *
     * @since 4.4
     */
    protected void handleAlreadySubmittedResponse(
            final Cancellable cancellable, final HttpContext context) {
        throw new IllegalStateException("Response already submitted");
    }

    /**
     * This method can be used to handle double response submission.
     *
     * @param responseProducer Response producer for second response.
     * @param context Request context.
     *
     * @since 4.4
     */
    protected void handleAlreadySubmittedResponse(
            final HttpAsyncResponseProducer responseProducer,
            final HttpContext context) {
        throw new IllegalStateException("Response already submitted");
    }

    private boolean canResponseHaveBody(final HttpRequest request, final HttpResponse response) {
        if (request != null && "HEAD".equalsIgnoreCase(request.getRequestLine().getMethod())) {
            return false;
        }
        final int status = response.getStatusLine().getStatusCode();
        return status >= HttpStatus.SC_OK
            && status != HttpStatus.SC_NO_CONTENT
            && status != HttpStatus.SC_NOT_MODIFIED
            && status != HttpStatus.SC_RESET_CONTENT;
    }

    private void completeRequest(
            final Incoming incoming,
            final NHttpServerConnection conn,
            final State state) throws IOException, HttpException {
        state.setRequestState(MessageState.READY);
        state.setIncoming(null);

        final PipelineEntry pipelineEntry;
        final HttpAsyncRequestConsumer<?> consumer = incoming.getConsumer();
        try {
            final HttpContext context = incoming.getContext();
            consumer.requestCompleted(context);
            pipelineEntry = new PipelineEntry(
                    incoming.getRequest(),
                    consumer.getResult(),
                    consumer.getException(),
                    incoming.getHandler(),
                    context);
        } finally {
            consumer.close();
        }
        final Queue<PipelineEntry> pipeline = state.getPipeline();
        pipeline.add(pipelineEntry);
        if (state.getResponseState() == MessageState.READY) {
            conn.requestOutput();
        }
    }

    private void commitFinalResponse(
            final NHttpServerConnection conn,
            final State state) throws IOException, HttpException {
        final Outgoing outgoing = state.getOutgoing();
        Asserts.notNull(outgoing, "Outgoing response");
        final HttpRequest request = outgoing.getRequest();
        final HttpResponse response = outgoing.getResponse();
        final HttpContext context = outgoing.getContext();

        context.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
        this.httpProcessor.process(response, context);

        HttpEntity entity = response.getEntity();
        if (entity != null && !canResponseHaveBody(request, response)) {
            response.setEntity(null);
            entity = null;
        }

        conn.submitResponse(response);

        if (entity == null) {
            completeResponse(outgoing, conn, state);
        } else {
            state.setResponseState(MessageState.BODY_STREAM);
        }
    }

    private void completeResponse(
            final Outgoing outgoing,
            final NHttpServerConnection conn,
            final State state) throws IOException, HttpException {
        final HttpContext context = outgoing.getContext();
        final HttpResponse response = outgoing.getResponse();
        final HttpAsyncResponseProducer responseProducer = outgoing.getProducer();
        try {
            responseProducer.responseCompleted(context);
            state.setOutgoing(null);
            state.setCancellable(null);
            state.setResponseState(MessageState.READY);
        } finally {
            responseProducer.close();
        }
        if (!this.connStrategy.keepAlive(response, context)) {
            conn.close();
        } else {
            conn.requestInput();
        }
    }

    @SuppressWarnings("unchecked")
    private HttpAsyncRequestHandler<Object> getRequestHandler(final HttpRequest request) {
        HttpAsyncRequestHandler<Object> handler = null;
        if (this.handlerMapper != null) {
            handler = (HttpAsyncRequestHandler<Object>) this.handlerMapper.lookup(request);
        }
        if (handler == null) {
            handler = new NullRequestHandler();
        }
        return handler;
    }

    static class Incoming {

        private final HttpRequest request;
        private final HttpAsyncRequestHandler<Object> handler;
        private final HttpAsyncRequestConsumer<Object> consumer;
        private final HttpContext context;

        Incoming(
                final HttpRequest request,
                final HttpAsyncRequestHandler<Object> handler,
                final HttpAsyncRequestConsumer<Object> consumer,
                final HttpContext context) {
            this.request = request;
            this.handler = handler;
            this.consumer = consumer;
            this.context = context;
        }

        public HttpRequest getRequest() {
            return this.request;
        }

        public HttpAsyncRequestHandler<Object> getHandler() {
            return this.handler;
        }

        public HttpAsyncRequestConsumer<Object> getConsumer() {
            return this.consumer;
        }

        public HttpContext getContext() {
            return this.context;
        }
    }

    static class Outgoing {

        private final HttpRequest request;
        private final HttpResponse response;
        private final HttpAsyncResponseProducer producer;
        private final HttpContext context;

        Outgoing(
                final HttpRequest request,
                final HttpResponse response,
                final HttpAsyncResponseProducer producer,
                final HttpContext context) {
            this.request = request;
            this.response = response;
            this.producer = producer;
            this.context = context;
        }

        public HttpRequest getRequest() {
            return this.request;
        }

        public HttpResponse getResponse() {
            return this.response;
        }

        public HttpAsyncResponseProducer getProducer() {
            return this.producer;
        }

        public HttpContext getContext() {
            return this.context;
        }
    }

    static class PipelineEntry {

        private final HttpRequest request;
        private final Object result;
        private final Exception exception;
        private final HttpAsyncRequestHandler<Object> handler;
        private final HttpContext context;

        PipelineEntry(
                final HttpRequest request,
                final Object result,
                final Exception exception,
                final HttpAsyncRequestHandler<Object> handler,
                final HttpContext context) {
            this.request = request;
            this.result = result;
            this.exception = exception;
            this.handler = handler;
            this.context = context;
        }

        public HttpRequest getRequest() {
            return this.request;
        }

        public Object getResult() {
            return this.result;
        }

        public Exception getException() {
            return this.exception;
        }

        public HttpAsyncRequestHandler<Object> getHandler() {
            return this.handler;
        }

        public HttpContext getContext() {
            return this.context;
        }

    }

    static class State {

        private final Queue<PipelineEntry> pipeline;
        private volatile boolean terminated;
        private volatile MessageState requestState;
        private volatile MessageState responseState;
        private volatile Incoming incoming;
        private volatile Outgoing outgoing;
        private volatile Cancellable cancellable;

        State() {
            super();
            this.pipeline = new ConcurrentLinkedQueue<PipelineEntry>();
            this.requestState = MessageState.READY;
            this.responseState = MessageState.READY;
        }

        public boolean isTerminated() {
            return this.terminated;
        }

        public void setTerminated() {
            this.terminated = true;
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

        public Incoming getIncoming() {
            return this.incoming;
        }

        public void setIncoming(final Incoming incoming) {
            this.incoming = incoming;
        }

        public Outgoing getOutgoing() {
            return this.outgoing;
        }

        public void setOutgoing(final Outgoing outgoing) {
            this.outgoing = outgoing;
        }

        public Cancellable getCancellable() {
            return this.cancellable;
        }

        public void setCancellable(final Cancellable cancellable) {
            this.cancellable = cancellable;
        }

        public Queue<PipelineEntry> getPipeline() {
            return this.pipeline;
        }

        @Override
        public String toString() {
            final StringBuilder buf = new StringBuilder();
            buf.append("[incoming ");
            buf.append(this.requestState);
            if (this.incoming != null) {
                buf.append(" ");
                buf.append(this.incoming.getRequest().getRequestLine());
            }
            buf.append("; outgoing ");
            buf.append(this.responseState);
            if (this.outgoing != null) {
                buf.append(" ");
                buf.append(this.outgoing.getResponse().getStatusLine());
            }
            buf.append("]");
            return buf.toString();
        }

    }

    class HttpAsyncExchangeImpl implements HttpAsyncExchange {

        private final AtomicBoolean completed = new AtomicBoolean();
        private final HttpRequest request;
        private final HttpResponse response;
        private final State state;
        private final NHttpServerConnection conn;
        private final HttpContext context;

        public HttpAsyncExchangeImpl(
                final HttpRequest request,
                final HttpResponse response,
                final State state,
                final NHttpServerConnection conn,
                final HttpContext context) {
            super();
            this.request = request;
            this.response = response;
            this.state = state;
            this.conn = conn;
            this.context = context;
        }

        @Override
        public HttpRequest getRequest() {
            return this.request;
        }

        @Override
        public HttpResponse getResponse() {
            return this.response;
        }

        @Override
        public void setCallback(final Cancellable cancellable) {
            if (this.completed.get()) {
                handleAlreadySubmittedResponse(cancellable, context);
            } else if (this.state.isTerminated() && cancellable != null) {
                cancellable.cancel();
            } else {
                this.state.setCancellable(cancellable);
            }
        }

        @Override
        public void submitResponse(final HttpAsyncResponseProducer responseProducer) {
            Args.notNull(responseProducer, "Response producer");
            if (this.completed.getAndSet(true)) {
                handleAlreadySubmittedResponse(responseProducer, context);
            } else if (!this.state.isTerminated()) {
                final HttpResponse response = responseProducer.generateResponse();
                final Outgoing outgoing = new Outgoing(
                        this.request, response, responseProducer, this.context);

                synchronized (this.state) {
                    this.state.setOutgoing(outgoing);
                    this.state.setCancellable(null);
                    this.conn.requestOutput();
                }

            } else {
                try {
                    responseProducer.close();
                } catch (final IOException ex) {
                    log(ex);
                }
            }
        }

        @Override
        public void submitResponse() {
            submitResponse(new BasicAsyncResponseProducer(this.response));
        }

        @Override
        public boolean isCompleted() {
            return this.completed.get();
        }

        @Override
        public void setTimeout(final int timeout) {
            this.conn.setSocketTimeout(timeout);
        }

        @Override
        public int getTimeout() {
            return this.conn.getSocketTimeout();
        }

    }

    /**
     * Adaptor class to transition from HttpAsyncRequestHandlerResolver to HttpAsyncRequestHandlerMapper.
     */
    @Deprecated
    private static class HttpAsyncRequestHandlerResolverAdapter implements HttpAsyncRequestHandlerMapper {

        private final HttpAsyncRequestHandlerResolver resolver;

        public HttpAsyncRequestHandlerResolverAdapter(final HttpAsyncRequestHandlerResolver resolver) {
            this.resolver = resolver;
        }

        @Override
        public HttpAsyncRequestHandler<?> lookup(final HttpRequest request) {
            return resolver.lookup(request.getRequestLine().getUri());
        }

    }

}

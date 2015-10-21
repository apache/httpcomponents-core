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

import org.apache.http.ConnectionClosedException;
import org.apache.http.ExceptionLogger;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.ProtocolVersion;
import org.apache.http.annotation.Immutable;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpClientEventHandler;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;
import org.apache.http.util.Asserts;

/**
 * {@code HttpAsyncRequestExecutor} is a fully asynchronous HTTP client side
 * protocol handler based on the NIO (non-blocking) I/O model.
 * {@code HttpAsyncRequestExecutor} translates individual events fired through
 * the {@link NHttpClientEventHandler} interface into logically related HTTP
 * message exchanges.
 * <p> The caller is expected to pass an instance of
 * {@link HttpAsyncClientExchangeHandler} to be used for the next series
 * of HTTP message exchanges through the connection context using
 * {@link #HTTP_HANDLER} attribute. HTTP exchange sequence is considered
 * complete when the {@link HttpAsyncClientExchangeHandler#isDone()} method
 * returns {@code true}. The {@link HttpAsyncRequester} utility class can
 * be used to facilitate initiation of asynchronous HTTP request execution.
 * <p>
 * Individual {@code HttpAsyncClientExchangeHandler} are expected to make use of
 * a {@link org.apache.http.protocol.HttpProcessor} to generate mandatory protocol
 * headers for all outgoing messages and apply common, cross-cutting message
 * transformations to all incoming and outgoing messages.
 * {@code HttpAsyncClientExchangeHandler}s can delegate implementation of
 * application specific content generation and processing to
 * a {@link HttpAsyncRequestProducer} and a {@link HttpAsyncResponseConsumer}.
 *
 * @see HttpAsyncClientExchangeHandler
 *
 * @since 4.2
 */
@Immutable
public class HttpAsyncRequestExecutor implements NHttpClientEventHandler {

    public static final int DEFAULT_WAIT_FOR_CONTINUE = 3000;
    public static final String HTTP_HANDLER = "http.nio.exchange-handler";

    private final int waitForContinue;
    private final ExceptionLogger exceptionLogger;

    /**
     * Creates new instance of {@code HttpAsyncRequestExecutor}.
     * @param waitForContinue wait for continue time period.
     * @param exceptionLogger Exception logger. If {@code null}
     *   {@link ExceptionLogger#NO_OP} will be used. Please note that the exception
     *   logger will be only used to log I/O exception thrown while closing
     *   {@link java.io.Closeable} objects (such as {@link org.apache.http.HttpConnection}).
     *
     * @since 4.4
     */
    public HttpAsyncRequestExecutor(
            final int waitForContinue,
            final ExceptionLogger exceptionLogger) {
        super();
        this.waitForContinue = Args.positive(waitForContinue, "Wait for continue time");
        this.exceptionLogger = exceptionLogger != null ? exceptionLogger : ExceptionLogger.NO_OP;
    }

    /**
     * Creates new instance of HttpAsyncRequestExecutor.
     *
     * @since 4.3
     */
    public HttpAsyncRequestExecutor(final int waitForContinue) {
        this(waitForContinue, null);
    }

    public HttpAsyncRequestExecutor() {
        this(DEFAULT_WAIT_FOR_CONTINUE, null);
    }

    @Override
    public void connected(
            final NHttpClientConnection conn,
            final Object attachment) throws IOException, HttpException {
        final State state = new State();
        final HttpContext context = conn.getContext();
        context.setAttribute(HTTP_EXCHANGE_STATE, state);
        requestReady(conn);
    }

    @Override
    public void closed(final NHttpClientConnection conn) {
        final State state = getState(conn);
        final HttpAsyncClientExchangeHandler handler = getHandler(conn);
        if (state != null) {
            if (state.getRequestState() != MessageState.READY || state.getResponseState() != MessageState.READY) {
                if (handler != null) {
                    handler.failed(new ConnectionClosedException("Connection closed unexpectedly"));
                }
            }
        }
        if (state == null || (handler != null && handler.isDone())) {
            closeHandler(handler);
        }
    }

    @Override
    public void exception(
            final NHttpClientConnection conn, final Exception cause) {
        shutdownConnection(conn);
        final HttpAsyncClientExchangeHandler handler = getHandler(conn);
        if (handler != null) {
            handler.failed(cause);
        } else {
            log(cause);
        }
    }

    @Override
    public void requestReady(
            final NHttpClientConnection conn) throws IOException, HttpException {
        final State state = getState(conn);
        Asserts.notNull(state, "Connection state");
        Asserts.check(state.getRequestState() == MessageState.READY ||
                        state.getRequestState() == MessageState.COMPLETED,
                "Unexpected request state %s", state.getRequestState());

        if (state.getRequestState() == MessageState.COMPLETED) {
            conn.suspendOutput();
            return;
        }
        final HttpContext context = conn.getContext();
        final HttpAsyncClientExchangeHandler handler;
        synchronized (context) {
            handler = getHandler(conn);
            if (handler == null || handler.isDone()) {
                conn.suspendOutput();
                return;
            }
        }
        final boolean pipelined = handler.getClass().getAnnotation(Pipelined.class) != null;

        final HttpRequest request = handler.generateRequest();
        if (request == null) {
            conn.suspendOutput();
            return;
        }
        final ProtocolVersion version = request.getRequestLine().getProtocolVersion();
        if (pipelined && version.lessEquals(HttpVersion.HTTP_1_0)) {
            throw new ProtocolException(version + " cannot be used with request pipelining");
        }
        state.setRequest(request);
        if (pipelined) {
            state.getRequestQueue().add(request);
        }
        if (request instanceof HttpEntityEnclosingRequest) {
            final boolean expectContinue = ((HttpEntityEnclosingRequest) request).expectContinue();
            if (expectContinue && pipelined) {
                throw new ProtocolException("Expect-continue handshake cannot be used with request pipelining");
            }
            conn.submitRequest(request);
            if (expectContinue) {
                final int timeout = conn.getSocketTimeout();
                state.setTimeout(timeout);
                conn.setSocketTimeout(this.waitForContinue);
                state.setRequestState(MessageState.ACK_EXPECTED);
            } else {
                final HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
                if (entity != null) {
                    state.setRequestState(MessageState.BODY_STREAM);
                } else {
                    handler.requestCompleted();
                    state.setRequestState(pipelined ? MessageState.READY : MessageState.COMPLETED);
                }
            }
        } else {
            conn.submitRequest(request);
            handler.requestCompleted();
            state.setRequestState(pipelined ? MessageState.READY : MessageState.COMPLETED);
        }
    }

    @Override
    public void outputReady(
            final NHttpClientConnection conn,
            final ContentEncoder encoder) throws IOException, HttpException {
        final State state = getState(conn);
        Asserts.notNull(state, "Connection state");
        Asserts.check(state.getRequestState() == MessageState.BODY_STREAM ||
                        state.getRequestState() == MessageState.ACK_EXPECTED,
                "Unexpected request state %s", state.getRequestState());

        final HttpAsyncClientExchangeHandler handler = getHandler(conn);
        Asserts.notNull(handler, "Client exchange handler");
        if (state.getRequestState() == MessageState.ACK_EXPECTED) {
            conn.suspendOutput();
            return;
        }
        handler.produceContent(encoder, conn);
        if (encoder.isCompleted()) {
            handler.requestCompleted();
            final boolean pipelined = handler.getClass().getAnnotation(Pipelined.class) != null;
            state.setRequestState(pipelined ? MessageState.READY : MessageState.COMPLETED);
        }
    }

    @Override
    public void responseReceived(
            final NHttpClientConnection conn) throws HttpException, IOException {
        final State state = getState(conn);
        Asserts.notNull(state, "Connection state");
        Asserts.check(state.getResponseState() == MessageState.READY,
                "Unexpected request state %s", state.getResponseState());

        final HttpAsyncClientExchangeHandler handler = getHandler(conn);
        Asserts.notNull(handler, "Client exchange handler");

        final boolean pipelined = handler.getClass().getAnnotation(Pipelined.class) != null;
        final HttpRequest request;
        if (pipelined) {
            request = state.getRequestQueue().poll();
            Asserts.notNull(request, "HTTP request");
        } else {
            request = state.getRequest();
            if (request == null) {
                throw new HttpException("Out of sequence response");
            }
        }

        final HttpResponse response = conn.getHttpResponse();

        final int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode < HttpStatus.SC_OK) {
            // 1xx intermediate response
            if (statusCode != HttpStatus.SC_CONTINUE) {
                throw new ProtocolException(
                        "Unexpected response: " + response.getStatusLine());
            }
            if (state.getRequestState() == MessageState.ACK_EXPECTED) {
                final int timeout = state.getTimeout();
                conn.setSocketTimeout(timeout);
                conn.requestOutput();
                state.setRequestState(MessageState.BODY_STREAM);
            }
            return;
        }
        state.setResponse(response);
        if (state.getRequestState() == MessageState.ACK_EXPECTED) {
            final int timeout = state.getTimeout();
            conn.setSocketTimeout(timeout);
            conn.resetOutput();
            state.setRequestState(MessageState.COMPLETED);
        } else if (state.getRequestState() == MessageState.BODY_STREAM) {
            // Early response
            conn.resetOutput();
            conn.suspendOutput();
            state.setRequestState(MessageState.COMPLETED);
            state.invalidate();
        }

        handler.responseReceived(response);

        state.setResponseState(MessageState.BODY_STREAM);
        if (!canResponseHaveBody(request, response)) {
            response.setEntity(null);
            conn.resetInput();
            processResponse(conn, state, handler);
        }
    }

    @Override
    public void inputReady(
            final NHttpClientConnection conn,
            final ContentDecoder decoder) throws IOException, HttpException {
        final State state = getState(conn);
        Asserts.notNull(state, "Connection state");
        Asserts.check(state.getResponseState() == MessageState.BODY_STREAM,
                "Unexpected request state %s", state.getResponseState());

        final HttpAsyncClientExchangeHandler handler = getHandler(conn);
        Asserts.notNull(handler, "Client exchange handler");
        handler.consumeContent(decoder, conn);
        if (decoder.isCompleted()) {
            processResponse(conn, state, handler);
        }
    }

    @Override
    public void endOfInput(final NHttpClientConnection conn) throws IOException {
        final State state = getState(conn);
        if (state != null) {
            if (state.getRequestState().compareTo(MessageState.READY) != 0) {
                state.invalidate();
            }
            final HttpAsyncClientExchangeHandler handler = getHandler(conn);
            if (handler != null) {
                if (state.isValid()) {
                    handler.inputTerminated();
                } else {
                    handler.failed(new ConnectionClosedException("Connection closed"));
                }
            }
        }
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
    public void timeout(
            final NHttpClientConnection conn) throws IOException {
        final State state = getState(conn);
        if (state != null) {
            if (state.getRequestState() == MessageState.ACK_EXPECTED) {
                final int timeout = state.getTimeout();
                conn.setSocketTimeout(timeout);
                conn.requestOutput();
                state.setRequestState(MessageState.BODY_STREAM);
                state.setTimeout(0);
                return;
            } else {
                state.invalidate();
                final HttpAsyncClientExchangeHandler handler = getHandler(conn);
                if (handler != null) {
                    handler.failed(new SocketTimeoutException());
                    handler.close();
                }
            }
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

    /**
     * This method can be used to log I/O exception thrown while closing
     * {@link java.io.Closeable} objects (such as
     * {@link org.apache.http.HttpConnection}}).
     *
     * @param ex I/O exception thrown by {@link java.io.Closeable#close()}
     */
    protected void log(final Exception ex) {
        this.exceptionLogger.log(ex);
    }

    private State getState(final NHttpConnection conn) {
        return (State) conn.getContext().getAttribute(HTTP_EXCHANGE_STATE);
    }

    private HttpAsyncClientExchangeHandler getHandler(final NHttpConnection conn) {
        return (HttpAsyncClientExchangeHandler) conn.getContext().getAttribute(HTTP_HANDLER);
    }

    private void shutdownConnection(final NHttpConnection conn) {
        try {
            conn.shutdown();
        } catch (final IOException ex) {
            log(ex);
        }
    }

    private void closeHandler(final HttpAsyncClientExchangeHandler handler) {
        if (handler != null) {
            try {
                handler.close();
            } catch (final IOException ioex) {
                log(ioex);
            }
        }
    }

    private void processResponse(
            final NHttpClientConnection conn,
            final State state,
            final HttpAsyncClientExchangeHandler handler) throws IOException, HttpException {
        if (!state.isValid()) {
            conn.close();
        }
        handler.responseCompleted();

        final boolean pipelined = handler.getClass().getAnnotation(Pipelined.class) != null;
        if (!pipelined) {
            state.setRequestState(MessageState.READY);
            state.setRequest(null);
        }
        state.setResponseState(MessageState.READY);
        state.setResponse(null);
        if (!handler.isDone() && conn.isOpen()) {
            conn.requestOutput();
        }
    }

    private boolean canResponseHaveBody(final HttpRequest request, final HttpResponse response) {

        final String method = request.getRequestLine().getMethod();
        final int status = response.getStatusLine().getStatusCode();

        if (method.equalsIgnoreCase("HEAD")) {
            return false;
        }
        if (method.equalsIgnoreCase("CONNECT") && status < 300) {
            return false;
        }
        return status >= HttpStatus.SC_OK
            && status != HttpStatus.SC_NO_CONTENT
            && status != HttpStatus.SC_NOT_MODIFIED
            && status != HttpStatus.SC_RESET_CONTENT;
    }

    static final String HTTP_EXCHANGE_STATE = "http.nio.http-exchange-state";

    static class State {

        private final Queue<HttpRequest> requestQueue;
        private volatile MessageState requestState;
        private volatile MessageState responseState;
        private volatile HttpRequest request;
        private volatile HttpResponse response;
        private volatile boolean valid;
        private volatile int timeout;

        State() {
            super();
            this.requestQueue = new ConcurrentLinkedQueue<HttpRequest>();
            this.valid = true;
            this.requestState = MessageState.READY;
            this.responseState = MessageState.READY;
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

        public Queue<HttpRequest> getRequestQueue() {
            return this.requestQueue;
        }

        public int getTimeout() {
            return this.timeout;
        }

        public void setTimeout(final int timeout) {
            this.timeout = timeout;
        }

        public boolean isValid() {
            return this.valid;
        }

        public void invalidate() {
            this.valid = false;
        }

        @Override
        public String toString() {
            final StringBuilder buf = new StringBuilder();
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
            buf.append("; valid: ");
            buf.append(this.valid);
            buf.append(";");
            return buf.toString();
        }

    }

}

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

import org.apache.http.ConnectionClosedException;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpConnection;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolException;
import org.apache.http.annotation.Immutable;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpClientEventHandler;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;

/**
 * <tt>HttpAsyncRequestExecutor</tt> is a fully asynchronous HTTP client side
 * protocol handler based on the NIO (non-blocking) I/O model.
 * <tt>HttpAsyncRequestExecutor</tt> translates individual events fired through
 * the {@link NHttpClientEventHandler} interface into logically related HTTP
 * message exchanges.
 * <p/>
 * <tt>HttpAsyncRequestExecutor</tt> relies on {@link HttpProcessor}
 * to generate mandatory protocol headers for all outgoing messages and apply
 * common, cross-cutting message transformations to all incoming and outgoing
 * messages, whereas individual {@link HttpAsyncRequestExecutionHandler}s
 * are expected to implement application specific content generation and
 * processing. The caller is expected to pass an instance of
 * {@link HttpAsyncRequestExecutionHandler} to be used for the next series
 * of HTTP message exchanges through the connection context using
 * {@link #HTTP_HANDLER} attribute. HTTP exchange sequence is considered
 * complete when the {@link HttpAsyncRequestExecutionHandler#isDone()} method
 * returns <code>true</code>. The {@link HttpAsyncRequester} utility class can
 * be used to facilitate initiation of asynchronous HTTP request execution.
 * <p/>
 * The following parameters can be used to customize the behavior of this
 * class:
 * <ul>
 *  <li>{@link org.apache.http.params.CoreProtocolPNames#WAIT_FOR_CONTINUE}</li>
 *  <li>{@link org.apache.http.params.CoreConnectionPNames#SO_TIMEOUT}</li>
 * </ul>
 *
 * @see HttpAsyncRequestExecutionHandler
 *
 * @since 4.2
 */
@Immutable
public class HttpAsyncRequestExecutor implements NHttpClientEventHandler {

    public static final String HTTP_HANDLER = "http.nio.exchange-handler";

    public HttpAsyncRequestExecutor() {
        super();
    }

    public void connected(
            final NHttpClientConnection conn,
            final Object attachment) throws IOException, HttpException {
        State state = new State();
        HttpContext context = conn.getContext();
        context.setAttribute(HTTP_EXCHANGE_STATE, state);
        requestReady(conn);
    }

    public void closed(final NHttpClientConnection conn) {
        State state = getState(conn);
        HttpAsyncRequestExecutionHandler<?> handler = getHandler(conn);
        if (state == null || !state.isValid()) {
            closeHandler(handler, null);
        }
        if (state != null) {
            state.reset();
        }
    }

    public void exception(
            final NHttpClientConnection conn, final Exception cause) {
        shutdownConnection(conn);
        HttpAsyncRequestExecutionHandler<?> handler = getHandler(conn);
        if (handler != null) {
            closeHandler(handler, cause);
        } else {
            log(cause);
        }
    }

    public void requestReady(
            final NHttpClientConnection conn) throws IOException, HttpException {
        State state = ensureNotNull(getState(conn));
        if (state.getRequestState() != MessageState.READY) {
            return;
        }
        HttpAsyncRequestExecutionHandler<?> handler = getHandler(conn);
        if (handler != null && handler.isDone()) {
            closeHandler(handler, null);
            state.reset();
            handler = null;
        }
        if (handler == null) {
            return;
        }

        HttpContext context = handler.getContext();
        context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);

        HttpRequest request = handler.generateRequest();
        context.setAttribute(ExecutionContext.HTTP_REQUEST, request);

        conn.setSocketTimeout(HttpConnectionParams.getSoTimeout(request.getParams()));

        HttpProcessor httppocessor = handler.getHttpProcessor();
        httppocessor.process(request, context);

        state.setRequest(request);

        conn.submitRequest(request);

        if (request instanceof HttpEntityEnclosingRequest) {
            if (((HttpEntityEnclosingRequest) request).expectContinue()) {
                int timeout = conn.getSocketTimeout();
                state.setTimeout(timeout);
                timeout = request.getParams().getIntParameter(
                        CoreProtocolPNames.WAIT_FOR_CONTINUE, 3000);
                conn.setSocketTimeout(timeout);
                state.setRequestState(MessageState.ACK_EXPECTED);
            } else {
                state.setRequestState(MessageState.BODY_STREAM);
            }
        } else {
            handler.requestCompleted(context);
            state.setRequestState(MessageState.COMPLETED);
        }
    }

    public void outputReady(
            final NHttpClientConnection conn,
            final ContentEncoder encoder) throws IOException {
        State state = ensureNotNull(getState(conn));
        HttpAsyncRequestExecutionHandler<?> handler = ensureNotNull(getHandler(conn));
        if (state.getRequestState() == MessageState.ACK_EXPECTED) {
            conn.suspendOutput();
            return;
        }
        HttpContext context = handler.getContext();
        handler.produceContent(encoder, conn);
        state.setRequestState(MessageState.BODY_STREAM);
        if (encoder.isCompleted()) {
            handler.requestCompleted(context);
            state.setRequestState(MessageState.COMPLETED);
        }
    }

    public void responseReceived(
            final NHttpClientConnection conn) throws HttpException, IOException {
        State state = ensureNotNull(getState(conn));
        HttpAsyncRequestExecutionHandler<?> handler = ensureNotNull(getHandler(conn));
        HttpResponse response = conn.getHttpResponse();
        HttpRequest request = state.getRequest();

        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode < HttpStatus.SC_OK) {
            // 1xx intermediate response
            if (statusCode != HttpStatus.SC_CONTINUE) {
                throw new ProtocolException(
                        "Unexpected response: " + response.getStatusLine());
            }
            if (state.getRequestState() == MessageState.ACK_EXPECTED) {
                int timeout = state.getTimeout();
                conn.setSocketTimeout(timeout);
                conn.requestOutput();
                state.setRequestState(MessageState.ACK);
            }
            return;
        }
        state.setResponse(response);
        if (state.getRequestState() == MessageState.ACK_EXPECTED) {
            int timeout = state.getTimeout();
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

        HttpContext context = handler.getContext();
        HttpProcessor httpprocessor = handler.getHttpProcessor();

        context.setAttribute(ExecutionContext.HTTP_RESPONSE, response);
        httpprocessor.process(response, context);

        state.setResponseState(MessageState.BODY_STREAM);
        if (!canResponseHaveBody(request, response)) {
            response.setEntity(null);
            conn.resetInput();
            processResponse(conn, state, handler);
        }
    }

    public void inputReady(
            final NHttpClientConnection conn,
            final ContentDecoder decoder) throws IOException {
        State state = ensureNotNull(getState(conn));
        HttpAsyncRequestExecutionHandler<?> handler = ensureNotNull(getHandler(conn));
        handler.consumeContent(decoder, conn);
        state.setResponseState(MessageState.BODY_STREAM);
        if (decoder.isCompleted()) {
            processResponse(conn, state, handler);
        }
    }

    public void endOfInput(final NHttpClientConnection conn) throws IOException {
        State state = getState(conn);
        if (state != null) {
            if (state.getRequestState().compareTo(MessageState.READY) != 0) {
                state.invalidate();
                closeHandler(getHandler(conn), new ConnectionClosedException("Connection closed"));
            }
        }
        conn.close();
    }

    public void timeout(
            final NHttpClientConnection conn) throws IOException {
        State state = getState(conn);
        if (state != null) {
            if (state.getRequestState() == MessageState.ACK_EXPECTED) {
                int timeout = state.getTimeout();
                conn.setSocketTimeout(timeout);
                conn.requestOutput();
                state.setRequestState(MessageState.BODY_STREAM);
                return;
            } else {
                state.invalidate();
                closeHandler(getHandler(conn), new SocketTimeoutException());
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
     * This method can be used to log I/O exception thrown while closing {@link Closeable}
     * objects (such as {@link HttpConnection}}).
     *
     * @param ex I/O exception thrown by {@link Closeable#close()}
     */
    protected void log(final Exception ex) {
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

    private HttpAsyncRequestExecutionHandler<?> getHandler(final NHttpConnection conn) {
        return (HttpAsyncRequestExecutionHandler<?>) conn.getContext().getAttribute(HTTP_HANDLER);
    }

    private HttpAsyncRequestExecutionHandler<?> ensureNotNull(final HttpAsyncRequestExecutionHandler<?> handler) {
        if (handler == null) {
            throw new IllegalStateException("HTTP exchange handler is null");
        }
        return handler;
    }

    private void shutdownConnection(final NHttpConnection conn) {
        try {
            conn.shutdown();
        } catch (IOException ex) {
            log(ex);
        }
    }

    private void closeHandler(final HttpAsyncRequestExecutionHandler<?> handler, final Exception ex) {
        if (handler != null) {
            try {
                if (ex != null) {
                    handler.failed(ex);
                }
            } finally {
                try {
                    handler.close();
                } catch (IOException ioex) {
                    log(ioex);
                }
            }
        }
    }

    private void processResponse(
            final NHttpClientConnection conn,
            final State state,
            final HttpAsyncRequestExecutionHandler<?> handler) throws IOException {
        HttpContext context = handler.getContext();
        if (state.isValid()) {
            HttpRequest request = state.getRequest();
            HttpResponse response = state.getResponse();
            String method = request.getRequestLine().getMethod();
            int status = response.getStatusLine().getStatusCode();
            if (!(method.equalsIgnoreCase("CONNECT") && status < 300)) {
                ConnectionReuseStrategy connReuseStrategy = handler.getConnectionReuseStrategy();
                if (!connReuseStrategy.keepAlive(response, context)) {
                    conn.close();
                }
            }
        } else {
            conn.close();
        }
        handler.responseCompleted(context);
        state.reset();
    }

    private boolean canResponseHaveBody(final HttpRequest request, final HttpResponse response) {

        String method = request.getRequestLine().getMethod();
        int status = response.getStatusLine().getStatusCode();

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

        private volatile MessageState requestState;
        private volatile MessageState responseState;
        private volatile HttpRequest request;
        private volatile HttpResponse response;
        private volatile boolean valid;
        private volatile int timeout;

        State() {
            super();
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

        public int getTimeout() {
            return this.timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        public void reset() {
            this.responseState = MessageState.READY;
            this.requestState = MessageState.READY;
            this.response = null;
            this.request = null;
            this.timeout = 0;
        }

        public boolean isValid() {
            return this.valid;
        }

        public void invalidate() {
            this.valid = false;
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
            buf.append("; valid: ");
            buf.append(this.valid);
            buf.append(";");
            return buf.toString();
        }

    }

}

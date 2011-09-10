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
import java.net.ProtocolException;
import java.net.SocketTimeoutException;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.annotation.Immutable;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.protocol.HttpContext;

/**
 * @since 4.2
 */
@Immutable
public class HttpAsyncClientProtocolHandler implements NHttpClientHandler {

    public static final String HTTP_HANDLER = "http.nio.exchange-handler";
    private static final String HTTP_EXCHNAGE = "http.nio.exchange";

    public HttpAsyncClientProtocolHandler() {
        super();
    }

    public void connected(final NHttpClientConnection conn, final Object attachment) {
        HttpExchange httpexchange = new HttpExchange();
        HttpContext context = conn.getContext();
        context.setAttribute(HTTP_EXCHNAGE, httpexchange);
        requestReady(conn);
    }

    public void closed(final NHttpClientConnection conn) {
        HttpExchange httpexchange = getHttpExchange(conn);
        HttpAsyncClientExchangeHandler<?> handler = httpexchange.getHandler();
        if (handler != null) {
            handler.cancel();
        }
    }

    public void exception(final NHttpClientConnection conn, final HttpException ex) {
        HttpExchange httpexchange = getHttpExchange(conn);
        HttpAsyncClientExchangeHandler<?> handler = httpexchange.getHandler();
        if (handler != null) {
            handler.failed(ex);
        }
        closeConnection(conn);
    }

    public void exception(final NHttpClientConnection conn, final IOException ex) {
        HttpExchange httpexchange = getHttpExchange(conn);
        HttpAsyncClientExchangeHandler<?> handler = httpexchange.getHandler();
        if (handler != null) {
            handler.failed(ex);
        }
        shutdownConnection(conn);
    }

    public void requestReady(final NHttpClientConnection conn) {
        HttpExchange httpexchange = getHttpExchange(conn);
        if (httpexchange.getRequestState() != MessageState.READY) {
            return;
        }
        HttpAsyncClientExchangeHandler<?> handler = httpexchange.getHandler();
        if (handler != null && handler.isDone()) {
            httpexchange.clearHandler();
            handler = null;
        }
        if (handler == null) {
            handler = (HttpAsyncClientExchangeHandler<?>) conn.getContext().removeAttribute(
                    HTTP_HANDLER);
            httpexchange.setHandler(handler);
        }
        if (handler == null) {
            return;
        }
        try {
            HttpContext context = handler.getContext();
            HttpRequest request = handler.generateRequest();
            httpexchange.setRequest(request);

            HttpEntityEnclosingRequest entityReq = null;
            if (request instanceof HttpEntityEnclosingRequest) {
                entityReq = (HttpEntityEnclosingRequest) request;
            }

            conn.submitRequest(request);

            if (entityReq != null) {
                if (entityReq.expectContinue()) {
                    int timeout = conn.getSocketTimeout();
                    httpexchange.setTimeout(timeout);
                    timeout = request.getParams().getIntParameter(
                            CoreProtocolPNames.WAIT_FOR_CONTINUE, 3000);
                    conn.setSocketTimeout(timeout);
                    httpexchange.setRequestState(MessageState.ACK);
                } else {
                    httpexchange.setRequestState(MessageState.BODY_STREAM);
                }
            } else {
                handler.requestCompleted(context);
                httpexchange.setRequestState(MessageState.COMPLETED);
            }
        } catch (RuntimeException ex) {
            shutdownConnection(conn);
            handler.failed(ex);
            throw ex;
        } catch (Exception ex) {
            shutdownConnection(conn);
            handler.failed(ex);
            onException(ex);
        }
    }

    public void inputReady(final NHttpClientConnection conn, final ContentDecoder decoder) {
        HttpExchange httpexchange = getHttpExchange(conn);
        HttpAsyncClientExchangeHandler<?> handler = httpexchange.getHandler();
        try {
            handler.consumeContent(decoder, conn);
            if (decoder.isCompleted()) {
                processResponse(conn, httpexchange, handler);
            }
        } catch (RuntimeException ex) {
            shutdownConnection(conn);
            handler.failed(ex);
            throw ex;
        } catch (Exception ex) {
            shutdownConnection(conn);
            handler.failed(ex);
            onException(ex);
        }
    }

    public void outputReady(final NHttpClientConnection conn, final ContentEncoder encoder) {
        HttpExchange httpexchange = getHttpExchange(conn);
        HttpAsyncClientExchangeHandler<?> handler = httpexchange.getHandler();
        try {
            if (httpexchange.getRequestState() == MessageState.ACK) {
                conn.suspendOutput();
                return;
            }
            HttpContext context = handler.getContext();
            handler.produceContent(encoder, conn);
            if (encoder.isCompleted()) {
                handler.requestCompleted(context);
                httpexchange.setRequestState(MessageState.COMPLETED);
            }
        } catch (RuntimeException ex) {
            shutdownConnection(conn);
            handler.failed(ex);
            throw ex;
        } catch (Exception ex) {
            shutdownConnection(conn);
            handler.failed(ex);
            onException(ex);
        }
    }

    public void responseReceived(final NHttpClientConnection conn) {
        HttpExchange httpexchange = getHttpExchange(conn);
        HttpAsyncClientExchangeHandler<?> handler = httpexchange.getHandler();
        try {
            HttpResponse response = conn.getHttpResponse();
            HttpRequest request = httpexchange.getRequest();

            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode < HttpStatus.SC_OK) {
                // 1xx intermediate response
                if (statusCode != HttpStatus.SC_CONTINUE) {
                    throw new ProtocolException(
                            "Unexpected response: " + response.getStatusLine());
                }
                if (httpexchange.getRequestState() == MessageState.ACK) {
                    int timeout = httpexchange.getTimeout();
                    conn.setSocketTimeout(timeout);
                    conn.requestOutput();
                    httpexchange.setRequestState(MessageState.BODY_STREAM);
                }
                return;
            } else {
                httpexchange.setResponse(response);
                if (httpexchange.getRequestState() == MessageState.ACK) {
                    int timeout = httpexchange.getTimeout();
                    conn.setSocketTimeout(timeout);
                    conn.resetOutput();
                    httpexchange.setRequestState(MessageState.COMPLETED);
                } else if (httpexchange.getRequestState() == MessageState.BODY_STREAM) {
                    // Early response
                    int timeout = httpexchange.getTimeout();
                    conn.setSocketTimeout(timeout);
                    conn.suspendOutput();
                    conn.resetOutput();
                    httpexchange.invalidate();
                }
            }
            handler.responseReceived(response);
            if (!canResponseHaveBody(request, response)) {
                conn.resetInput();
                processResponse(conn, httpexchange, handler);
            }
        } catch (RuntimeException ex) {
            shutdownConnection(conn);
            handler.failed(ex);
            throw ex;
        } catch (Exception ex) {
            shutdownConnection(conn);
            handler.failed(ex);
            onException(ex);
        }
    }

    public void timeout(final NHttpClientConnection conn) {
        HttpExchange httpexchange = getHttpExchange(conn);
        HttpAsyncClientExchangeHandler<?> handler = httpexchange.getHandler();
        if (handler == null) {
            shutdownConnection(conn);
            return;
        }
        try {
            if (httpexchange.getRequestState() == MessageState.ACK) {
                int timeout = httpexchange.getTimeout();
                conn.setSocketTimeout(timeout);
                conn.requestOutput();
                httpexchange.setRequestState(MessageState.BODY_STREAM);
            } else {
                handler.failed(new SocketTimeoutException());
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
        } catch (RuntimeException ex) {
            shutdownConnection(conn);
            handler.failed(ex);
            throw ex;
        } catch (Exception ex) {
            shutdownConnection(conn);
            handler.failed(ex);
            onException(ex);
        }
    }

    protected void onException(final Exception ex) {
    }

    private void closeConnection(final NHttpConnection conn) {
        try {
            conn.close();
        } catch (IOException ex) {
            try {
                conn.shutdown();
            } catch (IOException ex2) {
                onException(ex2);
            }
        }
    }

    private void shutdownConnection(final NHttpConnection conn) {
        try {
            conn.shutdown();
        } catch (IOException ex) {
            onException(ex);
        }
    }

    private HttpExchange getHttpExchange(final NHttpConnection conn) {
        return (HttpExchange) conn.getContext().getAttribute(HTTP_EXCHNAGE);
    }

    private void processResponse(
            final NHttpClientConnection conn,
            final HttpExchange httpexchange,
            final HttpAsyncClientExchangeHandler<?> handler) throws IOException {
        if (!httpexchange.isValid()) {
            conn.close();
        }
        HttpContext context = handler.getContext();
        HttpRequest request = httpexchange.getRequest();
        HttpResponse response = httpexchange.getResponse();

        String method = request.getRequestLine().getMethod();
        int status = response.getStatusLine().getStatusCode();
        if (method.equalsIgnoreCase("CONNECT") && status == HttpStatus.SC_OK) {
            conn.resetInput();
        } else {
            ConnectionReuseStrategy connReuseStrategy = handler.getConnectionReuseStrategy();
            if (!connReuseStrategy.keepAlive(response, context)) {
                conn.close();
            }
        }
        handler.responseCompleted(context);
        httpexchange.reset();
    }

    private boolean canResponseHaveBody(final HttpRequest request, final HttpResponse response) {

        String method = request.getRequestLine().getMethod();
        int status = response.getStatusLine().getStatusCode();

        if (method.equalsIgnoreCase("HEAD")) {
            return false;
        }
        if (method.equalsIgnoreCase("CONNECT") && status == HttpStatus.SC_OK) {
            return false;
        }
        return status >= HttpStatus.SC_OK
            && status != HttpStatus.SC_NO_CONTENT
            && status != HttpStatus.SC_NOT_MODIFIED
            && status != HttpStatus.SC_RESET_CONTENT;
    }

    class HttpExchange {

        private volatile HttpAsyncClientExchangeHandler<?> handler;
        private volatile MessageState requestState;
        private volatile MessageState responseState;
        private volatile HttpRequest request;
        private volatile HttpResponse response;
        private volatile boolean valid;
        private volatile int timeout;

        HttpExchange() {
            super();
            this.valid = true;
            this.requestState = MessageState.READY;
            this.responseState = MessageState.READY;
        }

        public HttpAsyncClientExchangeHandler<?> getHandler() {
            return this.handler;
        }

        public void setHandler(final HttpAsyncClientExchangeHandler<?> handler) {
            if (this.handler != null) {
                throw new IllegalStateException("Handler already set");
            }
            this.handler = handler;
        }

        public void clearHandler() {
            this.handler = null;
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
            if (this.request != null) {
                throw new IllegalStateException("Request already set");
            }
            this.request = request;
        }

        public HttpResponse getResponse() {
            return this.response;
        }

        public void setResponse(final HttpResponse response) {
            if (this.response != null) {
                throw new IllegalStateException("Response already set");
            }
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

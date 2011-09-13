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
import org.apache.http.HttpVersion;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.ProtocolException;
import org.apache.http.ProtocolVersion;
import org.apache.http.UnsupportedHttpVersionException;
import org.apache.http.annotation.Immutable;
import org.apache.http.concurrent.Cancellable;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.NHttpServiceHandler;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.params.DefaultedHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;

/**
 * @since 4.2
 */
@Immutable // provided injected dependencies are immutable
public class HttpAsyncServiceHandler implements NHttpServiceHandler {

    private static final String HTTP_EXCHANGE = "http.nio.http-exchange";

    private final HttpAsyncRequestHandlerResolver handlerResolver;
    private final HttpAsyncExpectationVerifier expectationVerifier;
    private final HttpProcessor httpProcessor;
    private final ConnectionReuseStrategy connStrategy;
    private final HttpParams params;

    public HttpAsyncServiceHandler(
            final HttpAsyncRequestHandlerResolver handlerResolver,
            final HttpAsyncExpectationVerifier expectationVerifier,
            final HttpProcessor httpProcessor,
            final ConnectionReuseStrategy connStrategy,
            final HttpParams params) {
        super();
        if (handlerResolver == null) {
            throw new IllegalArgumentException("Handler resolver may not be null.");
        }
        if (httpProcessor == null) {
            throw new IllegalArgumentException("HTTP processor may not be null.");
        }
        if (connStrategy == null) {
            throw new IllegalArgumentException("Connection reuse strategy may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.handlerResolver = handlerResolver;
        this.expectationVerifier = expectationVerifier;
        this.httpProcessor = httpProcessor;
        this.connStrategy = connStrategy;
        this.params = params;
    }

    public HttpAsyncServiceHandler(
            final HttpAsyncRequestHandlerResolver handlerResolver,
            final HttpProcessor httpProcessor,
            final ConnectionReuseStrategy connStrategy,
            final HttpParams params) {
        this(handlerResolver, null, httpProcessor, connStrategy, params);
    }

    public void connected(final NHttpServerConnection conn) {
        HttpExchange httpExchange = new HttpExchange();
        conn.getContext().setAttribute(HTTP_EXCHANGE, httpExchange);
    }

    public void closed(final NHttpServerConnection conn) {
        HttpExchange httpExchange = (HttpExchange) conn.getContext().getAttribute(HTTP_EXCHANGE);
        Cancellable asyncProcess = httpExchange.getAsyncProcess();
        httpExchange.clear();
        if (asyncProcess != null) {
            asyncProcess.cancel();
        }
    }

    public void requestReceived(final NHttpServerConnection conn) {
        HttpExchange httpExchange = (HttpExchange) conn.getContext().getAttribute(HTTP_EXCHANGE);
        try {
            HttpRequest request = conn.getHttpRequest();
            HttpContext context = httpExchange.getContext();
            request.setParams(new DefaultedHttpParams(request.getParams(), this.params));

            context.setAttribute(ExecutionContext.HTTP_REQUEST, request);
            context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
            this.httpProcessor.process(request, context);

            HttpAsyncRequestHandler<Object> requestHandler = getRequestHandler(request);
            HttpAsyncRequestConsumer<Object> consumer = requestHandler.processRequest(request, context);

            httpExchange.setRequestHandler(requestHandler);
            httpExchange.setRequestConsumer(consumer);
            httpExchange.setRequest(request);

            consumer.requestReceived(request);

            if (request instanceof HttpEntityEnclosingRequest) {
                HttpEntityEnclosingRequest entityRequest = (HttpEntityEnclosingRequest) request;
                if (entityRequest.expectContinue()) {
                    httpExchange.setRequestState(MessageState.ACK_EXPECTED);
                    if (this.expectationVerifier != null) {
                        conn.suspendInput();
                        HttpAsyncContinueTrigger trigger = new ContinueTriggerImpl(httpExchange, conn);
                        Cancellable asyncProcess = this.expectationVerifier.verify(request, trigger, context);
                        httpExchange.setAsyncProcess(asyncProcess);
                    } else {
                        HttpResponse response = create100Continue(request);
                        conn.submitResponse(response);
                        httpExchange.setRequestState(MessageState.BODY_STREAM);
                    }
                } else {
                    httpExchange.setRequestState(MessageState.BODY_STREAM);
                }
            } else {
                // No request content is expected.
                // Process request right away
                conn.suspendInput();
                processRequest(conn, httpExchange);
            }
        } catch (RuntimeException ex) {
            shutdownConnection(conn);
            throw ex;
        } catch (Exception ex) {
            shutdownConnection(conn);
            onException(ex);
        }
    }

    public void exception(final NHttpServerConnection conn, final HttpException httpex) {
        if (conn.isResponseSubmitted()) {
            // There is not much that we can do if a response head
            // has already been submitted
            closeConnection(conn);
            onException(httpex);
            return;
        }

        HttpExchange httpExchange = (HttpExchange) conn.getContext().getAttribute(HTTP_EXCHANGE);
        try {
            HttpAsyncResponseProducer responseProducer = handleException(httpex);
            httpExchange.setResponseProducer(responseProducer);
            commitResponse(conn, httpExchange);
        } catch (RuntimeException ex) {
            shutdownConnection(conn);
            throw ex;
        } catch (Exception ex) {
            shutdownConnection(conn);
            onException(ex);
        }
    }

    public void exception(final NHttpServerConnection conn, final IOException ex) {
        shutdownConnection(conn);
        onException(ex);
    }

    public void timeout(final NHttpServerConnection conn) {
        try {
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
        } catch (IOException ex) {
            onException(ex);
        }
    }

    public void inputReady(final NHttpServerConnection conn, final ContentDecoder decoder) {
        HttpExchange httpExchange = (HttpExchange) conn.getContext().getAttribute(HTTP_EXCHANGE);
        try {
            HttpAsyncRequestConsumer<?> consumer = httpExchange.getRequestConsumer();
            consumer.consumeContent(decoder, conn);
            httpExchange.setRequestState(MessageState.BODY_STREAM);
            if (decoder.isCompleted()) {
                conn.suspendInput();
                processRequest(conn, httpExchange);
            }
        } catch (RuntimeException ex) {
            shutdownConnection(conn);
            throw ex;
        } catch (Exception ex) {
            shutdownConnection(conn);
            onException(ex);
        }
    }

    public void responseReady(final NHttpServerConnection conn) {
        HttpExchange httpExchange = (HttpExchange) conn.getContext().getAttribute(HTTP_EXCHANGE);
        try {
            if (httpExchange.getRequestState() == MessageState.ACK) {
                conn.requestInput();
                httpExchange.setRequestState(MessageState.COMPLETED);
                HttpRequest request = httpExchange.getRequest();
                HttpResponse response = create100Continue(request);
                conn.submitResponse(response);
            } else if (httpExchange.getResponse() == null && httpExchange.getResponseProducer() != null) {
                if (httpExchange.getRequestState() == MessageState.ACK_EXPECTED) {
                    conn.resetInput();
                    httpExchange.setRequestState(MessageState.COMPLETED);
                }
                conn.resetInput();
                commitResponse(conn, httpExchange);
            }
        } catch (RuntimeException ex) {
            shutdownConnection(conn);
            throw ex;
        } catch (Exception ex) {
            shutdownConnection(conn);
            onException(ex);
        }
    }

    public void outputReady(final NHttpServerConnection conn, final ContentEncoder encoder) {
        HttpExchange httpExchange = (HttpExchange) conn.getContext().getAttribute(HTTP_EXCHANGE);
        try {
            HttpAsyncResponseProducer responseProducer = httpExchange.getResponseProducer();
            HttpContext context = httpExchange.getContext();
            HttpResponse response = httpExchange.getResponse();

            responseProducer.produceContent(encoder, conn);
            httpExchange.setResponseState(MessageState.BODY_STREAM);
            if (encoder.isCompleted()) {
                responseProducer.responseCompleted(context);
                if (!this.connStrategy.keepAlive(response, context)) {
                    conn.close();
                } else {
                    conn.requestInput();
                }
                httpExchange.clear();
            }
        } catch (RuntimeException ex) {
            shutdownConnection(conn);
            throw ex;
        } catch (Exception ex) {
            shutdownConnection(conn);
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

    protected HttpAsyncResponseProducer handleException(final Exception ex) {
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
        return new ErrorResponseProducer(
                HttpVersion.HTTP_1_0, code, NStringEntity.create(message), false);
    }

    private HttpResponse create100Continue(final HttpRequest request) {
        ProtocolVersion ver = request.getRequestLine().getProtocolVersion();
        if (!ver.lessEquals(HttpVersion.HTTP_1_1)) {
            // Downgrade protocol version if greater than HTTP/1.1
            ver = HttpVersion.HTTP_1_1;
        }
        return new BasicHttpResponse(ver, HttpStatus.SC_CONTINUE, "Continue");
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
            final HttpExchange httpExchange) throws HttpException, IOException {
        HttpAsyncRequestHandler<Object> handler = httpExchange.getRequestHandler();
        HttpContext context = httpExchange.getContext();
        HttpAsyncRequestConsumer<?> consumer = httpExchange.getRequestConsumer();
        consumer.requestCompleted(context);
        httpExchange.setRequestState(MessageState.COMPLETED);
        Exception exception = consumer.getException();
        if (exception != null) {
            HttpAsyncResponseProducer responseProducer = handleException(exception);
            httpExchange.setResponseProducer(responseProducer);
            conn.requestOutput();
        } else {
            Object result = consumer.getResult();
            HttpAsyncResponseTrigger trigger = new ResponseTriggerImpl(httpExchange, conn);
            try {
                Cancellable asyncProcess = handler.handle(result, trigger, context);
                httpExchange.setAsyncProcess(asyncProcess);
            } catch (HttpException ex) {
                HttpAsyncResponseProducer responseProducer = handleException(ex);
                httpExchange.setResponseProducer(responseProducer);
                conn.requestOutput();
            } catch (IOException ex) {
                HttpAsyncResponseProducer responseProducer = handleException(ex);
                httpExchange.setResponseProducer(responseProducer);
                conn.requestOutput();
            }
        }
    }

    private void commitResponse(
            final NHttpServerConnection conn,
            final HttpExchange httpExchange) throws IOException, HttpException {
        HttpContext context = httpExchange.getContext();
        HttpRequest request = httpExchange.getRequest();
        HttpAsyncResponseProducer responseProducer = httpExchange.getResponseProducer();
        HttpResponse response = responseProducer.generateResponse();
        response.setParams(new DefaultedHttpParams(response.getParams(), this.params));

        httpExchange.setResponse(response);

        context.setAttribute(ExecutionContext.HTTP_RESPONSE, response);
        this.httpProcessor.process(response, context);

        HttpEntity entity = response.getEntity();
        if (entity != null && !canResponseHaveBody(request, response)) {
            response.setEntity(null);
            entity = null;
        }

        conn.submitResponse(response);

        if (entity == null) {
            responseProducer.responseCompleted(context);
            if (!this.connStrategy.keepAlive(response, context)) {
                conn.close();
            } else {
                // Ready to process new request
                conn.requestInput();
            }
            httpExchange.clear();
        } else {
            httpExchange.setRequestState(MessageState.BODY_STREAM);
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

    class HttpExchange {

        private final BasicHttpContext context;
        private volatile HttpAsyncRequestHandler<Object> requestHandler;
        private volatile MessageState requestState;
        private volatile MessageState responseState;
        private volatile HttpAsyncRequestConsumer<Object> requestConsumer;
        private volatile HttpAsyncResponseProducer responseProducer;
        private volatile HttpRequest request;
        private volatile HttpResponse response;
        private volatile Cancellable asyncProcess;

        HttpExchange() {
            super();
            this.context = new BasicHttpContext();
        }

        public HttpContext getContext() {
            return this.context;
        }

        public HttpAsyncRequestHandler<Object> getRequestHandler() {
            return this.requestHandler;
        }

        public void setRequestHandler(final HttpAsyncRequestHandler<Object> requestHandler) {
            if (this.requestHandler != null) {
                throw new IllegalStateException("Request handler already set");
            }
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
            if (this.requestConsumer != null) {
                throw new IllegalStateException("Request consumer already set");
            }
            this.requestConsumer = requestConsumer;
        }

        public HttpAsyncResponseProducer getResponseProducer() {
            return this.responseProducer;
        }

        public void setResponseProducer(final HttpAsyncResponseProducer responseProducer) {
            if (this.responseProducer != null) {
                throw new IllegalStateException("Response producer already set");
            }
            this.responseProducer = responseProducer;
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

        public Cancellable getAsyncProcess() {
            return this.asyncProcess;
        }

        public void setAsyncProcess(final Cancellable asyncProcess) {
            this.asyncProcess = asyncProcess;
        }

        public void clear() {
            this.responseState = MessageState.READY;
            this.requestState = MessageState.READY;
            this.requestHandler = null;
            if (this.requestConsumer != null) {
                try {
                    this.requestConsumer.close();
                } catch (IOException ex) {
                    onException(ex);
                }
            }
            this.requestConsumer = null;
            if (this.responseProducer != null) {
                try {
                    this.responseProducer.close();
                } catch (IOException ex) {
                    onException(ex);
                }
            }
            this.responseProducer = null;
            this.request = null;
            this.response = null;
            this.asyncProcess = null;
            this.context.clear();
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

    class ResponseTriggerImpl implements HttpAsyncResponseTrigger {

        private final HttpExchange httpExchange;
        private final IOControl iocontrol;

        private volatile boolean triggered;

        public ResponseTriggerImpl(final HttpExchange httpExchange, final IOControl iocontrol) {
            super();
            this.httpExchange = httpExchange;
            this.iocontrol = iocontrol;
        }

        public synchronized void submitResponse(final HttpAsyncResponseProducer responseProducer) {
            if (responseProducer == null) {
                throw new IllegalArgumentException("Response producer may not be null");
            }
            if (this.triggered) {
                throw new IllegalStateException("Response already triggered");
            }
            this.triggered = true;
            this.httpExchange.setResponseProducer(responseProducer);
            this.iocontrol.requestOutput();
        }

        public boolean isTriggered() {
            return this.triggered;
        }

    }

    class ContinueTriggerImpl implements HttpAsyncContinueTrigger {

        private final HttpExchange httpExchange;
        private final IOControl iocontrol;

        private volatile boolean triggered;

        public ContinueTriggerImpl(final HttpExchange httpExchange, final IOControl iocontrol) {
            super();
            this.httpExchange = httpExchange;
            this.iocontrol = iocontrol;
        }

        public synchronized void continueRequest() {
            if (this.triggered) {
                throw new IllegalStateException("Response already triggered");
            }
            this.triggered = true;
            this.httpExchange.setRequestState(MessageState.ACK);
            this.iocontrol.requestOutput();
        }

        public synchronized void submitResponse(final HttpAsyncResponseProducer responseProducer) {
            if (responseProducer == null) {
                throw new IllegalArgumentException("Response producer may not be null");
            }
            if (this.triggered) {
                throw new IllegalStateException("Response already triggered");
            }
            this.triggered = true;
            this.httpExchange.setResponseProducer(responseProducer);
            this.iocontrol.requestOutput();
        }

        public boolean isTriggered() {
            return this.triggered;
        }

    }

}

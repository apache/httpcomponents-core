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
package org.apache.hc.core5.http.impl.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.MisdirectedRequestException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.UnsupportedHttpVersionException;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.ServerSupport;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncPushProducer;
import org.apache.hc.core5.http.nio.AsyncResponseProducer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.ResourceHolder;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.nio.support.BasicResponseProducer;
import org.apache.hc.core5.http.nio.support.ImmediateResponseExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.io.CloseMode;

class ServerHttp1StreamHandler implements ResourceHolder {

    private final Http1StreamChannel<HttpResponse> outputChannel;
    private final DataStreamChannel internalDataChannel;
    private final ResponseChannel responseChannel;
    private final HttpProcessor httpProcessor;
    private final Http1Config http1Config;
    private final ConnectionReuseStrategy connectionReuseStrategy;
    private final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory;
    private final Callback<Exception> exceptionCallback;
    private final HttpCoreContext context;
    private final AtomicReference<MessageState> requestState;
    private final AtomicReference<MessageState> responseState;
    private final AtomicBoolean responseCommitted;
    private final AtomicBoolean done;

    private volatile boolean keepAlive;
    private volatile AsyncServerExchangeHandler exchangeHandler;
    private volatile HttpRequest receivedRequest;

    ServerHttp1StreamHandler(
            final Http1StreamChannel<HttpResponse> outputChannel,
            final HttpProcessor httpProcessor,
            final Http1Config http1Config,
            final ConnectionReuseStrategy connectionReuseStrategy,
            final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory,
            final Callback<Exception> exceptionCallback,
            final HttpCoreContext context) {
        this.outputChannel = outputChannel;
        this.internalDataChannel = new DataStreamChannel() {

            @Override
            public void requestOutput() {
                outputChannel.requestOutput();
            }

            @Override
            public void endStream(final List<? extends Header> trailers) throws IOException {
                responseState.set( MessageState.COMPLETE);
                outputChannel.complete(trailers);
                if (requestState.get() == MessageState.COMPLETE && !keepAlive) {
                    outputChannel.close();
                }
            }

            @Override
            public int write(final ByteBuffer src) throws IOException {
                return outputChannel.write(src);
            }

            @Override
            public void endStream() throws IOException {
                endStream(null);
            }

        };

        this.responseChannel = new ResponseChannel() {

            @Override
            public void sendInformation(final HttpResponse response, final HttpContext httpContext) throws HttpException, IOException {
                commitInformation(response);
            }

            @Override
            public void sendResponse(
                    final HttpResponse response, final EntityDetails responseEntityDetails, final HttpContext httpContext) throws HttpException, IOException {
                commitResponse(response, responseEntityDetails);
            }

            @Override
            public void pushPromise(
                    final HttpRequest promise, final AsyncPushProducer pushProducer, final HttpContext httpContext) throws HttpException, IOException {
                commitPromise();
            }

            @Override
            public void terminateExchange() {
                terminate();
            }

            @Override
            public String toString() {
                return super.toString() + " " + ServerHttp1StreamHandler.this;
            }

        };

        this.httpProcessor = httpProcessor;
        this.http1Config = http1Config != null ? http1Config : Http1Config.DEFAULT;
        this.connectionReuseStrategy = connectionReuseStrategy;
        this.exchangeHandlerFactory = exchangeHandlerFactory;
        this.exceptionCallback = exceptionCallback;
        this.context = context;
        this.requestState = new AtomicReference<>(MessageState.HEADERS);
        this.responseState = new AtomicReference<>(MessageState.IDLE);
        this.responseCommitted = new AtomicBoolean();
        this.done = new AtomicBoolean();
        this.keepAlive = true;
    }

    private void commitResponse(
            final HttpResponse response,
            final EntityDetails responseEntityDetails) throws HttpException, IOException {
        if (responseCommitted.compareAndSet(false, true)) {

            final ProtocolVersion transportVersion = response.getVersion();
            if (transportVersion != null) {
                if (!transportVersion.lessEquals(http1Config.getVersion())) {
                    throw new UnsupportedHttpVersionException(transportVersion);
                }
                context.setProtocolVersion(transportVersion);
            }

            final int status = response.getCode();
            if (status < HttpStatus.SC_SUCCESS) {
                throw new HttpException("Invalid response: " + status);
            }

            context.setResponse(response);
            httpProcessor.process(response, responseEntityDetails, context);

            final boolean endStream = responseEntityDetails == null ||
                    receivedRequest != null && Method.HEAD.isSame(receivedRequest.getMethod());

            if (!connectionReuseStrategy.keepAlive(receivedRequest, response, context)) {
                keepAlive = false;
            }

            if (endStream) {
                responseState.set(MessageState.COMPLETE);
                outputChannel.submit(response, true, FlushMode.IMMEDIATE);
                if (!keepAlive) {
                    outputChannel.close();
                }
            } else {
                outputChannel.submit(response, false, FlushMode.BUFFER);
                exchangeHandler.produce(internalDataChannel);
                if (responseState.compareAndSet(MessageState.IDLE, MessageState.BODY)) {
                    outputChannel.requestOutput();
                }
            }
        } else {
            throw new HttpException("Response already committed");
        }
    }

    private void commitInformation(final HttpResponse response) throws IOException, HttpException {
        if (responseCommitted.get()) {
            throw new HttpException("Response already committed");
        }
        final int status = response.getCode();
        if (status < HttpStatus.SC_INFORMATIONAL || status >= HttpStatus.SC_SUCCESS) {
            throw new HttpException("Invalid intermediate response: " + status);
        }
        outputChannel.submit(response, true, FlushMode.IMMEDIATE);
    }

    private void commitPromise() throws HttpException {
        throw new HttpException("HTTP/1.1 does not support server push");
    }

    private void terminate() {
        outputChannel.close(CloseMode.IMMEDIATE);
    }

    void activateChannel() throws IOException, HttpException {
        outputChannel.activate();
    }

    boolean isResponseFinal() {
        return responseState.get() == MessageState.COMPLETE;
    }

    boolean keepAlive() {
        return keepAlive;
    }

    boolean isCompleted() {
        return requestState.get() == MessageState.COMPLETE && responseState.get() == MessageState.COMPLETE;
    }

    void terminateExchange(final HttpException ex) throws HttpException, IOException {
        if (done.get() || requestState.get() != MessageState.HEADERS) {
            throw new ProtocolException("Unexpected message head");
        }
        receivedRequest = null;
        requestState.set(MessageState.COMPLETE);
        final HttpResponse response = new BasicHttpResponse(ServerSupport.toStatusCode(ex));
        response.addHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
        final AsyncResponseProducer responseProducer = new BasicResponseProducer(response, ServerSupport.toErrorMessage(ex));
        exchangeHandler = new ImmediateResponseExchangeHandler(responseProducer);
        exchangeHandler.handleRequest(null, null, responseChannel, context);
    }

    void consumeHeader(final HttpRequest request, final EntityDetails requestEntityDetails) throws HttpException, IOException {
        if (done.get() || requestState.get() != MessageState.HEADERS) {
            throw new ProtocolException("Unexpected message head");
        }
        receivedRequest = request;
        requestState.set(requestEntityDetails == null ? MessageState.COMPLETE : MessageState.BODY);
        try {
            final ProtocolVersion transportVersion = request.getVersion();
            if (transportVersion != null && transportVersion.greaterEquals(HttpVersion.HTTP_2)) {
                throw new UnsupportedHttpVersionException(transportVersion);
            }
            context.setProtocolVersion(transportVersion != null ? transportVersion : http1Config.getVersion());
            context.setRequest(request);

            httpProcessor.process(request, requestEntityDetails, context);

            AsyncServerExchangeHandler handler;
            try {
                handler = exchangeHandlerFactory.create(request, context);
            } catch (final MisdirectedRequestException ex) {
                handler = new ImmediateResponseExchangeHandler(HttpStatus.SC_MISDIRECTED_REQUEST, ex.getMessage());
            } catch (final HttpException ex) {
                handler = new ImmediateResponseExchangeHandler(HttpStatus.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
            }
            if (handler == null) {
                handler = new ImmediateResponseExchangeHandler(HttpStatus.SC_NOT_FOUND, "Cannot handle request");
            }
            exchangeHandler = handler;

            exchangeHandler.handleRequest(request, requestEntityDetails, responseChannel, context);
        } catch (final HttpException ex) {
            if (!responseCommitted.get()) {
                final HttpResponse response = new BasicHttpResponse(ServerSupport.toStatusCode(ex));
                response.addHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
                final AsyncResponseProducer responseProducer = new BasicResponseProducer(response, ServerSupport.toErrorMessage(ex));
                exchangeHandler = new ImmediateResponseExchangeHandler(responseProducer);
                exchangeHandler.handleRequest(request, requestEntityDetails, responseChannel, context);
            } else {
                throw ex;
            }
        }

    }

    boolean isOutputReady() {
        switch (responseState.get()) {
            case BODY:
                return exchangeHandler.available() > 0;
            default:
                return false;
        }
    }

    void produceOutput() throws IOException {
        switch (responseState.get()) {
            case BODY:
                exchangeHandler.produce(internalDataChannel);
                break;
        }
    }

    void consumeData(final ByteBuffer src) throws HttpException, IOException {
        if (done.get() || requestState.get() != MessageState.BODY) {
            throw new ProtocolException("Unexpected message data");
        }
        if (responseState.get() == MessageState.ACK) {
            outputChannel.requestOutput();
        }
        exchangeHandler.consume(src);
    }

    void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        exchangeHandler.updateCapacity(capacityChannel);
    }

    void dataEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        if (done.get() || requestState.get() != MessageState.BODY) {
            throw new ProtocolException("Unexpected message data");
        }
        requestState.set(MessageState.COMPLETE);
        if (responseState.get() == MessageState.COMPLETE && !keepAlive) {
            outputChannel.close();
        }
        exchangeHandler.streamEnd(trailers);
    }

    void failed(final Exception cause) {
        if (!done.get() && exchangeHandler != null) {
            exchangeHandler.failed(cause);
        } else if (exceptionCallback != null) {
            exceptionCallback.execute(cause);
        }
    }

    @Override
    public void releaseResources() {
        if (done.compareAndSet(false, true)) {
            requestState.set(MessageState.COMPLETE);
            responseState.set(MessageState.COMPLETE);
            if (exchangeHandler != null) {
                exchangeHandler.releaseResources();
            }
        }
    }

    void appendState(final StringBuilder buf) {
        buf.append("requestState=").append(requestState.get())
                .append(", responseState=").append(responseState.get())
                .append(", responseCommitted=").append(responseCommitted)
                .append(", keepAlive=").append(keepAlive)
                .append(", done=").append(done);
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("[");
        appendState(buf);
        buf.append("]");
        return buf.toString();
    }

}


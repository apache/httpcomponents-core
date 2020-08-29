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

class ServerHttp1StreamHandler implements ResourceHolder {

    private final Http1StreamChannel<HttpResponse> outputChannel;
    private final DataStreamChannel internalDataChannel;
    private final ResponseChannel responseChannel;
    private final HttpProcessor httpProcessor;
    private final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory;
    private final ConnectionReuseStrategy connectionReuseStrategy;
    private final HttpCoreContext context;
    private final AtomicBoolean responseCommitted;
    private final AtomicBoolean done;

    private volatile boolean keepAlive;
    private volatile AsyncServerExchangeHandler exchangeHandler;
    private volatile HttpRequest receivedRequest;
    private volatile MessageState requestState;
    private volatile MessageState responseState;

    ServerHttp1StreamHandler(
            final Http1StreamChannel<HttpResponse> outputChannel,
            final HttpProcessor httpProcessor,
            final ConnectionReuseStrategy connectionReuseStrategy,
            final HandlerFactory<AsyncServerExchangeHandler> exchangeHandlerFactory,
            final HttpCoreContext context) {
        this.outputChannel = outputChannel;
        this.internalDataChannel = new DataStreamChannel() {

            @Override
            public void requestOutput() {
                outputChannel.requestOutput();
            }

            @Override
            public void endStream(final List<? extends Header> trailers) throws IOException {
                outputChannel.complete(trailers);
                if (!keepAlive) {
                    outputChannel.close();
                }
                responseState = MessageState.COMPLETE;
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
                ServerSupport.validateResponse(response, responseEntityDetails);
                commitResponse(response, responseEntityDetails);
            }

            @Override
            public void pushPromise(
                    final HttpRequest promise, final AsyncPushProducer pushProducer, final HttpContext httpContext) throws HttpException, IOException {
                commitPromise();
            }

            @Override
            public String toString() {
                return super.toString() + " " + ServerHttp1StreamHandler.this;
            }

        };

        this.httpProcessor = httpProcessor;
        this.connectionReuseStrategy = connectionReuseStrategy;
        this.exchangeHandlerFactory = exchangeHandlerFactory;
        this.context = context;
        this.responseCommitted = new AtomicBoolean(false);
        this.done = new AtomicBoolean(false);
        this.keepAlive = true;
        this.requestState = MessageState.HEADERS;
        this.responseState = MessageState.IDLE;
    }

    private void commitResponse(
            final HttpResponse response,
            final EntityDetails responseEntityDetails) throws HttpException, IOException {
        if (responseCommitted.compareAndSet(false, true)) {

            final ProtocolVersion transportVersion = response.getVersion();
            if (transportVersion != null && transportVersion.greaterEquals(HttpVersion.HTTP_2)) {
                throw new UnsupportedHttpVersionException(transportVersion);
            }

            final int status = response.getCode();
            if (status < HttpStatus.SC_SUCCESS) {
                throw new HttpException("Invalid response: " + status);
            }

            context.setProtocolVersion(transportVersion != null ? transportVersion : HttpVersion.HTTP_1_1);
            context.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
            httpProcessor.process(response, responseEntityDetails, context);

            final boolean endStream = responseEntityDetails == null ||
                    (receivedRequest != null && Method.HEAD.isSame(receivedRequest.getMethod()));

            if (!connectionReuseStrategy.keepAlive(receivedRequest, response, context)) {
                keepAlive = false;
            }

            outputChannel.submit(response, endStream, endStream ? FlushMode.IMMEDIATE : FlushMode.BUFFER);
            if (endStream) {
                if (!keepAlive) {
                    outputChannel.close();
                }
                responseState = MessageState.COMPLETE;
            } else {
                responseState = MessageState.BODY;
                exchangeHandler.produce(internalDataChannel);
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

    void activateChannel() throws IOException, HttpException {
        outputChannel.activate();
    }

    boolean isResponseFinal() {
        return responseState == MessageState.COMPLETE;
    }

    boolean keepAlive() {
        return keepAlive;
    }

    boolean isCompleted() {
        return requestState == MessageState.COMPLETE && responseState == MessageState.COMPLETE;
    }

    void terminateExchange(final HttpException ex) throws HttpException, IOException {
        if (done.get() || requestState != MessageState.HEADERS) {
            throw new ProtocolException("Unexpected message head");
        }
        receivedRequest = null;
        requestState = MessageState.COMPLETE;
        final HttpResponse response = new BasicHttpResponse(ServerSupport.toStatusCode(ex));
        response.addHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
        final AsyncResponseProducer responseProducer = new BasicResponseProducer(response, ServerSupport.toErrorMessage(ex));
        exchangeHandler = new ImmediateResponseExchangeHandler(responseProducer);
        exchangeHandler.handleRequest(null, null, responseChannel, context);
    }

    void consumeHeader(final HttpRequest request, final EntityDetails requestEntityDetails) throws HttpException, IOException {
        if (done.get() || requestState != MessageState.HEADERS) {
            throw new ProtocolException("Unexpected message head");
        }
        receivedRequest = request;
        requestState = requestEntityDetails == null ? MessageState.COMPLETE : MessageState.BODY;

        AsyncServerExchangeHandler handler;
        try {
            handler = exchangeHandlerFactory.create(request, context);
        } catch (final MisdirectedRequestException ex) {
            handler =  new ImmediateResponseExchangeHandler(HttpStatus.SC_MISDIRECTED_REQUEST, ex.getMessage());
        } catch (final HttpException ex) {
            handler =  new ImmediateResponseExchangeHandler(HttpStatus.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
        }
        if (handler == null) {
            handler = new ImmediateResponseExchangeHandler(HttpStatus.SC_NOT_FOUND, "Cannot handle request");
        }

        exchangeHandler = handler;

        final ProtocolVersion transportVersion = request.getVersion();
        if (transportVersion != null && transportVersion.greaterEquals(HttpVersion.HTTP_2)) {
            throw new UnsupportedHttpVersionException(transportVersion);
        }
        context.setProtocolVersion(transportVersion != null ? transportVersion : HttpVersion.HTTP_1_1);
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

        try {
            httpProcessor.process(request, requestEntityDetails, context);
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
        switch (responseState) {
            case BODY:
                return exchangeHandler.available() > 0;
            default:
                return false;
        }
    }

    void produceOutput() throws HttpException, IOException {
        switch (responseState) {
            case BODY:
                exchangeHandler.produce(internalDataChannel);
                break;
        }
    }

    void consumeData(final ByteBuffer src) throws HttpException, IOException {
        if (done.get() || requestState != MessageState.BODY) {
            throw new ProtocolException("Unexpected message data");
        }
        if (responseState == MessageState.ACK) {
            outputChannel.requestOutput();
        }
        exchangeHandler.consume(src);
    }

    void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        exchangeHandler.updateCapacity(capacityChannel);
    }

    void dataEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        if (done.get() || requestState != MessageState.BODY) {
            throw new ProtocolException("Unexpected message data");
        }
        requestState = MessageState.COMPLETE;
        exchangeHandler.streamEnd(trailers);
    }

    void failed(final Exception cause) {
        if (!done.get()) {
            exchangeHandler.failed(cause);
        }
    }

    @Override
    public void releaseResources() {
        if (done.compareAndSet(false, true)) {
            requestState = MessageState.COMPLETE;
            responseState = MessageState.COMPLETE;
            exchangeHandler.releaseResources();
        }
    }

    void appendState(final StringBuilder buf) {
        buf.append("requestState=").append(requestState)
                .append(", responseState=").append(responseState)
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


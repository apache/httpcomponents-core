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
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.UnsupportedHttpVersionException;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResourceHolder;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.util.Timeout;

class ClientHttp1StreamHandler implements ResourceHolder {

    private final Http1StreamChannel<HttpRequest> outputChannel;
    private final DataStreamChannel internalDataChannel;
    private final HttpProcessor httpProcessor;
    private final Http1Config http1Config;
    private final ConnectionReuseStrategy connectionReuseStrategy;
    private final AsyncClientExchangeHandler exchangeHandler;
    private final HttpCoreContext context;
    private final AtomicBoolean requestCommitted;
    private final AtomicBoolean done;

    private volatile boolean keepAlive;
    private volatile Timeout timeout;
    private volatile HttpRequest committedRequest;
    private volatile MessageState requestState;
    private volatile MessageState responseState;

    ClientHttp1StreamHandler(
            final Http1StreamChannel<HttpRequest> outputChannel,
            final HttpProcessor httpProcessor,
            final Http1Config http1Config,
            final ConnectionReuseStrategy connectionReuseStrategy,
            final AsyncClientExchangeHandler exchangeHandler,
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
                requestState = MessageState.COMPLETE;
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

        this.httpProcessor = httpProcessor;
        this.http1Config = http1Config;
        this.connectionReuseStrategy = connectionReuseStrategy;
        this.exchangeHandler = exchangeHandler;
        this.context = context;
        this.requestCommitted = new AtomicBoolean(false);
        this.done = new AtomicBoolean(false);
        this.keepAlive = true;
        this.requestState = MessageState.IDLE;
        this.responseState = MessageState.HEADERS;
    }

    boolean isResponseFinal() {
        return responseState == MessageState.COMPLETE;
    }

    boolean isCompleted() {
        return requestState == MessageState.COMPLETE && responseState == MessageState.COMPLETE;
    }

    String getRequestMethod() {
        return committedRequest != null ? committedRequest.getMethod() : null;
    }

    boolean isOutputReady() {
        switch (requestState) {
            case IDLE:
            case ACK:
                return true;
            case BODY:
                return exchangeHandler.available() > 0;
            default:
                return false;
        }
    }

    private void commitRequest(final HttpRequest request, final EntityDetails entityDetails) throws IOException, HttpException {
        if (requestCommitted.compareAndSet(false, true)) {
            final ProtocolVersion transportVersion = request.getVersion();
            if (transportVersion != null && transportVersion.greaterEquals(HttpVersion.HTTP_2)) {
                throw new UnsupportedHttpVersionException(transportVersion);
            }
            context.setProtocolVersion(transportVersion != null ? transportVersion : HttpVersion.HTTP_1_1);
            context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

            httpProcessor.process(request, entityDetails, context);

            final boolean endStream = entityDetails == null;
            if (endStream) {
                outputChannel.submit(request, endStream, FlushMode.IMMEDIATE);
                committedRequest = request;
                requestState = MessageState.COMPLETE;
            } else {
                final Header h = request.getFirstHeader(HttpHeaders.EXPECT);
                final boolean expectContinue = h != null && HeaderElements.CONTINUE.equalsIgnoreCase(h.getValue());
                outputChannel.submit(request, endStream, expectContinue ? FlushMode.IMMEDIATE : FlushMode.BUFFER);
                committedRequest = request;
                if (expectContinue) {
                    requestState = MessageState.ACK;
                    timeout = outputChannel.getSocketTimeout();
                    outputChannel.setSocketTimeout(http1Config.getWaitForContinueTimeout());
                } else {
                    requestState = MessageState.BODY;
                    exchangeHandler.produce(internalDataChannel);
                }
            }
        } else {
            throw new HttpException("Request already committed");
        }
    }

    void produceOutput() throws HttpException, IOException {
        switch (requestState) {
            case IDLE:
                requestState = MessageState.HEADERS;
                exchangeHandler.produceRequest((request, entityDetails, httpContext) -> commitRequest(request, entityDetails), context);
                break;
            case ACK:
                outputChannel.suspendOutput();
                break;
            case BODY:
                exchangeHandler.produce(internalDataChannel);
                break;
        }
    }

    void consumeHeader(final HttpResponse response, final EntityDetails entityDetails) throws HttpException, IOException {
        if (done.get() || responseState != MessageState.HEADERS) {
            throw new ProtocolException("Unexpected message head");
        }
        final ProtocolVersion transportVersion = response.getVersion();
        if (transportVersion != null && transportVersion.greaterEquals(HttpVersion.HTTP_2)) {
            throw new UnsupportedHttpVersionException(transportVersion);
        }

        final int status = response.getCode();
        if (status < HttpStatus.SC_INFORMATIONAL) {
            throw new ProtocolException("Invalid response: " + new StatusLine(response));
        }
        if (status > HttpStatus.SC_CONTINUE && status < HttpStatus.SC_SUCCESS) {
            exchangeHandler.consumeInformation(response, context);
        } else {
            if (!connectionReuseStrategy.keepAlive(committedRequest, response, context)) {
                keepAlive = false;
            }
        }
        if (requestState == MessageState.ACK) {
            if (status == HttpStatus.SC_CONTINUE || status >= HttpStatus.SC_SUCCESS) {
                outputChannel.setSocketTimeout(timeout);
                requestState = MessageState.BODY;
                if (status < HttpStatus.SC_CLIENT_ERROR) {
                    exchangeHandler.produce(internalDataChannel);
                }
            }
        }
        if (status < HttpStatus.SC_SUCCESS) {
            return;
        }
        if (requestState == MessageState.BODY) {
            if (status >= HttpStatus.SC_CLIENT_ERROR) {
                requestState = MessageState.COMPLETE;
                if (!outputChannel.abortGracefully()) {
                    keepAlive = false;
                }
            }
        }

        context.setProtocolVersion(transportVersion != null ? transportVersion : HttpVersion.HTTP_1_1);
        context.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
        httpProcessor.process(response, entityDetails, context);

        if (entityDetails == null && !keepAlive) {
            outputChannel.close();
        }

        exchangeHandler.consumeResponse(response, entityDetails, context);
        if (entityDetails == null) {
            responseState = MessageState.COMPLETE;
        } else {
            responseState = MessageState.BODY;
        }
    }

    void consumeData(final ByteBuffer src) throws HttpException, IOException {
        if (done.get() || responseState != MessageState.BODY) {
            throw new ProtocolException("Unexpected message data");
        }
        exchangeHandler.consume(src);
    }

    void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        exchangeHandler.updateCapacity(capacityChannel);
    }

    void dataEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        if (done.get() || responseState != MessageState.BODY) {
            throw new ProtocolException("Unexpected message data");
        }
        if (!keepAlive) {
            outputChannel.close();
        }
        responseState = MessageState.COMPLETE;
        exchangeHandler.streamEnd(trailers);
    }

    boolean handleTimeout() {
        if (requestState == MessageState.ACK) {
            requestState = MessageState.BODY;
            outputChannel.setSocketTimeout(timeout);
            outputChannel.requestOutput();
            return true;
        }
        return false;
    }

    void failed(final Exception cause) {
        if (!done.get()) {
            exchangeHandler.failed(cause);
        }
    }

    @Override
    public void releaseResources() {
        if (done.compareAndSet(false, true)) {
            responseState = MessageState.COMPLETE;
            requestState = MessageState.COMPLETE;
            exchangeHandler.releaseResources();
        }
    }

    void appendState(final StringBuilder buf) {
        buf.append("requestState=").append(requestState)
                .append(", responseState=").append(responseState)
                .append(", responseCommitted=").append(requestCommitted)
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


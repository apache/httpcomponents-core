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
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.impl.LazyEntityDetails;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.ContentDecoder;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.HttpContextAware;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.nio.ResourceHolder;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;

class ClientHttp1StreamHandler implements ResourceHolder {

    private final Http1StreamChannel<HttpRequest> outputChannel;
    private final DataStreamChannel internalDataChannel;
    private final HttpProcessor httpProcessor;
    private final H1Config h1Config;
    private final ConnectionReuseStrategy connectionReuseStrategy;
    private final AsyncClientExchangeHandler exchangeHandler;
    private final HttpCoreContext context;
    private final ByteBuffer inputBuffer;
    private final AtomicBoolean requestCommitted;
    private final AtomicBoolean done;

    private volatile int timeout;
    private volatile HttpRequest committedRequest;
    private volatile HttpResponse receivedResponse;
    private volatile MessageState requestState;
    private volatile MessageState responseState;

    ClientHttp1StreamHandler(
            final Http1StreamChannel<HttpRequest> outputChannel,
            final HttpProcessor httpProcessor,
            final H1Config h1Config,
            final ConnectionReuseStrategy connectionReuseStrategy,
            final AsyncClientExchangeHandler exchangeHandler,
            final HttpCoreContext context,
            final ByteBuffer inputBuffer) {
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
        this.h1Config = h1Config;
        this.connectionReuseStrategy = connectionReuseStrategy;
        this.exchangeHandler = exchangeHandler;
        this.context = context;
        this.inputBuffer = inputBuffer;
        this.requestCommitted = new AtomicBoolean(false);
        this.done = new AtomicBoolean(false);
        this.requestState = MessageState.HEADERS;
        this.responseState = MessageState.HEADERS;
    }

    boolean isResponseCompleted() {
        return responseState == MessageState.COMPLETE;
    }

    boolean isCompleted() {
        return requestState == MessageState.COMPLETE && responseState == MessageState.COMPLETE;
    }

    boolean keepAlive() {
        return committedRequest != null && receivedResponse != null &&
                connectionReuseStrategy.keepAlive(committedRequest, receivedResponse, context);
    }

    boolean isHeadRequest() {
        return committedRequest != null && "HEAD".equalsIgnoreCase(committedRequest.getMethod());
    }

    boolean isOutputReady() {
        switch (requestState) {
            case HEADERS:
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
                throw new UnsupportedHttpVersionException("Unsupported version: " + transportVersion);
            }
            context.setProtocolVersion(transportVersion);
            context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

            if (exchangeHandler instanceof HttpContextAware) {
                ((HttpContextAware) exchangeHandler).setContext(context);
            }

            httpProcessor.process(request, entityDetails, context);

            final boolean endStream = entityDetails == null;
            outputChannel.submit(request, endStream);
            committedRequest = request;
            if (endStream) {
                requestState = MessageState.COMPLETE;
            } else {
                final Header h = request.getFirstHeader(HttpHeaders.EXPECT);
                final boolean expectContinue = h != null && "100-continue".equalsIgnoreCase(h.getValue());
                if (expectContinue) {
                    requestState = MessageState.ACK;
                    timeout = outputChannel.getSocketTimeout();
                    outputChannel.setSocketTimeout(h1Config.getWaitForContinueTimeout());
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
            case HEADERS:
                exchangeHandler.produceRequest(new RequestChannel() {

                    @Override
                    public void sendRequest(
                            final HttpRequest request, final EntityDetails entityDetails) throws HttpException, IOException {
                        commitRequest(request, entityDetails);
                    }

                });
                break;
            case ACK:
                outputChannel.suspendOutput();
                break;
            case BODY:
                exchangeHandler.produce(internalDataChannel);
                break;
        }
    }

    void consumeHeader(final HttpResponse response, final boolean endStream) throws HttpException, IOException {
        if (done.get() || responseState != MessageState.HEADERS) {
            throw new ProtocolException("Unexpected message head");
        }
        final ProtocolVersion transportVersion = response.getVersion();
        if (transportVersion != null && transportVersion.greaterEquals(HttpVersion.HTTP_2)) {
            throw new UnsupportedHttpVersionException("Unsupported version: " + transportVersion);
        }

        final int status = response.getCode();
        if (status < HttpStatus.SC_INFORMATIONAL) {
            throw new ProtocolException("Invalid response: " + status);
        }
        if (status < HttpStatus.SC_SUCCESS) {
            exchangeHandler.consumeInformation(response);
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
            boolean keepAlive = status < HttpStatus.SC_CLIENT_ERROR;
            if (keepAlive) {
                final Header h = response.getFirstHeader(HttpHeaders.CONNECTION);
                if (h != null && HeaderElements.CLOSE.equalsIgnoreCase(h.getValue())) {
                    keepAlive = false;
                }
            }
            if (!keepAlive) {
                requestState = MessageState.COMPLETE;
                outputChannel.abortOutput();
            }
        }

        final EntityDetails entityDetails = endStream ? null : new LazyEntityDetails(response);
        context.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
        httpProcessor.process(response, entityDetails, context);
        receivedResponse = response;

        exchangeHandler.consumeResponse(response, entityDetails);
        responseState = endStream ? MessageState.COMPLETE : MessageState.BODY;
    }

    int consumeData(final ContentDecoder contentDecoder) throws HttpException, IOException {
        if (done.get() || responseState != MessageState.BODY) {
            throw new ProtocolException("Unexpected message data");
        }
        int total = 0;
        int byteRead;
        while ((byteRead = contentDecoder.read(inputBuffer)) > 0) {
            total += byteRead;
            inputBuffer.flip();
            final int capacity = exchangeHandler.consume(inputBuffer);
            inputBuffer.clear();
            if (capacity <= 0) {
                if (!contentDecoder.isCompleted()) {
                    outputChannel.suspendInput();
                    exchangeHandler.updateCapacity(outputChannel);
                }
                break;
            }
        }
        if (contentDecoder.isCompleted()) {
            responseState = MessageState.COMPLETE;
            exchangeHandler.streamEnd(contentDecoder.getTrailers());
            return total > 0 ? total : -1;
        } else {
            return total;
        }
    }

    boolean handleTimeout() {
        if (requestState == MessageState.ACK) {
            requestState = MessageState.BODY;
            outputChannel.setSocketTimeout(timeout);
            outputChannel.requestOutput();
            return true;
        } else {
            return false;
        }
    }

    void failed(final Exception cause) {
        exchangeHandler.failed(cause);
    }

    @Override
    public void releaseResources() {
        if (done.compareAndSet(false, true)) {
            responseState = MessageState.COMPLETE;
            requestState = MessageState.COMPLETE;
            exchangeHandler.releaseResources();
        }
    }

    @Override
    public String toString() {
        return "[" +
                "requestState=" + requestState +
                ", responseState=" + responseState +
                ']';
    }

}


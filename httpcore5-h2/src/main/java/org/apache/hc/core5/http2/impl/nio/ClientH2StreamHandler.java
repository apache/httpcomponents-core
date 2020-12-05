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
package org.apache.hc.core5.http2.impl.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
import org.apache.hc.core5.http.impl.BasicHttpConnectionMetrics;
import org.apache.hc.core5.http.impl.IncomingEntityDetails;
import org.apache.hc.core5.http.impl.nio.MessageState;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.impl.DefaultH2RequestConverter;
import org.apache.hc.core5.http2.impl.DefaultH2ResponseConverter;

class ClientH2StreamHandler implements H2StreamHandler {

    private final H2StreamChannel outputChannel;
    private final DataStreamChannel dataChannel;
    private final HttpProcessor httpProcessor;
    private final BasicHttpConnectionMetrics connMetrics;
    private final AsyncClientExchangeHandler exchangeHandler;
    private final HandlerFactory<AsyncPushConsumer> pushHandlerFactory;
    private final HttpCoreContext context;
    private final AtomicBoolean requestCommitted;
    private final AtomicBoolean failed;
    private final AtomicBoolean done;

    private volatile MessageState requestState;
    private volatile MessageState responseState;

    ClientH2StreamHandler(
            final H2StreamChannel outputChannel,
            final HttpProcessor httpProcessor,
            final BasicHttpConnectionMetrics connMetrics,
            final AsyncClientExchangeHandler exchangeHandler,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final HttpCoreContext context) {
        this.outputChannel = outputChannel;
        this.dataChannel = new DataStreamChannel() {

            @Override
            public void requestOutput() {
                outputChannel.requestOutput();
            }

            @Override
            public int write(final ByteBuffer src) throws IOException {
                return outputChannel.write(src);
            }

            @Override
            public void endStream(final List<? extends Header> trailers) throws IOException {
                outputChannel.endStream(trailers);
                requestState = MessageState.COMPLETE;
            }

            @Override
            public void endStream() throws IOException {
                outputChannel.endStream();
                requestState = MessageState.COMPLETE;
            }

        };
        this.httpProcessor = httpProcessor;
        this.connMetrics = connMetrics;
        this.exchangeHandler = exchangeHandler;
        this.pushHandlerFactory = pushHandlerFactory;
        this.context = context;
        this.requestCommitted = new AtomicBoolean(false);
        this.failed = new AtomicBoolean(false);
        this.done = new AtomicBoolean(false);
        this.requestState = MessageState.HEADERS;
        this.responseState = MessageState.HEADERS;
    }

    @Override
    public HandlerFactory<AsyncPushConsumer> getPushHandlerFactory() {
        return pushHandlerFactory;
    }

    @Override
    public boolean isOutputReady() {
        switch (requestState) {
            case HEADERS:
                return true;
            case BODY:
                return exchangeHandler.available() > 0;
            default:
                return false;
        }
    }

    private void commitRequest(final HttpRequest request, final EntityDetails entityDetails) throws HttpException, IOException {
        if (requestCommitted.compareAndSet(false, true)) {
            context.setProtocolVersion(HttpVersion.HTTP_2);
            context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

            httpProcessor.process(request, entityDetails, context);

            final List<Header> headers = DefaultH2RequestConverter.INSTANCE.convert(request);
            outputChannel.submit(headers, entityDetails == null);
            connMetrics.incrementRequestCount();

            if (entityDetails == null) {
                requestState = MessageState.COMPLETE;
            } else {
                final Header h = request.getFirstHeader(HttpHeaders.EXPECT);
                final boolean expectContinue = h != null && HeaderElements.CONTINUE.equalsIgnoreCase(h.getValue());
                if (expectContinue) {
                    requestState = MessageState.ACK;
                } else {
                    requestState = MessageState.BODY;
                    exchangeHandler.produce(dataChannel);
                }
            }
        } else {
            throw new H2ConnectionException(H2Error.INTERNAL_ERROR, "Request already committed");
        }
    }

    @Override
    public void produceOutput() throws HttpException, IOException {
        switch (requestState) {
            case HEADERS:
                exchangeHandler.produceRequest((request, entityDetails, httpContext) -> commitRequest(request, entityDetails), context);
                break;
            case BODY:
                exchangeHandler.produce(dataChannel);
                break;
        }
    }

    @Override
    public void consumePromise(final List<Header> headers) throws HttpException, IOException {
        throw new ProtocolException("Unexpected message promise");
    }

    @Override
    public void consumeHeader(final List<Header> headers, final boolean endStream) throws HttpException, IOException {
        if (done.get()) {
            throw new ProtocolException("Unexpected message headers");
        }
        switch (responseState) {
            case HEADERS:
                final HttpResponse response = DefaultH2ResponseConverter.INSTANCE.convert(headers);
                final int status = response.getCode();
                if (status < HttpStatus.SC_INFORMATIONAL) {
                    throw new ProtocolException("Invalid response: " + new StatusLine(response));
                }
                if (status > HttpStatus.SC_CONTINUE && status < HttpStatus.SC_SUCCESS) {
                    exchangeHandler.consumeInformation(response, context);
                }
                if (requestState == MessageState.ACK) {
                    if (status == HttpStatus.SC_CONTINUE || status >= HttpStatus.SC_SUCCESS) {
                        requestState = MessageState.BODY;
                        exchangeHandler.produce(dataChannel);
                    }
                }
                if (status < HttpStatus.SC_SUCCESS) {
                    return;
                }

                final EntityDetails entityDetails = endStream ? null : new IncomingEntityDetails(response, -1);
                context.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
                httpProcessor.process(response, entityDetails, context);
                connMetrics.incrementResponseCount();

                exchangeHandler.consumeResponse(response, entityDetails, context);
                responseState = endStream ? MessageState.COMPLETE : MessageState.BODY;
                break;
            case BODY:
                responseState = MessageState.COMPLETE;
                exchangeHandler.streamEnd(headers);
                break;
            default:
                throw new ProtocolException("Unexpected message headers");
        }
    }

    @Override
    public void updateInputCapacity() throws IOException {
        exchangeHandler.updateCapacity(outputChannel);
    }

    @Override
    public void consumeData(final ByteBuffer src, final boolean endStream) throws HttpException, IOException {
        if (done.get() || responseState != MessageState.BODY) {
            throw new ProtocolException("Unexpected message data");
        }
        if (src != null) {
            exchangeHandler.consume(src);
        }
        if (endStream) {
            responseState = MessageState.COMPLETE;
            exchangeHandler.streamEnd(null);
        }
    }

    @Override
    public void handle(final HttpException ex, final boolean endStream) throws HttpException, IOException {
        throw ex;
    }

    @Override
    public void failed(final Exception cause) {
        try {
            if (failed.compareAndSet(false, true)) {
                if (exchangeHandler != null) {
                    exchangeHandler.failed(cause);
                }
            }
        } finally {
            releaseResources();
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

    @Override
    public String toString() {
        return "[" +
                "requestState=" + requestState +
                ", responseState=" + responseState +
                ']';
    }

}


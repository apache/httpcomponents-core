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
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.impl.BasicHttpConnectionMetrics;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.impl.DefaultH2RequestConverter;
import org.apache.hc.core5.http2.impl.DefaultH2ResponseConverter;
import org.apache.hc.core5.http2.impl.IncomingEntityDetails;
import org.apache.hc.core5.http2.nio.AsyncExchangeHandler;
import org.apache.hc.core5.http2.nio.AsyncPushProducer;
import org.apache.hc.core5.http2.nio.DataStreamChannel;
import org.apache.hc.core5.http2.nio.ExpectationChannel;
import org.apache.hc.core5.http2.nio.HandlerFactory;
import org.apache.hc.core5.http2.nio.ResponseChannel;
import org.apache.hc.core5.util.Asserts;

public class ServerHttp2StreamHandler implements Http2StreamHandler {

    private final Http2StreamChannel outputChannel;
    private final DataStreamChannel dataChannel;
    private final HttpProcessor httpProcessor;
    private final BasicHttpConnectionMetrics connMetrics;
    private final HandlerFactory<AsyncExchangeHandler> exchangeHandlerFactory;
    private final HttpCoreContext context;
    private final AtomicBoolean responseCommitted;
    private final AtomicBoolean done;

    private volatile HttpRequest request;
    private volatile EntityDetails requestEntityDetails;
    private volatile AsyncExchangeHandler exchangeHandler;
    private volatile MessageState requestState;
    private volatile MessageState responseState;

    ServerHttp2StreamHandler(
            final Http2StreamChannel outputChannel,
            final HttpProcessor httpProcessor,
            final BasicHttpConnectionMetrics connMetrics,
            final HandlerFactory<AsyncExchangeHandler> exchangeHandlerFactory) {
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
            public void endStream(final List<Header> trailers) throws IOException {
                outputChannel.endStream(trailers);
                responseState = MessageState.COMPLETE;
            }

            @Override
            public void endStream() throws IOException {
                outputChannel.endStream();
                responseState = MessageState.COMPLETE;
            }

        };
        this.httpProcessor = httpProcessor;
        this.connMetrics = connMetrics;
        this.exchangeHandlerFactory = exchangeHandlerFactory;
        this.context = HttpCoreContext.create();
        this.responseCommitted = new AtomicBoolean(false);
        this.done = new AtomicBoolean(false);
        this.requestState = MessageState.HEADERS;
        this.responseState = MessageState.IDLE;
    }

    private void commitResponse(
            final HttpResponse response,
            final EntityDetails responseEntityDetails) throws HttpException, IOException {
        if (responseCommitted.compareAndSet(false, true)) {

            context.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
            httpProcessor.process(response, responseEntityDetails, context);

            final List<Header> responseHeaders = DefaultH2ResponseConverter.INSTANCE.convert(response);

            outputChannel.submit(responseHeaders, responseEntityDetails == null);
            connMetrics.incrementResponseCount();
            if (responseEntityDetails == null) {
                responseState = MessageState.COMPLETE;
            } else {
                responseState = MessageState.BODY;
                exchangeHandler.produce(dataChannel);
            }
        } else {
            throw new H2ConnectionException(H2Error.INTERNAL_ERROR, "Response already committed");
        }
    }

    private void commitPromise(
            final HttpRequest promise,
            final AsyncPushProducer pushProducer) throws HttpException, IOException {

        httpProcessor.process(promise, null, context);

        final List<Header> promiseHeaders = DefaultH2RequestConverter.INSTANCE.convert(promise);
        outputChannel.push(promiseHeaders, pushProducer);
        connMetrics.incrementRequestCount();
    }

    private void commitContinue() throws IOException, HttpException {
        final HttpResponse ack = new BasicHttpResponse(HttpStatus.SC_CONTINUE);
        final List<Header> responseHeaders = DefaultH2ResponseConverter.INSTANCE.convert(ack);

        outputChannel.submit(responseHeaders, false);
    }

    @Override
    public void consumePromise(final List<Header> headers) throws HttpException, IOException {
        throw new ProtocolException("Unexpected message promise");
    }

    @Override
    public void consumeHeader(final List<Header> requestHeaders, final boolean requestEndStream) throws HttpException, IOException {
        if (done.get() || requestState != MessageState.HEADERS) {
            throw new ProtocolException("Unexpected message headers");
        }
        requestState = requestEndStream ? MessageState.COMPLETE : MessageState.BODY;

        final HttpRequest request = DefaultH2RequestConverter.INSTANCE.convert(requestHeaders);
        final EntityDetails requestEntityDetails = requestEndStream ? null : new IncomingEntityDetails(request);

        context.setProtocolVersion(HttpVersion.HTTP_2);
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
        context.setAttribute(HttpCoreContext.HTTP_CONNECTION, this);
        httpProcessor.process(request, requestEntityDetails, context);
        connMetrics.incrementRequestCount();

        exchangeHandler = exchangeHandlerFactory.create(request, context);
        if (exchangeHandler == null) {
            throw new H2ConnectionException(H2Error.INTERNAL_ERROR,
                    "Unable to handle " + request.getMethod() + " " + request.getPath());
        }

        boolean expectContinue = false;
        if (requestEntityDetails != null) {
            final Header h = request.getFirstHeader(HttpHeaders.EXPECT);
            if ("100-continue".equalsIgnoreCase(h.getValue())) {
                expectContinue = true;
            }
        }

        if (expectContinue) {
            exchangeHandler.verify(request, requestEntityDetails, new ExpectationChannel() {

                @Override
                public void sendResponse(
                        final HttpResponse response, final EntityDetails responseEntityDetails) throws HttpException, IOException {
                    commitResponse(response, responseEntityDetails);
                }

                @Override
                public void sendContinue() throws HttpException, IOException {
                    commitContinue();
                    exchangeHandler.handleRequest(request, requestEntityDetails, new ResponseChannel() {

                        @Override
                        public void sendResponse(
                                final HttpResponse response, final EntityDetails responseEntityDetails) throws HttpException, IOException {
                            commitResponse(response, responseEntityDetails);
                        }

                        @Override
                        public void pushPromise(
                                final HttpRequest promise, final AsyncPushProducer pushProducer) throws HttpException, IOException {
                            commitPromise(promise, pushProducer);
                        }

                    });
                }

            });
        } else {
            exchangeHandler.handleRequest(request, requestEntityDetails, new ResponseChannel() {

                @Override
                public void sendResponse(
                        final HttpResponse response, final EntityDetails responseEntityDetails) throws HttpException, IOException {
                    commitResponse(response, responseEntityDetails);
                }

                @Override
                public void pushPromise(
                        final HttpRequest promise, final AsyncPushProducer pushProducer) throws HttpException, IOException {
                    commitPromise(promise, pushProducer);
                }

            });
        }
    }

    @Override
    public void updateInputCapacity() throws IOException {
        Asserts.notNull(exchangeHandler, "Exchange handler");
        exchangeHandler.updateCapacity(outputChannel);
    }

    @Override
    public void consumeData(final ByteBuffer src, final boolean endStream) throws HttpException, IOException {
        if (done.get() || requestState != MessageState.BODY) {
            throw new ProtocolException("Unexpected message data");
        }
        Asserts.notNull(exchangeHandler, "Exchange handler");
        if (src != null) {
            exchangeHandler.consume(src);
        }
        if (endStream) {
            requestState = MessageState.COMPLETE;
            exchangeHandler.streamEnd(null);
        }
    }

    @Override
    public boolean isOutputReady() {
        return responseState == MessageState.BODY && exchangeHandler != null && exchangeHandler.available() > 0;
    }

    @Override
    public void produceOutput() throws HttpException, IOException {
        if (responseState == MessageState.BODY) {
            Asserts.notNull(exchangeHandler, "Exchange handler");
            exchangeHandler.produce(dataChannel);
        }
    }

    @Override
    public void failed(final Exception cause) {
        try {
            if (exchangeHandler != null) {
                exchangeHandler.failed(cause);
            }
        } finally {
            releaseResources();
        }
    }

    @Override
    public void cancel() {
        releaseResources();
    }

    @Override
    public void releaseResources() {
        if (done.compareAndSet(false, true)) {
            requestState = MessageState.COMPLETE;
            responseState = MessageState.COMPLETE;
            if (exchangeHandler != null) {
                exchangeHandler.releaseResources();
            }
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


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

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.impl.DefaultH2RequestConverter;
import org.apache.hc.core5.http2.impl.DefaultH2ResponseConverter;
import org.apache.hc.core5.http2.nio.AsyncExchangeHandler;
import org.apache.hc.core5.http2.nio.AsyncPushProducer;
import org.apache.hc.core5.http2.nio.HandlerFactory;
import org.apache.hc.core5.http2.nio.ResponseChannel;
import org.apache.hc.core5.util.Asserts;

public class ServerHttp2StreamHandler implements Http2StreamHandler {

    private final HandlerFactory<AsyncExchangeHandler> exchangeHandlerFactory;
    private final AtomicBoolean done;

    private volatile Http2StreamChannel internalOutputChannel;
    private volatile AsyncExchangeHandler exchangeHandler;
    private volatile MessageState requestState;
    private volatile MessageState responseState;

    ServerHttp2StreamHandler(final Http2StreamChannel outputChannel, final HandlerFactory<AsyncExchangeHandler> exchangeHandlerFactory) {
        this.internalOutputChannel = new Http2StreamChannel() {

            @Override
            public void submit(final List<Header> headers, final boolean endStream) throws HttpException, IOException {
                outputChannel.submit(headers, endStream);
                responseState = endStream ? MessageState.COMPLETE : MessageState.BODY;
            }

            @Override
            public void push(final List<Header> headers, final AsyncPushProducer responseProducer) throws HttpException, IOException {
                outputChannel.push(headers, responseProducer);
            }

            @Override
            public void update(final int increment) throws IOException {
                outputChannel.update(increment);
            }

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
        this.exchangeHandlerFactory = exchangeHandlerFactory;
        this.done = new AtomicBoolean(false);
        this.requestState = MessageState.HEADERS;
        this.responseState = MessageState.IDLE;
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

        exchangeHandler = exchangeHandlerFactory.create(request, null);
        if (exchangeHandler == null) {
            throw new H2ConnectionException(H2Error.INTERNAL_ERROR,
                    "Unable to handle " + request.getMethod() + " " + request.getPath());
        }
        exchangeHandler.handleRequest(request, requestEndStream, new ResponseChannel() {

            private final AtomicBoolean responseCommitted = new AtomicBoolean(false);

            @Override
            public void sendResponse(
                    final HttpResponse response, final boolean responseEndStream) throws HttpException, IOException {
                if (responseCommitted.compareAndSet(false, true)) {
                    final List<Header> responseHeaders = DefaultH2ResponseConverter.INSTANCE.convert(response);
                    internalOutputChannel.submit(responseHeaders, responseEndStream);
                    if (!responseEndStream) {
                        exchangeHandler.produce(internalOutputChannel);
                    }
                } else {
                    throw new H2ConnectionException(H2Error.INTERNAL_ERROR, "Response already committed");
                }
            }

            @Override
            public void pushPromise(final HttpRequest promise, final AsyncPushProducer pushProducer) throws HttpException, IOException {
                final List<Header> promiseHeaders = DefaultH2RequestConverter.INSTANCE.convert(promise);
                internalOutputChannel.push(promiseHeaders, pushProducer);
            }

            @Override
            public String toString() {
                return internalOutputChannel.toString();
            }

        });
    }

    @Override
    public void updateInputCapacity() throws IOException {
        Asserts.notNull(exchangeHandler, "Exchange handler");
        exchangeHandler.updateCapacity(internalOutputChannel);
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
            exchangeHandler.produce(internalOutputChannel);
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


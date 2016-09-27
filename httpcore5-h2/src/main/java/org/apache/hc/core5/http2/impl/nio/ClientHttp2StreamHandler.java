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

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.impl.BasicHttpConnectionMetrics;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.impl.DefaultH2RequestConverter;
import org.apache.hc.core5.http2.impl.DefaultH2ResponseConverter;
import org.apache.hc.core5.http2.impl.IncomingEntityDetails;
import org.apache.hc.core5.http2.nio.AsyncPushProducer;
import org.apache.hc.core5.http2.nio.AsyncRequestProducer;
import org.apache.hc.core5.http2.nio.AsyncResponseConsumer;

class ClientHttp2StreamHandler<T> implements Http2StreamHandler {

    private final Http2StreamChannel internalOutputChannel;
    private final HttpProcessor httpProcessor;
    private final BasicHttpConnectionMetrics connMetrics;
    private final AsyncRequestProducer requestProducer;
    private final AsyncResponseConsumer<T> responseConsumer;
    private final HttpCoreContext context;
    private final FutureCallback<T> resultCallback;
    private final AtomicBoolean done;

    private volatile MessageState requestState;
    private volatile MessageState responseState;

    ClientHttp2StreamHandler(
            final Http2StreamChannel outputChannel,
            final HttpProcessor httpProcessor,
            final BasicHttpConnectionMetrics connMetrics,
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final HttpContext context,
            final FutureCallback<T> resultCallback) {
        this.internalOutputChannel = new Http2StreamChannel() {

            @Override
            public void submit(final List<Header> headers, final boolean endStream) throws HttpException, IOException {
                outputChannel.submit(headers, endStream);
                requestState = endStream ? MessageState.COMPLETE : MessageState.BODY;
            }

            @Override
            public void push(final List<Header> headers, final AsyncPushProducer pushProducer) throws IOException {
                throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Push not allowed");
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
        this.requestProducer = requestProducer;
        this.responseConsumer = responseConsumer;
        this.resultCallback = resultCallback;
        this.context = HttpCoreContext.adapt(context);
        this.done = new AtomicBoolean(false);
        this.requestState = MessageState.HEADERS;
        this.responseState = MessageState.HEADERS;
    }

    @Override
    public boolean isOutputReady() {
        switch (requestState) {
            case HEADERS:
                return true;
            case BODY:
                return requestProducer.available() > 0;
            default:
                return false;
        }
    }

    @Override
    public void produceOutput() throws HttpException, IOException {
        switch (requestState) {
            case HEADERS:
                final HttpRequest request = requestProducer.produceRequest();
                final EntityDetails entityDetails = requestProducer.getEntityDetails();

                context.setProtocolVersion(HttpVersion.HTTP_2);
                context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
                context.setAttribute(HttpCoreContext.HTTP_CONNECTION, this);
                httpProcessor.process(request, entityDetails, context);

                final List<Header> headers = DefaultH2RequestConverter.INSTANCE.convert(request);
                internalOutputChannel.submit(headers, entityDetails == null);
                connMetrics.incrementRequestCount();
                if (entityDetails != null) {
                    requestProducer.dataStart(internalOutputChannel);
                }
                break;
            case BODY:
                requestProducer.produce(internalOutputChannel);
                break;
        }
    }

    @Override
    public void consumePromise(final List<Header> headers) throws HttpException, IOException {
        throw new ProtocolException("Unexpected message promise");
    }

    @Override
    public void consumeHeader(final List<Header> headers, final boolean endStream) throws HttpException, IOException {
        if (done.get() || responseState != MessageState.HEADERS) {
            throw new ProtocolException("Unexpected message headers");
        }
        final HttpResponse response = DefaultH2ResponseConverter.INSTANCE.convert(headers);
        final EntityDetails entityDetails = endStream ? null : new IncomingEntityDetails(response);

        context.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
        httpProcessor.process(response, entityDetails, context);
        connMetrics.incrementResponseCount();

        responseConsumer.consumeResponse(response, entityDetails, new FutureCallback<T>() {

            @Override
            public void completed(final T result) {
                if (resultCallback != null) {
                    resultCallback.completed(result);
                }
            }

            @Override
            public void failed(final Exception ex) {
                if (resultCallback != null) {
                    resultCallback.failed(ex);
                }
            }

            @Override
            public void cancelled() {
                if (resultCallback != null) {
                    resultCallback.cancelled();
                }
            }

        });
        if (endStream) {
            responseState = MessageState.COMPLETE;
            responseConsumer.streamEnd(null);
        } else {
            responseState = MessageState.BODY;
        }
    }

    @Override
    public void updateInputCapacity() throws IOException {
        responseConsumer.updateCapacity(internalOutputChannel);
    }

    @Override
    public void consumeData(final ByteBuffer src, final boolean endStream) throws HttpException, IOException {
        if (done.get() || responseState != MessageState.BODY) {
            throw new ProtocolException("Unexpected message data");
        }
        if (src != null) {
            responseConsumer.consume(src);
        }
        if (endStream) {
            responseState = MessageState.COMPLETE;
            responseConsumer.streamEnd(null);
        }
    }

    @Override
    public void failed(final Exception cause) {
        try {
            if (resultCallback != null) {
                resultCallback.failed(cause);
            }
            requestProducer.failed(cause);
        } finally {
            releaseResources();
        }
    }

    @Override
    public void cancel() {
        try {
            if (resultCallback != null) {
                resultCallback.cancelled();
            }
        } finally {
            releaseResources();
        }
    }

    @Override
    public void releaseResources() {
        if (done.compareAndSet(false, true)) {
            responseState = MessageState.COMPLETE;
            responseConsumer.releaseResources();
            requestState = MessageState.COMPLETE;
            requestProducer.releaseResources();
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


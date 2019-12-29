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
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.impl.BasicHttpConnectionMetrics;
import org.apache.hc.core5.http.impl.IncomingEntityDetails;
import org.apache.hc.core5.http.impl.nio.MessageState;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.H2StreamResetException;
import org.apache.hc.core5.http2.impl.DefaultH2RequestConverter;
import org.apache.hc.core5.http2.impl.DefaultH2ResponseConverter;
import org.apache.hc.core5.util.Asserts;

class ClientPushH2StreamHandler implements H2StreamHandler {

    private final H2StreamChannel internalOutputChannel;
    private final HttpProcessor httpProcessor;
    private final BasicHttpConnectionMetrics connMetrics;
    private final HandlerFactory<AsyncPushConsumer> pushHandlerFactory;
    private final HttpCoreContext context;
    private final AtomicBoolean failed;
    private final AtomicBoolean done;

    private volatile HttpRequest request;
    private volatile AsyncPushConsumer exchangeHandler;
    private volatile MessageState requestState;
    private volatile MessageState responseState;

    ClientPushH2StreamHandler(
            final H2StreamChannel outputChannel,
            final HttpProcessor httpProcessor,
            final BasicHttpConnectionMetrics connMetrics,
            final HandlerFactory<AsyncPushConsumer> pushHandlerFactory,
            final HttpCoreContext context) {
        this.internalOutputChannel = outputChannel;
        this.httpProcessor = httpProcessor;
        this.connMetrics = connMetrics;
        this.pushHandlerFactory = pushHandlerFactory;
        this.context = context;
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
        return false;
    }

    @Override
    public void produceOutput() throws HttpException, IOException {
    }

    @Override
    public void consumePromise(final List<Header> headers) throws HttpException, IOException {
        if (requestState == MessageState.HEADERS) {

            request = DefaultH2RequestConverter.INSTANCE.convert(headers);

            final AsyncPushConsumer handler;
            try {
                handler = pushHandlerFactory != null ? pushHandlerFactory.create(request, context) : null;
            } catch (final ProtocolException ex) {
                throw new H2StreamResetException(H2Error.PROTOCOL_ERROR, ex.getMessage());
            }
            if (handler == null) {
                throw new H2StreamResetException(H2Error.REFUSED_STREAM, "Stream refused");
            }
            exchangeHandler = handler;

            context.setProtocolVersion(HttpVersion.HTTP_2);
            context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);

            httpProcessor.process(request, null, context);
            connMetrics.incrementRequestCount();
            this.requestState = MessageState.COMPLETE;
        } else {
            throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Unexpected promise");
        }
    }

    @Override
    public void consumeHeader(final List<Header> headers, final boolean endStream) throws HttpException, IOException {
        if (responseState == MessageState.HEADERS) {
            Asserts.notNull(request, "Request");
            Asserts.notNull(exchangeHandler, "Exchange handler");

            final HttpResponse response = DefaultH2ResponseConverter.INSTANCE.convert(headers);
            final EntityDetails entityDetails = endStream ? null : new IncomingEntityDetails(request, -1);

            context.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
            httpProcessor.process(response, entityDetails, context);
            connMetrics.incrementResponseCount();

            exchangeHandler.consumePromise(request, response, entityDetails, context);
            if (endStream) {
                responseState = MessageState.COMPLETE;
                exchangeHandler.streamEnd(null);
            } else {
                responseState = MessageState.BODY;
            }
        } else {
            throw new ProtocolException("Unexpected message headers");
        }
    }

    @Override
    public void updateInputCapacity() throws IOException {
        Asserts.notNull(exchangeHandler, "Exchange handler");
        exchangeHandler.updateCapacity(internalOutputChannel);
    }

    @Override
    public void consumeData(final ByteBuffer src, final boolean endStream) throws HttpException, IOException {
        if (responseState != MessageState.BODY) {
            throw new ProtocolException("Unexpected message data");
        }
        Asserts.notNull(exchangeHandler, "Exchange handler");
        if (src != null) {
            exchangeHandler.consume(src);
        }
        if (endStream) {
            responseState = MessageState.COMPLETE;
            exchangeHandler.streamEnd(null);
        }
    }

    public boolean isDone() {
        return responseState == MessageState.COMPLETE;
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
    public void handle(final HttpException ex, final boolean endStream) throws HttpException {
        throw ex;
    }

    @Override
    public void releaseResources() {
        if (done.compareAndSet(false, true)) {
            responseState = MessageState.COMPLETE;
            requestState = MessageState.COMPLETE;
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


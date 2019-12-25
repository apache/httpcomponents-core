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
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.impl.BasicHttpConnectionMetrics;
import org.apache.hc.core5.http.impl.nio.MessageState;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.AsyncPushProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.impl.DefaultH2RequestConverter;
import org.apache.hc.core5.http2.impl.DefaultH2ResponseConverter;

class ServerPushH2StreamHandler implements H2StreamHandler {

    private final H2StreamChannel outputChannel;
    private final DataStreamChannel dataChannel;
    private final HttpProcessor httpProcessor;
    private final BasicHttpConnectionMetrics connMetrics;
    private final AsyncPushProducer pushProducer;
    private final HttpCoreContext context;
    private final AtomicBoolean responseCommitted;
    private final AtomicBoolean failed;
    private final AtomicBoolean done;

    private volatile MessageState requestState;
    private volatile MessageState responseState;

    ServerPushH2StreamHandler(
            final H2StreamChannel outputChannel,
            final HttpProcessor httpProcessor,
            final BasicHttpConnectionMetrics connMetrics,
            final AsyncPushProducer pushProducer,
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
        this.pushProducer = pushProducer;
        this.context = context;
        this.responseCommitted = new AtomicBoolean(false);
        this.failed = new AtomicBoolean(false);
        this.done = new AtomicBoolean(false);
        this.requestState = MessageState.COMPLETE;
        this.responseState = MessageState.IDLE;
    }

    @Override
    public HandlerFactory<AsyncPushConsumer> getPushHandlerFactory() {
        return null;
    }

    @Override
    public void consumePromise(final List<Header> headers) throws HttpException, IOException {
        throw new ProtocolException("Unexpected message promise");
    }

    @Override
    public void consumeHeader(final List<Header> requestHeaders, final boolean requestEndStream) throws HttpException, IOException {
        throw new ProtocolException("Unexpected message headers");
    }

    @Override
    public void updateInputCapacity() throws IOException {
    }

    @Override
    public void consumeData(final ByteBuffer src, final boolean endStream) throws HttpException, IOException {
        throw new ProtocolException("Unexpected message data");
    }

    @Override
    public boolean isOutputReady() {
        switch (responseState) {
            case IDLE:
                return true;
            case BODY:
                return pushProducer.available() > 0;
            default:
                return false;
        }
    }

    private void commitInformation(final HttpResponse response) throws IOException, HttpException {
        if (responseCommitted.get()) {
            throw new H2ConnectionException(H2Error.INTERNAL_ERROR, "Response already committed");
        }
        final int status = response.getCode();
        if (status < HttpStatus.SC_INFORMATIONAL || status >= HttpStatus.SC_SUCCESS) {
            throw new HttpException("Invalid intermediate response: " + status);
        }
        final List<Header> responseHeaders = DefaultH2ResponseConverter.INSTANCE.convert(response);
        outputChannel.submit(responseHeaders, false);
    }

    private void commitResponse(
            final HttpResponse response,
            final EntityDetails responseEntityDetails) throws HttpException, IOException {
        if (responseCommitted.compareAndSet(false, true)) {

            context.setProtocolVersion(HttpVersion.HTTP_2);
            context.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
            httpProcessor.process(response, responseEntityDetails, context);

            final List<Header> headers = DefaultH2ResponseConverter.INSTANCE.convert(response);
            outputChannel.submit(headers, responseEntityDetails == null);
            connMetrics.incrementResponseCount();
            if (responseEntityDetails == null) {
                responseState = MessageState.COMPLETE;
            } else {
                responseState = MessageState.BODY;
                pushProducer.produce(outputChannel);
            }
        }
    }

    private void commitPromise(
            final HttpRequest promise,
            final AsyncPushProducer pushProducer) throws HttpException, IOException {

        context.setProtocolVersion(HttpVersion.HTTP_2);
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, promise);
        httpProcessor.process(promise, null, context);

        final List<Header> headers = DefaultH2RequestConverter.INSTANCE.convert(promise);

        outputChannel.push(headers, pushProducer);
        connMetrics.incrementRequestCount();
    }

    @Override
    public void produceOutput() throws HttpException, IOException {
        switch (responseState) {
            case IDLE:
                responseState = MessageState.HEADERS;
                pushProducer.produceResponse(new ResponseChannel() {

                    @Override
                    public void sendInformation(final HttpResponse response, final HttpContext httpContext) throws HttpException, IOException {
                        commitInformation(response);
                    }

                    @Override
                    public void sendResponse(
                            final HttpResponse response, final EntityDetails entityDetails, final HttpContext httpContext) throws HttpException, IOException {
                        commitResponse(response, entityDetails);
                    }

                    @Override
                    public void pushPromise(
                            final HttpRequest promise, final AsyncPushProducer pushProducer, final HttpContext httpContext) throws HttpException, IOException {
                        commitPromise(promise, pushProducer);
                    }

                }, context);
                break;
            case BODY:
                pushProducer.produce(dataChannel);
                break;
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
                pushProducer.failed(cause);
            }
        } finally {
            releaseResources();
        }
    }

    @Override
    public void releaseResources() {
        if (done.compareAndSet(false, true)) {
            requestState = MessageState.COMPLETE;
            responseState = MessageState.COMPLETE;
            pushProducer.releaseResources();
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


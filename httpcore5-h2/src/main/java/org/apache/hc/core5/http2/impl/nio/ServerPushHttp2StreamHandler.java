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
import org.apache.hc.core5.http2.nio.AsyncPushProducer;
import org.apache.hc.core5.http2.nio.ResponseChannel;

class ServerPushHttp2StreamHandler implements Http2StreamHandler {

    private final AsyncPushProducer pushProducer;
    private final AtomicBoolean done;

    private volatile Http2StreamChannel internalOutputChannel;
    private volatile MessageState requestState;
    private volatile MessageState responseState;

    ServerPushHttp2StreamHandler(final Http2StreamChannel outputChannel, final AsyncPushProducer pushProducer) {
        this.pushProducer = pushProducer;
        this.done = new AtomicBoolean(false);
        this.requestState = MessageState.COMPLETE;
        this.responseState = MessageState.IDLE;
        this.internalOutputChannel = new Http2StreamChannel() {

            @Override
            public void submit(final List<Header> headers, final boolean endStream) throws HttpException, IOException {
                outputChannel.submit(headers, endStream);
                responseState = endStream ? MessageState.COMPLETE : MessageState.BODY;
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
                responseState = MessageState.COMPLETE;
            }

            @Override
            public void endStream() throws IOException {
                outputChannel.endStream();
                responseState = MessageState.COMPLETE;
            }

        };
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

    @Override
    public void produceOutput() throws HttpException, IOException {
        switch (responseState) {
            case IDLE:
                responseState = MessageState.HEADERS;
                pushProducer.produceResponse(new ResponseChannel() {

                    private final AtomicBoolean responseCommitted = new AtomicBoolean(false);

                    @Override
                    public void sendResponse(
                            final HttpResponse response, final boolean endStream) throws HttpException, IOException {
                        if (responseCommitted.compareAndSet(false, true)) {
                            final List<Header> headers = DefaultH2ResponseConverter.INSTANCE.convert(response);
                            internalOutputChannel.submit(headers, endStream);
                        }
                    }

                    @Override
                    public void pushPromise(
                            final HttpRequest promise, final AsyncPushProducer pushProducer) throws HttpException, IOException {
                        final List<Header> headers = DefaultH2RequestConverter.INSTANCE.convert(promise);
                        internalOutputChannel.push(headers, pushProducer);
                    }

                });
                break;
            case BODY:
                pushProducer.produce(internalOutputChannel);
                break;
        }
    }

    @Override
    public void failed(final Exception cause) {
        try {
            pushProducer.failed(cause);
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


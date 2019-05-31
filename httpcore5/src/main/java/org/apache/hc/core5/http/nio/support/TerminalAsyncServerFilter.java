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
package org.apache.hc.core5.http.nio.support;

import java.io.IOException;
import java.util.Set;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncFilterChain;
import org.apache.hc.core5.http.nio.AsyncFilterHandler;
import org.apache.hc.core5.http.nio.AsyncPushProducer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;

/**
 * {@link AsyncFilterHandler} implementation represents a terminal handler
 * in an asynchronous request processing pipeline that makes use of {@link HandlerFactory}
 * to dispatch the request to a particular {@link AsyncServerExchangeHandler}.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public final class TerminalAsyncServerFilter implements AsyncFilterHandler {

    final private HandlerFactory<AsyncServerExchangeHandler> handlerFactory;

    public TerminalAsyncServerFilter(final HandlerFactory<AsyncServerExchangeHandler> handlerFactory) {
        this.handlerFactory = Args.notNull(handlerFactory, "Handler factory");
    }

    @Override
    public AsyncDataConsumer handle(
            final HttpRequest request,
            final EntityDetails entityDetails,
            final HttpContext context,
            final AsyncFilterChain.ResponseTrigger responseTrigger,
            final AsyncFilterChain chain) throws HttpException, IOException {
        final AsyncServerExchangeHandler exchangeHandler = handlerFactory.create(request, context);
        if (exchangeHandler != null) {
            exchangeHandler.handleRequest(request, entityDetails, new ResponseChannel() {

                @Override
                public void sendInformation(final HttpResponse response, final HttpContext httpContext) throws HttpException, IOException {
                    responseTrigger.sendInformation(response);
                }

                @Override
                public void sendResponse(final HttpResponse response, final EntityDetails entityDetails, final HttpContext httpContext) throws HttpException, IOException {
                    responseTrigger.submitResponse(response, entityDetails != null ? new AsyncEntityProducer() {

                        @Override
                        public void failed(final Exception cause) {
                            exchangeHandler.failed(cause);
                        }

                        @Override
                        public boolean isRepeatable() {
                            return false;
                        }

                        @Override
                        public long getContentLength() {
                            return entityDetails.getContentLength();
                        }

                        @Override
                        public String getContentType() {
                            return entityDetails.getContentType();
                        }

                        @Override
                        public String getContentEncoding() {
                            return entityDetails.getContentEncoding();
                        }

                        @Override
                        public boolean isChunked() {
                            return entityDetails.isChunked();
                        }

                        @Override
                        public Set<String> getTrailerNames() {
                            return entityDetails.getTrailerNames();
                        }

                        @Override
                        public int available() {
                            return exchangeHandler.available();
                        }

                        @Override
                        public void produce(final DataStreamChannel channel) throws IOException {
                            exchangeHandler.produce(channel);
                        }

                        @Override
                        public void releaseResources() {
                            exchangeHandler.releaseResources();
                        }

                    } : null);
                }

                @Override
                public void pushPromise(final HttpRequest promise, final AsyncPushProducer pushProducer, final HttpContext httpContext) throws HttpException, IOException {
                    responseTrigger.pushPromise(promise, pushProducer);
                }

            }, context);
            return exchangeHandler;
        }
        responseTrigger.submitResponse(new BasicHttpResponse(HttpStatus.SC_NOT_FOUND), AsyncEntityProducers.create("Not found"));
        return null;
    }

}

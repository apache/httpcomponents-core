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
package org.apache.hc.core5.benchmark;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.nio.ResourceHolder;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.FileEntityProducer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;

class BenchmarkWorker implements ResourceHolder {

    private final HttpAsyncRequester requester;
    private final HttpHost host;
    private final HttpCoreContext context;
    private final AtomicLong requestCount;
    private final CountDownLatch completionLatch;
    private final Stats stats;
    private final BenchmarkConfig config;
    private final AtomicReference<AsyncClientEndpoint> endpointRef;

    public BenchmarkWorker(
            final HttpAsyncRequester requester,
            final HttpHost host,
            final HttpCoreContext context,
            final AtomicLong requestCount,
            final CountDownLatch completionLatch,
            final Stats stats,
            final BenchmarkConfig config) {
        this.requester = requester;
        this.host = host;
        this.context = context;
        this.requestCount = requestCount;
        this.completionLatch = completionLatch;
        this.stats = stats;
        this.config = config;
        this.endpointRef = new AtomicReference<>(null);
    }

    private AsyncRequestProducer createRequestProducer() {
        String method = config.getMethod();
        if (method == null) {
            method = config.isHeadInsteadOfGet() ? Method.HEAD.name() : Method.GET.name();
        }

        final BasicHttpRequest request = new BasicHttpRequest(method, config.getUri());
        final String[] headers = config.getHeaders();
        if (headers != null) {
            for (final String s : headers) {
                final int pos = s.indexOf(':');
                if (pos != -1) {
                    request.addHeader(new BasicHeader(s.substring(0, pos).trim(), s.substring(pos + 1)));
                }
            }
        }
        if (!config.isKeepAlive() && !config.isForceHttp2()) {
            request.addHeader(new BasicHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE));
        }
        if (config.isUseAcceptGZip()) {
            request.addHeader(new BasicHeader(HttpHeaders.ACCEPT_ENCODING, "gzip"));
        }
        if (config.getSoapAction() != null && config.getSoapAction().length() > 0) {
            request.addHeader(new BasicHeader("SOAPAction", config.getSoapAction()));
        }

        final AsyncEntityProducer entityProducer;
        if (config.getPayloadFile() != null) {
            entityProducer = new FileEntityProducer(
                    config.getPayloadFile(),
                    config.getContentType(),
                    config.isUseChunking());
        } else if (config.getPayloadText() != null) {
            entityProducer = new BasicAsyncEntityProducer(
                    config.getPayloadText(),
                    config.getContentType(),
                    config.isUseChunking());
        } else {
            entityProducer = null;
        }

        return new AsyncRequestProducer() {

            @Override
            public void sendRequest(
                    final RequestChannel channel,
                    final HttpContext context) throws HttpException, IOException {
                channel.sendRequest(request, entityProducer, context);
            }

            @Override
            public boolean isRepeatable() {
                return entityProducer == null || entityProducer.isRepeatable();
            }

            @Override
            public int available() {
                return entityProducer != null ? entityProducer.available() : 0;
            }

            @Override
            public void produce(final DataStreamChannel channel) throws IOException {
                if (entityProducer != null) {
                    entityProducer.produce(channel);
                }
            }

            @Override
            public void failed(final Exception cause) {
                if (config.getVerbosity() >= 1) {
                    System.out.println("Failed HTTP request: " + cause.getMessage());
                }
            }

            @Override
            public void releaseResources() {
                if (entityProducer != null) {
                    entityProducer.releaseResources();
                }
            }

        };
    }

    private AsyncResponseConsumer<Void> createResponseConsumer() {

        return new AsyncResponseConsumer<Void>() {

            volatile int status;
            volatile Charset charset;
            final AtomicLong contentLength = new AtomicLong();
            final AtomicReference<FutureCallback<Void>> resultCallbackRef = new AtomicReference<>(null);

            @Override
            public void consumeResponse(
                    final HttpResponse response,
                    final EntityDetails entityDetails,
                    final HttpContext context,
                    final FutureCallback<Void> resultCallback) throws HttpException, IOException {
                status = response.getCode();
                resultCallbackRef.set(resultCallback);
                stats.setVersion(response.getVersion());
                final Header serverHeader = response.getFirstHeader(HttpHeaders.SERVER);
                if (serverHeader != null) {
                    stats.setServerName(serverHeader.getValue());
                }
                if (config.getVerbosity() >= 2) {
                    System.out.println(response.getCode());
                }
                if (entityDetails != null) {
                    if (config.getVerbosity() >= 6) {
                        if (entityDetails.getContentType() != null) {
                            final ContentType contentType = ContentType.parseLenient(entityDetails.getContentType());
                            charset = contentType.getCharset();
                        }
                    }
                } else {
                    streamEnd(null);
                }
            }

            @Override
            public void informationResponse(
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
            }

            @Override
            public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
                capacityChannel.update(Integer.MAX_VALUE);
            }

            @Override
            public void consume(final ByteBuffer src) throws IOException {
                final int n = src.remaining();
                contentLength.addAndGet(n);
                stats.incTotalContentLength(n);
                if (config.getVerbosity() >= 6) {
                    final CharsetDecoder decoder = (charset != null ? charset : StandardCharsets.US_ASCII).newDecoder();
                    System.out.print(decoder.decode(src));
                }
            }

            @Override
            public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
                if (status == HttpStatus.SC_OK) {
                    stats.incSuccessCount();
                } else {
                    stats.incFailureCount();
                }
                stats.setContentLength(contentLength.get());
                final FutureCallback<Void> resultCallback = resultCallbackRef.getAndSet(null);
                if (resultCallback != null) {
                    resultCallback.completed(null);
                }
                if (config.getVerbosity() >= 6) {
                    System.out.println();
                    System.out.println();
                }
            }

            @Override
            public void failed(final Exception cause) {
                stats.incFailureCount();
                final FutureCallback<Void> resultCallback = resultCallbackRef.getAndSet(null);
                if (resultCallback != null) {
                    resultCallback.failed(cause);
                }
                if (config.getVerbosity() >= 1) {
                    System.out.println("HTTP response error: " + cause.getMessage());
                }
            }

            @Override
            public void releaseResources() {
            }

        };
    }

    public void execute() {
        if (requestCount.decrementAndGet() >= 0) {
            AsyncClientEndpoint endpoint = endpointRef.get();
            if (endpoint != null && !endpoint.isConnected()) {
                endpoint.releaseAndDiscard();
                endpoint = null;
            }
            if (endpoint == null) {
                requester.connect(host, config.getSocketTimeout(), null, new FutureCallback<AsyncClientEndpoint>() {

                    @Override
                    public void completed(final AsyncClientEndpoint endpoint) {
                        endpointRef.set(endpoint);
                        endpoint.execute(
                                createRequestProducer(),
                                createResponseConsumer(),
                                context,
                                new FutureCallback<Void>() {

                                    @Override
                                    public void completed(final Void result) {
                                        execute();
                                    }

                                    @Override
                                    public void failed(final Exception cause) {
                                        execute();
                                    }

                                    @Override
                                    public void cancelled() {
                                        completionLatch.countDown();
                                    }

                                });
                    }

                    @Override
                    public void failed(final Exception cause) {
                        stats.incFailureCount();
                        if (config.getVerbosity() >= 1) {
                            System.out.println("Connect error: " + cause.getMessage());
                        }
                        execute();
                    }

                    @Override
                    public void cancelled() {
                        completionLatch.countDown();
                    }

                });
            } else {
                stats.incKeepAliveCount();
                endpoint.execute(
                        createRequestProducer(),
                        createResponseConsumer(),
                        context,
                        new FutureCallback<Void>() {

                            @Override
                            public void completed(final Void result) {
                                execute();
                            }

                            @Override
                            public void failed(final Exception cause) {
                                execute();
                            }

                            @Override
                            public void cancelled() {
                                completionLatch.countDown();
                            }

                        });
            }
        } else {
            completionLatch.countDown();
        }
    }

    @Override
    public void releaseResources() {
        final AsyncClientEndpoint endpoint = endpointRef.getAndSet(null);
        if (endpoint != null) {
            endpoint.releaseAndDiscard();
        }
    }

}

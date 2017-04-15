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
package org.apache.hc.core5.testing.compatibility.http2;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.BasicRequestProducer;
import org.apache.hc.core5.http.nio.BasicResponseConsumer;
import org.apache.hc.core5.http.nio.entity.NoopEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.AbstractAsyncPushHandler;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.testing.nio.ClientSessionEndpoint;
import org.apache.hc.core5.testing.nio.Http2TestClient;
import org.apache.hc.core5.util.TimeValue;

public class Http2CompatibilityTest {

    enum ServerType { DEFAULT, APACHE_HTTPD, NGINX }

    private final HttpHost target;
    private final H2Config h2Config;
    private final Http2TestClient client;

    public static void main(final String... args) throws Exception {

        final HttpHost target = args.length > 0 ? HttpHost.create(args[0]) : new HttpHost("localhost", 8080);
        final ServerType serverType = args.length > 1 ? ServerType.valueOf(args[1].toUpperCase(Locale.ROOT)) : ServerType.DEFAULT;

        final H2Config h2Config;
        switch (serverType) {
            case APACHE_HTTPD:
                h2Config = H2Config.custom()
                        .setPushEnabled(true)
                        // Required for compatibility with Apache HTTPD 2.4
                        .setSettingAckNeeded(false)
                        .build();
                break;
            case NGINX:
                h2Config = H2Config.custom()
                        .setPushEnabled(true)
                        .build();
                break;
             default:
                 h2Config = H2Config.DEFAULT;
        }

        final Http2CompatibilityTest test = new Http2CompatibilityTest(target, h2Config);
        try {
            test.start();
            test.execute();
        } finally {
            test.shutdown();
        }
    }

    Http2CompatibilityTest(final HttpHost target, final H2Config h2Config) throws Exception {
        this.target = target;
        this.h2Config = h2Config;
        this.client = new Http2TestClient(IOReactorConfig.DEFAULT, null);
    }

    void start() throws Exception {
        client.start(h2Config);
    }

    void shutdown() throws Exception {
        client.shutdown(TimeValue.ofSeconds(5));
    }

    private final static String[] REQUEST_URIS = new String[] {"/", "/news.html", "/status.html"};

    void execute() throws Exception {
        final Future<ClientSessionEndpoint> connectFuture = client.connect(target, TimeValue.ofSeconds(5));
        final ClientSessionEndpoint clientEndpoint = connectFuture.get(5, TimeUnit.SECONDS);

        final BlockingDeque<RequestResult> resultQueue = new LinkedBlockingDeque<>();

        if (h2Config.isPushEnabled()) {
            client.register("*", new Supplier<AsyncPushConsumer>() {

                @Override
                public AsyncPushConsumer get() {
                    return new AbstractAsyncPushHandler<Message<HttpResponse, Void>>(new BasicResponseConsumer<>(new NoopEntityConsumer())) {

                        @Override
                        protected void handleResponse(
                                final HttpRequest promise,
                                final Message<HttpResponse, Void> responseMessage) throws IOException, HttpException {
                            resultQueue.add(new RequestResult(promise, responseMessage.getHead(), null));
                        }

                        @Override
                        protected void handleError(
                                final HttpRequest promise,
                                final Exception cause) {
                            resultQueue.add(new RequestResult(promise, null, cause));
                        }
                    };
                }

            });
        }
        for (final String requestUri: REQUEST_URIS) {
            final HttpRequest request = new BasicHttpRequest("GET", target, requestUri);
            clientEndpoint.execute(
                    new BasicRequestProducer(request, null),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                    new FutureCallback<Message<HttpResponse, String>>() {

                        @Override
                        public void completed(final Message<HttpResponse, String> responseMessage) {
                            resultQueue.add(new RequestResult(request, responseMessage.getHead(), null));
                        }

                        @Override
                        public void failed(final Exception ex) {
                            resultQueue.add(new RequestResult(request, null, ex));
                        }

                        @Override
                        public void cancelled() {
                            resultQueue.add(new RequestResult(request, null, new CancellationException()));
                        }

                    });
        }

        String serverInfo = null;
        final Map<String, RequestResult> resultMap = new HashMap<>();
        for (;;) {
            final RequestResult entry = resultQueue.poll(3, TimeUnit.SECONDS);
            if (entry != null) {
                final HttpRequest request = entry.request;
                final HttpResponse response = entry.response;
                if (response != null) {
                    final Header header = response.getFirstHeader(HttpHeaders.SERVER);
                    if (header != null) {
                        serverInfo = header.getValue();
                    }
                }
                resultMap.put(request.getRequestUri(), entry);
            } else {
                break;
            }
        }
        clientEndpoint.close();

        System.out.println("Server info: " + serverInfo);
        for (final String requestUri: REQUEST_URIS) {
            final RequestResult entry = resultMap.remove(requestUri);
            if (entry != null) {
                final HttpResponse response = entry.response;
                final Exception exception = entry.exception;
                if (exception != null) {
                    System.out.println("NOK: " + requestUri + " -> " + exception.getMessage());
                } if (response != null) {
                    System.out.println((response.getCode() == HttpStatus.SC_OK ? "OK: " : "NOK: ") +
                            requestUri + " -> " + response.getCode());
                }
            }
        }
        if (h2Config.isPushEnabled() && resultMap.isEmpty()) {
            System.out.println("NOK: pushed responses expected");
        }

        for (final Iterator<RequestResult> it = resultMap.values().iterator(); it.hasNext(); ) {
            final RequestResult entry = it.next();
            final HttpRequest request = entry.request;
            final HttpResponse response = entry.response;
            final Exception exception = entry.exception;
            if (exception != null) {
                System.out.println("NOK: " + request.getRequestUri() + " (pushed) -> " + exception.getMessage());
            } if (response != null) {
                System.out.println((response.getCode() == HttpStatus.SC_OK ? "OK: " : "NOK: ") +
                        request.getRequestUri() + " (pushed) -> " + response.getCode());
            }
        }
    }

    static class RequestResult {

        final HttpRequest request;
        final HttpResponse response;
        final Exception exception;

        RequestResult(final HttpRequest request, final HttpResponse response, final Exception exception) {
            this.request = request;
            this.response = response;
            this.exception = exception;
        }

    }

}

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
package org.apache.hc.core5.jackson2.http.examles;

import java.net.URI;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.impl.bootstrap.AsyncRequesterBootstrap;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncRequester;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.jackson2.http.JsonObjectEntityProducer;
import org.apache.hc.core5.jackson2.http.JsonResponseConsumers;
import org.apache.hc.core5.jackson2.http.RequestData;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;

public class JsonObjectRequestExample {

    public static void main(final String... args) throws Exception {
        final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setSoTimeout(5, TimeUnit.SECONDS)
                .build();

        // Create and start requester
        final HttpAsyncRequester requester = AsyncRequesterBootstrap.bootstrap()
                .setIOReactorConfig(ioReactorConfig)
                .create();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("HTTP requester shutting down");
            requester.close(CloseMode.GRACEFUL);
        }));
        requester.start();

        final URI uri = URI.create("http://httpbin.org/post");

        System.out.println("Executing POST " + uri);
        final JsonFactory factory = new JsonFactory();
        final ObjectMapper objectMapper = new ObjectMapper(factory);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        final Future<?> future = requester.execute(
                AsyncRequestBuilder.post(uri)
                        .addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.toString())
                        .setEntity(new JsonObjectEntityProducer<>(
                                new BasicNameValuePair("name", "value"),
                                objectMapper))
                        .build(),
                JsonResponseConsumers.create(objectMapper, RequestData.class),
                Timeout.ofMinutes(1),
                new FutureCallback<Message<HttpResponse, RequestData>>() {

                    @Override
                    public void completed(final Message<HttpResponse, RequestData> message) {
                        System.out.println("Response status: " + message.getHead().getCode());
                        System.out.println(message.getBody());
                    }

                    @Override
                    public void failed(final Exception ex) {
                        ex.printStackTrace(System.out);
                    }

                    @Override
                    public void cancelled() {
                    }

                });
        future.get();

        System.out.println("Shutting down I/O reactor");
        requester.initiateShutdown();
    }

}

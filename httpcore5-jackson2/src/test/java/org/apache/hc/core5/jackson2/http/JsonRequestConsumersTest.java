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
package org.apache.hc.core5.jackson2.http;

import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.UnsupportedMediaTypeException;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class JsonRequestConsumersTest {

    @Test
    void testRequestJsonContentCorrectlyProcessed() throws Exception {
        final ObjectMapper objectMapper = new ObjectMapper();

        final URL resource = getClass().getResource("/sample1.json");
        Assertions.assertThat(resource).isNotNull();

        final BasicHttpRequest request = BasicRequestBuilder.post().build();

        final AsyncRequestConsumer<Message<HttpRequest, RequestData>> requestConsumer = new JsonRequestConsumer<>(
                () -> new JsonObjectEntityConsumer<>(objectMapper, RequestData.class));
        final AtomicReference<Message<HttpRequest, RequestData>> resultRef = new AtomicReference<>();
        try (final InputStream inputStream = resource.openStream()) {
            requestConsumer.consumeRequest(
                    request,
                    new BasicEntityDetails(-1, ContentType.APPLICATION_JSON),
                    HttpCoreContext.create(),
                    new FutureCallback<Message<HttpRequest, RequestData>>() {

                        @Override
                        public void completed(final Message<HttpRequest, RequestData> result) {
                            resultRef.set(result);
                        }

                        @Override
                        public void failed(final Exception ex) {
                        }

                        @Override
                        public void cancelled() {
                        }

                    });
            final byte[] bytebuf = new byte[1024];
            int len;
            while ((len = inputStream.read(bytebuf)) != -1) {
                requestConsumer.consume(ByteBuffer.wrap(bytebuf, 0, len));
            }
            requestConsumer.streamEnd(null);
        }

        Assertions.assertThat(resultRef.get()).isNotNull().satisfies(e -> {
            Assertions.assertThat(e.head()).isSameAs(request);
            Assertions.assertThat(e.body()).isNotNull();
            Assertions.assertThat(e.error()).isNull();
        });
    }

    @Test
    void testRequestWrongContentTypeThrowsException() throws Exception {
        final ObjectMapper objectMapper = new ObjectMapper();

        final BasicHttpRequest request = BasicRequestBuilder.post().build();

        final AsyncRequestConsumer<Message<HttpRequest, RequestData>> requestConsumer = new JsonRequestConsumer<>(
                () -> new JsonObjectEntityConsumer<>(objectMapper, RequestData.class));
        final AtomicReference<Exception> resultRef = new AtomicReference<>();
        requestConsumer.consumeRequest(
                request,
                new BasicEntityDetails(-1, ContentType.TEXT_PLAIN),
                HttpCoreContext.create(),
                new FutureCallback<Message<HttpRequest, RequestData>>() {

                    @Override
                    public void completed(final Message<HttpRequest, RequestData> result) {
                    }

                    @Override
                    public void failed(final Exception ex) {
                        resultRef.set(ex);
                    }

                    @Override
                    public void cancelled() {
                    }

                });
        requestConsumer.consume(ByteBuffer.wrap("This is just plain text".getBytes(StandardCharsets.UTF_8)));
        requestConsumer.streamEnd(null);

        Assertions.assertThat(resultRef.get()).isNotNull().isInstanceOf(UnsupportedMediaTypeException.class)
                .hasMessage("Unsupported media type: text/plain");
    }

    @Test
    void testRequestJsonSequenceContentCorrectlyProcessed() throws Exception {
        final ObjectMapper objectMapper = new ObjectMapper();

        final URL resource = getClass().getResource("/sample3.json");
        Assertions.assertThat(resource).isNotNull();

        final BasicHttpRequest request = BasicRequestBuilder.post().build();

        final AtomicReference<HttpRequest> messageRef = new AtomicReference<>();
        final List<RequestData> resultList = new LinkedList<>();
        final AtomicReference<Long> resultRef = new AtomicReference<>();
        final JsonSequenceRequestConsumer<Long> requestConsumer = new JsonSequenceRequestConsumer<>(
                () -> new JsonSequenceEntityConsumer<>(
                        objectMapper,
                        RequestData.class,
                        resultList::add),
                messageRef::set);
        try (final InputStream inputStream = resource.openStream()) {
            requestConsumer.consumeRequest(
                    request,
                    new BasicEntityDetails(-1, ContentType.APPLICATION_JSON),
                    HttpCoreContext.create(),
                    new FutureCallback<Long>() {

                        @Override
                        public void completed(final Long result) {
                            resultRef.set(result);
                        }

                        @Override
                        public void failed(final Exception ex) {
                        }

                        @Override
                        public void cancelled() {
                        }

                    });
            final byte[] bytebuf = new byte[1024];
            int len;
            while ((len = inputStream.read(bytebuf)) != -1) {
                requestConsumer.consume(ByteBuffer.wrap(bytebuf, 0, len));
            }
            requestConsumer.streamEnd(null);
        }

        Assertions.assertThat(messageRef.get()).isSameAs(request);
        Assertions.assertThat(resultList).hasSize(3);
        Assertions.assertThat(resultRef.get()).isEqualTo(3L);
    }

    @Test
    void testRequestJsonSequenceWrongContentTypeThrowsException() throws Exception {
        final ObjectMapper objectMapper = new ObjectMapper();

        final BasicHttpRequest request = BasicRequestBuilder.post().build();

        final AtomicReference<HttpRequest> messageRef = new AtomicReference<>();
        final List<RequestData> resultList = new LinkedList<>();
        final AtomicReference<Exception> resultRef = new AtomicReference<>();
        final JsonSequenceRequestConsumer<Long> requestConsumer = new JsonSequenceRequestConsumer<>(
                () -> new JsonSequenceEntityConsumer<>(
                        objectMapper,
                        RequestData.class,
                        resultList::add),
                messageRef::set);
        requestConsumer.consumeRequest(
                request,
                new BasicEntityDetails(-1, ContentType.TEXT_PLAIN),
                HttpCoreContext.create(),
                new FutureCallback<Long>() {

                    @Override
                    public void completed(final Long result) {
                    }

                    @Override
                    public void failed(final Exception ex) {
                        resultRef.set(ex);
                    }

                    @Override
                    public void cancelled() {
                    }

                });
        requestConsumer.consume(ByteBuffer.wrap("This is just plain text".getBytes(StandardCharsets.UTF_8)));
        requestConsumer.streamEnd(null);

        Assertions.assertThat(messageRef.get()).isSameAs(request);
        Assertions.assertThat(resultList).isEmpty();
        Assertions.assertThat(resultRef.get()).isNotNull().isInstanceOf(UnsupportedMediaTypeException.class)
                .hasMessage("Unsupported media type: text/plain");
    }

}

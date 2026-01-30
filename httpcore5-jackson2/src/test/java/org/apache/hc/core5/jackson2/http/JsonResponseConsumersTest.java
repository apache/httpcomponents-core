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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.support.BasicResponseBuilder;
import org.apache.hc.core5.jackson2.JsonContentException;
import org.apache.hc.core5.jackson2.JsonTokenConsumer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class JsonResponseConsumersTest {

    ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void testResponseJsonContentCorrectlyProcessed() throws Exception {
        final URL resource = getClass().getResource("/sample1.json");
        Assertions.assertThat(resource).isNotNull();

        final BasicHttpResponse response = BasicResponseBuilder.create(200).build();

        final AsyncResponseConsumer<Message<HttpResponse, RequestData>> responseConsumer = new JsonResponseConsumer<>(
                () -> new JsonObjectEntityConsumer<>(objectMapper, RequestData.class),
                StringAsyncEntityConsumer::new);
        final AtomicReference<Message<HttpResponse, RequestData>> resultRef = new AtomicReference<>();
        try (final InputStream inputStream = resource.openStream()) {
            responseConsumer.consumeResponse(
                    response,
                    new BasicEntityDetails(-1, ContentType.APPLICATION_JSON),
                    HttpCoreContext.create(),
                    new FutureCallback<Message<HttpResponse, RequestData>>() {

                        @Override
                        public void completed(final Message<HttpResponse, RequestData> result) {
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
                responseConsumer.consume(ByteBuffer.wrap(bytebuf, 0, len));
            }
            responseConsumer.streamEnd(null);
        }

        Assertions.assertThat(resultRef.get()).isNotNull().satisfies(e -> {
            Assertions.assertThat(e.getHead()).isSameAs(response);
            Assertions.assertThat(e.getBody()).isNotNull();
            Assertions.assertThat(e.error()).isNull();
        });
    }

    @Test
    void testResponseWrongContentTypeThrowsException() throws Exception {
        final BasicHttpResponse response = BasicResponseBuilder.create(200).build();

        final AsyncResponseConsumer<Message<HttpResponse, RequestData>> responseConsumer = new JsonResponseConsumer<>(
                () -> new JsonObjectEntityConsumer<>(objectMapper, RequestData.class),
                StringAsyncEntityConsumer::new);
        final AtomicReference<Exception> resultRef = new AtomicReference<>();
        responseConsumer.consumeResponse(
                response,
                new BasicEntityDetails(-1, ContentType.TEXT_PLAIN),
                HttpCoreContext.create(),
                new FutureCallback<Message<HttpResponse, RequestData>>() {

                    @Override
                    public void completed(final Message<HttpResponse, RequestData> result) {
                    }

                    @Override
                    public void failed(final Exception ex) {
                        resultRef.set(ex);
                    }

                    @Override
                    public void cancelled() {
                    }

                });
        responseConsumer.consume(ByteBuffer.wrap("This is just plain text".getBytes(StandardCharsets.UTF_8)));
        responseConsumer.streamEnd(null);

        Assertions.assertThat(resultRef.get()).isNotNull().isInstanceOf(JsonContentException.class).hasMessage("Unexpected content type: text/plain");
    }

    @Test
    void testResponseJsonSequenceContentCorrectlyProcessed() throws Exception {
        final URL resource = getClass().getResource("/sample3.json");
        Assertions.assertThat(resource).isNotNull();

        final BasicHttpResponse response = BasicResponseBuilder.create(200).build();

        final AtomicReference<HttpResponse> messageRef = new AtomicReference<>();
        final AtomicReference<String> errorRef = new AtomicReference<>();
        final List<RequestData> resultList = new LinkedList<>();
        final AtomicReference<Long> resultRef = new AtomicReference<>();
        final JsonSequenceResponseConsumer<Long, String> responseConsumer = new JsonSequenceResponseConsumer<>(
                () -> new JsonSequenceEntityConsumer<>(
                        objectMapper,
                        RequestData.class,
                        resultList::add),
                StringAsyncEntityConsumer::new,
                messageRef::set,
                errorRef::set);
        try (final InputStream inputStream = resource.openStream()) {
            responseConsumer.consumeResponse(
                    response,
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
                responseConsumer.consume(ByteBuffer.wrap(bytebuf, 0, len));
            }
            responseConsumer.streamEnd(null);
        }

        Assertions.assertThat(messageRef.get()).isSameAs(response);
        Assertions.assertThat(resultList).hasSize(3);
        Assertions.assertThat(resultRef.get()).isEqualTo(3L);
    }

    @Test
    void testResponseJsonSequenceWrongContentTypeThrowsException() throws Exception {
        final BasicHttpResponse response = BasicResponseBuilder.create(200).build();

        final AtomicReference<HttpResponse> messageRef = new AtomicReference<>();
        final AtomicReference<String> errorRef = new AtomicReference<>();
        final List<RequestData> resultList = new LinkedList<>();
        final AtomicReference<Exception> resultRef = new AtomicReference<>();
        final JsonSequenceResponseConsumer<Long, String> responseConsumer = new JsonSequenceResponseConsumer<>(
                () -> new JsonSequenceEntityConsumer<>(
                        objectMapper,
                        RequestData.class,
                        resultList::add),
                StringAsyncEntityConsumer::new,
                messageRef::set,
                errorRef::set);
        responseConsumer.consumeResponse(
                response,
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
        responseConsumer.consume(ByteBuffer.wrap("This is just plain text".getBytes(StandardCharsets.UTF_8)));
        responseConsumer.streamEnd(null);

        Assertions.assertThat(messageRef.get()).isSameAs(response);
        Assertions.assertThat(resultList).isEmpty();
        Assertions.assertThat(resultRef.get()).isNotNull().isInstanceOf(JsonContentException.class).hasMessage("Unexpected content type: text/plain");
    }

    @Test
    void testErrorResponseNonJsonContentMappedAsError() throws Exception {
        final String errorBody = "Unexpected internal failure";

        final BasicHttpResponse response = BasicResponseBuilder.create(500).build();

        final AsyncResponseConsumer<Message<HttpResponse, RequestData>> responseConsumer = new JsonResponseConsumer<>(
                () -> new JsonObjectEntityConsumer<>(objectMapper, RequestData.class),
                StringAsyncEntityConsumer::new);
        final AtomicReference<Message<HttpResponse, RequestData>> resultRef = new AtomicReference<>();

        responseConsumer.consumeResponse(
                response,
                new BasicEntityDetails(errorBody.length(), ContentType.TEXT_PLAIN),
                HttpCoreContext.create(),
                new FutureCallback<Message<HttpResponse, RequestData>>() {
                    @Override
                    public void completed(final Message<HttpResponse, RequestData> result) {
                        resultRef.set(result);
                    }

                    @Override
                    public void failed(final Exception ex) {
                    }

                    @Override
                    public void cancelled() {
                    }

                });
        responseConsumer.consume(ByteBuffer.wrap(errorBody.getBytes(StandardCharsets.UTF_8)));
        responseConsumer.streamEnd(null);

        Assertions.assertThat(resultRef.get()).isNotNull().satisfies(e -> {
            Assertions.assertThat(e.getHead()).isSameAs(response);
            Assertions.assertThat(e.getBody()).isNull();
            Assertions.assertThat(e.error()).isEqualTo(errorBody);
        });
    }

    @Test
    void testErrorResponseJsonContentMappedAsError() throws Exception {
        final String errorBody = "{\"code\": 500, \"message\": \"Unexpected internal failure\"}";

        final BasicHttpResponse response = BasicResponseBuilder.create(500).build();

        final AsyncResponseConsumer<Message<HttpResponse, RequestData>> responseConsumer = new JsonResponseConsumer<>(
                () -> new JsonObjectEntityConsumer<>(objectMapper, RequestData.class),
                StringAsyncEntityConsumer::new);
        final AtomicReference<Message<HttpResponse, RequestData>> resultRef = new AtomicReference<>();

        responseConsumer.consumeResponse(
                response,
                new BasicEntityDetails(errorBody.length(), ContentType.APPLICATION_JSON),
                HttpCoreContext.create(),
                new FutureCallback<Message<HttpResponse, RequestData>>() {
                    @Override
                    public void completed(final Message<HttpResponse, RequestData> result) {
                        resultRef.set(result);
                    }

                    @Override
                    public void failed(final Exception ex) {
                    }

                    @Override
                    public void cancelled() {
                    }

                });
        responseConsumer.consume(ByteBuffer.wrap(errorBody.getBytes(StandardCharsets.UTF_8)));
        responseConsumer.streamEnd(null);

        Assertions.assertThat(resultRef.get()).isNotNull().satisfies(e -> {
            Assertions.assertThat(e.getHead()).isSameAs(response);
            Assertions.assertThat(e.getBody()).isNull();
            Assertions.assertThat(e.error()).isEqualTo(errorBody);
        });
    }

    @Test
    void testResponseJsonTokenContentCorrectlyProcessed() throws Exception {
        final JsonFactory jsonFactory = objectMapper.getFactory();
        final JsonTokenConsumer mockJsonTokenConsumer = Mockito.mock(JsonTokenConsumer.class);

        final AsyncResponseConsumer<Void> responseConsumer = new JsonSequenceResponseConsumer<>(
                () -> new JsonTokenEntityConsumer(jsonFactory, mockJsonTokenConsumer),
                StringAsyncEntityConsumer::new,
                response -> {
                },
                error -> {
                });

        final BasicHttpResponse response = BasicResponseBuilder.create(200).build();

        responseConsumer.consumeResponse(
                response,
                new BasicEntityDetails(-1, ContentType.APPLICATION_JSON),
                HttpCoreContext.create(),
                null);
        final ByteBuffer data = ByteBuffer.wrap("{\"foo\":\"bar\"}".getBytes(StandardCharsets.UTF_8));
        responseConsumer.consume(data);
        responseConsumer.streamEnd(Collections.emptyList());
        Mockito.verify(mockJsonTokenConsumer).accept(
                Mockito.eq(JsonTokenId.ID_START_OBJECT), Mockito.any(JsonParser.class));
        Mockito.verify(mockJsonTokenConsumer).accept(
                Mockito.eq(JsonTokenId.ID_FIELD_NAME), Mockito.any(JsonParser.class));
        Mockito.verify(mockJsonTokenConsumer).accept(
                Mockito.eq(JsonTokenId.ID_STRING), Mockito.any(JsonParser.class));
        Mockito.verify(mockJsonTokenConsumer).accept(
                Mockito.eq(JsonTokenId.ID_END_OBJECT), Mockito.any(JsonParser.class));
        Mockito.verify(mockJsonTokenConsumer).accept(
                Mockito.eq(JsonTokenId.ID_NO_TOKEN), Mockito.any(JsonParser.class));
        Mockito.verifyNoMoreInteractions(mockJsonTokenConsumer);
    }

}

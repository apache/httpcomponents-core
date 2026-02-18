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
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.UnsupportedMediaTypeException;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.jackson2.JsonResultSink;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;

public class JsonEntityConsumersTest {

    @Test
    void testJsonNodeCorrectlyProcessed() throws Exception {
        final JsonFactory factory = new JsonFactory();

        final URL resource = getClass().getResource("/sample1.json");
        Assertions.assertThat(resource).isNotNull();

        final JsonNodeEntityConsumer entityConsumer = new JsonNodeEntityConsumer(factory);
        final AtomicReference<JsonNode> resultRef = new AtomicReference<>();
        try (final InputStream inputStream = resource.openStream()) {
            entityConsumer.streamStart(
                    new BasicEntityDetails(-1, ContentType.APPLICATION_JSON),
                    new FutureCallback<JsonNode>() {

                        @Override
                        public void completed(final JsonNode result) {
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
                entityConsumer.consume(ByteBuffer.wrap(bytebuf, 0, len));
            }
            entityConsumer.streamEnd(null);
        }

        final ObjectNode expectedObject = JsonNodeFactory.instance.objectNode();
        expectedObject.putObject("args");
        expectedObject.putObject("headers")
                .put("Accept", "application/json")
                .put("Accept-Encoding", "gzip, deflate")
                .put("Accept-Language", "en-US,en;q=0.9")
                .put("Connection", "close")
                .put("Cookie", "_gauges_unique_year=1; _gauges_unique=1; _gauges_unique_hour=1; " +
                        "_gauges_unique_day=1; _gauges_unique_month=1")
                .put("Host", "httpbin.org")
                .put("Referer", "http://httpbin.org/")
                .put("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "snap Chromium/71.0.3578.98 Chrome/71.0.3578.98 Safari/537.36");
        expectedObject.put("origin", "xxx.xxx.xxx.xxx");
        expectedObject.put("url", "http://httpbin.org/get");

        Assertions.assertThat(resultRef.get()).isEqualTo(expectedObject);
    }

    @Test
    void testWrongContentTypeThrowsException() throws Exception {
        final JsonFactory factory = new JsonFactory();

        final JsonNodeEntityConsumer entityConsumer = new JsonNodeEntityConsumer(factory);
        Assertions.assertThatThrownBy(() -> entityConsumer.streamStart(
                        new BasicEntityDetails(-1, ContentType.TEXT_PLAIN),
                        null)).isInstanceOf(UnsupportedMediaTypeException.class)
                .hasMessage("Unsupported media type: text/plain");
    }

    @Test
    void testJsonObjectEntityCorrectlyProcessed() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final ObjectMapper objectMapper = new ObjectMapper(factory);

        final URL resource = getClass().getResource("/sample1.json");
        Assertions.assertThat(resource).isNotNull();

        final AtomicReference<RequestData> resultRef = new AtomicReference<>();
        final JsonObjectEntityConsumer<RequestData> entityConsumer = new JsonObjectEntityConsumer<>(objectMapper, RequestData.class);
        try (final InputStream inputStream = resource.openStream()) {
            entityConsumer.streamStart(
                    new BasicEntityDetails(-1, ContentType.APPLICATION_JSON),
                    new FutureCallback<RequestData>() {

                        @Override
                        public void completed(final RequestData result) {
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
                entityConsumer.consume(ByteBuffer.wrap(bytebuf, 0, len));
            }
            entityConsumer.streamEnd(null);
        }

        final RequestData expectedObject = new RequestData();
        expectedObject.setArgs(new HashMap<>());
        expectedObject.generateHeaders(
                new BasicHeader("Accept", "application/json"),
                new BasicHeader("Accept-Encoding", "gzip, deflate"),
                new BasicHeader("Accept-Language", "en-US,en;q=0.9"),
                new BasicHeader("Connection", "close"),
                new BasicHeader("Cookie", "_gauges_unique_year=1; _gauges_unique=1; _gauges_unique_hour=1; " +
                        "_gauges_unique_day=1; _gauges_unique_month=1"),
                new BasicHeader("Host", "httpbin.org"),
                new BasicHeader("Referer", "http://httpbin.org/"),
                new BasicHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "snap Chromium/71.0.3578.98 Chrome/71.0.3578.98 Safari/537.36"));
        expectedObject.setOrigin("xxx.xxx.xxx.xxx");
        expectedObject.setUrl(URI.create("http://httpbin.org/get"));

        Assertions.assertThat(resultRef.get()).usingRecursiveComparison().isEqualTo(expectedObject);
    }

    @Test
    void testJsonTypeReferenceCorrectlyProcessed() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final ObjectMapper objectMapper = new ObjectMapper(factory);

        final URL resource = getClass().getResource("/sample4.json");
        Assertions.assertThat(resource).isNotNull();

        final AtomicReference<List<String>> resultRef = new AtomicReference<>();
        final JsonObjectEntityConsumer<List<String>> entityConsumer = new JsonObjectEntityConsumer<>(objectMapper, new TypeReference<List<String>>() {
        });
        try (final InputStream inputStream = resource.openStream()) {
            entityConsumer.streamStart(
                    new BasicEntityDetails(-1, ContentType.APPLICATION_JSON),
                    new FutureCallback<List<String>>() {

                        @Override
                        public void completed(final List<String> result) {
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
                entityConsumer.consume(ByteBuffer.wrap(bytebuf, 0, len));
            }
            entityConsumer.streamEnd(null);
        }

        Assertions.assertThat(resultRef.get()).containsExactly("1", "2", "3", "4");
    }

    @Test
    void testJsonSequenceCorrectlyProcessed() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final ObjectMapper objectMapper = new ObjectMapper(factory);

        final URL resource = getClass().getResource("/sample3.json");
        Assertions.assertThat(resource).isNotNull();

        final AtomicReference<Long> resultRef = new AtomicReference<>();
        final AtomicInteger started = new AtomicInteger(0);
        final List<RequestData> jsonDataList = new ArrayList<>();
        final AtomicInteger ended = new AtomicInteger(0);
        final JsonSequenceEntityConsumer<RequestData> entityConsumer = new JsonSequenceEntityConsumer<>(
                objectMapper,
                RequestData.class,
                new JsonResultSink<RequestData>() {

                    @Override
                    public void begin(final int sizeHint) {
                        started.incrementAndGet();
                    }

                    @Override
                    public void accept(final RequestData data) {
                        jsonDataList.add(data);
                    }

                    @Override
                    public void end() {
                        ended.incrementAndGet();
                    }

                });
        try (final InputStream inputStream = resource.openStream()) {
            entityConsumer.streamStart(
                    new BasicEntityDetails(-1, ContentType.APPLICATION_JSON),
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
                entityConsumer.consume(ByteBuffer.wrap(bytebuf, 0, len));
            }
            entityConsumer.streamEnd(null);
        }

        Assertions.assertThat(resultRef.get()).isEqualTo(3L);

        Assertions.assertThat(jsonDataList).hasSize(3);
        Assertions.assertThat(started.get()).isEqualTo(1);
        Assertions.assertThat(ended.get()).isEqualTo(1);


        final RequestData expectedObject1 = new RequestData();
        expectedObject1.setId(0);
        expectedObject1.setUrl(URI.create("http://httpbin.org/stream/3"));
        expectedObject1.setArgs(new HashMap<>());
        expectedObject1.generateHeaders(
                new BasicHeader("Host", "httpbin.org"),
                new BasicHeader("Connection", "close"),
                new BasicHeader("Referer", "http://httpbin.org/"),
                new BasicHeader("Accept", "application/json"),
                new BasicHeader("Accept-Encoding", "gzip, deflate"),
                new BasicHeader("Accept-Language", "en-US,en;q=0.9"),
                new BasicHeader("Cookie", "_gauges_unique_year=1; _gauges_unique=1; _gauges_unique_month=1; " +
                        "_gauges_unique_day=1; _gauges_unique_hour=1"),
                new BasicHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "snap Chromium/71.0.3578.98 Chrome/71.0.3578.98 Safari/537.36"));
        expectedObject1.setOrigin("xxx.xxx.xxx.xxx");

        Assertions.assertThat(jsonDataList.get(0)).usingRecursiveComparison().isEqualTo(expectedObject1);

        final RequestData expectedObject2 = new RequestData();
        expectedObject2.setId(1);
        expectedObject2.setUrl(URI.create("http://httpbin.org/stream/3"));
        expectedObject2.setArgs(new HashMap<>());
        expectedObject2.generateHeaders(
                new BasicHeader("Host", "httpbin.org"),
                new BasicHeader("Connection", "close"),
                new BasicHeader("Referer", "http://httpbin.org/"),
                new BasicHeader("Accept", "application/json"),
                new BasicHeader("Accept-Encoding", "gzip, deflate"),
                new BasicHeader("Accept-Language", "en-US,en;q=0.9"),
                new BasicHeader("Cookie", "_gauges_unique_year=1; _gauges_unique=1; _gauges_unique_month=1; " +
                        "_gauges_unique_day=1; _gauges_unique_hour=1"),
                new BasicHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "snap Chromium/71.0.3578.98 Chrome/71.0.3578.98 Safari/537.36"));
        expectedObject2.setOrigin("xxx.xxx.xxx.xxx");

        Assertions.assertThat(jsonDataList.get(1)).usingRecursiveComparison().isEqualTo(expectedObject2);

        final RequestData expectedObject3 = new RequestData();
        expectedObject3.setId(2);
        expectedObject3.setUrl(URI.create("http://httpbin.org/stream/3"));
        expectedObject3.setArgs(new HashMap<>());
        expectedObject3.generateHeaders(
                new BasicHeader("Host", "httpbin.org"),
                new BasicHeader("Connection", "close"),
                new BasicHeader("Referer", "http://httpbin.org/"),
                new BasicHeader("Accept", "application/json"),
                new BasicHeader("Accept-Encoding", "gzip, deflate"),
                new BasicHeader("Accept-Language", "en-US,en;q=0.9"),
                new BasicHeader("Cookie", "_gauges_unique_year=1; _gauges_unique=1; _gauges_unique_month=1; " +
                        "_gauges_unique_day=1; _gauges_unique_hour=1"),
                new BasicHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "snap Chromium/71.0.3578.98 Chrome/71.0.3578.98 Safari/537.36"));
        expectedObject3.setOrigin("xxx.xxx.xxx.xxx");

        Assertions.assertThat(jsonDataList.get(2)).usingRecursiveComparison().isEqualTo(expectedObject3);
    }

    @Test
    void testJsonTypeReferenceSequenceCorrectlyProcessed() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final ObjectMapper objectMapper = new ObjectMapper(factory);

        final URL resource = getClass().getResource("/sample5.json");
        Assertions.assertThat(resource).isNotNull();

        final AtomicReference<Long> resultRef = new AtomicReference<>();
        final AtomicInteger started = new AtomicInteger(0);
        final List<List<String>> jsonDataList = new ArrayList<>();
        final AtomicInteger ended = new AtomicInteger(0);
        final JsonSequenceEntityConsumer<List<String>> entityConsumer = new JsonSequenceEntityConsumer<>(
                objectMapper,
                new TypeReference<List<String>>() {
                },
                new JsonResultSink<List<String>>() {

                    @Override
                    public void begin(final int sizeHint) {
                        started.incrementAndGet();
                    }

                    @Override
                    public void accept(final List<String> data) {
                        jsonDataList.add(data);
                    }

                    @Override
                    public void end() {
                        ended.incrementAndGet();
                    }

                });
        try (final InputStream inputStream = resource.openStream()) {
            entityConsumer.streamStart(
                    new BasicEntityDetails(-1, ContentType.APPLICATION_JSON),
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
                entityConsumer.consume(ByteBuffer.wrap(bytebuf, 0, len));
            }
            entityConsumer.streamEnd(null);
        }

        Assertions.assertThat(resultRef.get()).isEqualTo(3L);

        Assertions.assertThat(jsonDataList).hasSize(3);
        Assertions.assertThat(started.get()).isEqualTo(1);
        Assertions.assertThat(ended.get()).isEqualTo(1);

        Assertions.assertThat(jsonDataList.get(0)).containsExactly("1", "2", "3", "4");
        Assertions.assertThat(jsonDataList.get(1)).containsExactly("5", "6", "7", "8");
        Assertions.assertThat(jsonDataList.get(2)).containsExactly("9", "10", "11", "12");
    }

    @Test
    void testJsonNodeSequenceCorrectlyProcessed() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final ObjectMapper objectMapper = new ObjectMapper(factory);

        final URL resource = getClass().getResource("/sample7.json");
        Assertions.assertThat(resource).isNotNull();

        final AtomicReference<Long> resultRef = new AtomicReference<>();
        final AtomicInteger started = new AtomicInteger(0);
        final List<JsonNode> jsonDataList = new ArrayList<>();
        final AtomicInteger ended = new AtomicInteger(0);
        final JsonNodeSequenceEntityConsumer entityConsumer = new JsonNodeSequenceEntityConsumer(
                objectMapper,
                new JsonResultSink<JsonNode>() {

                    @Override
                    public void begin(final int sizeHint) {
                        started.incrementAndGet();
                    }

                    @Override
                    public void accept(final JsonNode data) {
                        jsonDataList.add(data);
                    }

                    @Override
                    public void end() {
                        ended.incrementAndGet();
                    }

                });
        try (final InputStream inputStream = resource.openStream()) {
            entityConsumer.streamStart(
                    new BasicEntityDetails(-1, ContentType.APPLICATION_JSON),
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
                entityConsumer.consume(ByteBuffer.wrap(bytebuf, 0, len));
            }
            entityConsumer.streamEnd(null);
        }

        Assertions.assertThat(resultRef.get()).isEqualTo(5L);
        Assertions.assertThat(jsonDataList).hasSize(5);

        final ObjectNode expectedObject1 = JsonNodeFactory.instance.objectNode();
        expectedObject1.putObject("args");
        expectedObject1.putObject("headers")
                .put("Accept", "application/json")
                .put("Accept-Encoding", "gzip, deflate")
                .put("Accept-Language", "en-US,en;q=0.9")
                .put("Connection", "close")
                .put("Cookie", "_gauges_unique_year=1; _gauges_unique=1; _gauges_unique_hour=1; " +
                        "_gauges_unique_day=1; _gauges_unique_month=1")
                .put("Host", "httpbin.org")
                .put("Referer", "http://httpbin.org/")
                .put("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "snap Chromium/71.0.3578.98 Chrome/71.0.3578.98 Safari/537.36");
        expectedObject1.put("origin", "xxx.xxx.xxx.xxx");
        expectedObject1.put("url", "http://httpbin.org/get");

        Assertions.assertThat(jsonDataList.get(0)).isEqualTo(expectedObject1);

        final ObjectNode expectedObject2 = JsonNodeFactory.instance.objectNode();
        expectedObject2.put("value", "Some text");

        Assertions.assertThat(jsonDataList.get(1)).isEqualTo(expectedObject2);

        final ArrayNode expectedObject3 = JsonNodeFactory.instance.arrayNode();
        expectedObject3.add("1").add("2").add("3");

        Assertions.assertThat(jsonDataList.get(2)).isEqualTo(expectedObject3);

        final ArrayNode expectedObject4 = JsonNodeFactory.instance.arrayNode();
        expectedObject4.add(1).add(2).add(3);

        Assertions.assertThat(jsonDataList.get(3)).isEqualTo(expectedObject4);

        final ArrayNode expectedObject5 = JsonNodeFactory.instance.arrayNode();
        expectedObject5.addObject()
                .put("value", "Some text");
        expectedObject5.addObject()
                .put("value", "Some other text");

        Assertions.assertThat(jsonDataList.get(4)).isEqualTo(expectedObject5);
    }

    @Test
    void testJsonTokenCorrectlyProcessed() throws Exception {
        final JsonFactory factory = new JsonFactory();

        final URL resource = getClass().getResource("/sample1.json");
        Assertions.assertThat(resource).isNotNull();

        final List<Integer> tokenIds = new LinkedList<>();
        final JsonTokenEntityConsumer entityConsumer = new JsonTokenEntityConsumer(factory,
                (tokenId, jsonParser) -> tokenIds.add(tokenId));
        try (final InputStream inputStream = resource.openStream()) {
            entityConsumer.streamStart(
                    new BasicEntityDetails(-1, ContentType.APPLICATION_JSON),
                    null);
            final byte[] bytebuf = new byte[1024];
            int len;
            while ((len = inputStream.read(bytebuf)) != -1) {
                entityConsumer.consume(ByteBuffer.wrap(bytebuf, 0, len));
            }
            entityConsumer.streamEnd(null);
        }
        Assertions.assertThat(tokenIds).containsExactly(
                JsonTokenId.ID_START_OBJECT,
                JsonTokenId.ID_FIELD_NAME,
                JsonTokenId.ID_START_OBJECT,
                JsonTokenId.ID_END_OBJECT,
                JsonTokenId.ID_FIELD_NAME,
                JsonTokenId.ID_START_OBJECT,
                JsonTokenId.ID_FIELD_NAME,
                JsonTokenId.ID_STRING,
                JsonTokenId.ID_FIELD_NAME,
                JsonTokenId.ID_STRING,
                JsonTokenId.ID_FIELD_NAME,
                JsonTokenId.ID_STRING,
                JsonTokenId.ID_FIELD_NAME,
                JsonTokenId.ID_STRING,
                JsonTokenId.ID_FIELD_NAME,
                JsonTokenId.ID_STRING,
                JsonTokenId.ID_FIELD_NAME,
                JsonTokenId.ID_STRING,
                JsonTokenId.ID_FIELD_NAME,
                JsonTokenId.ID_STRING,
                JsonTokenId.ID_FIELD_NAME,
                JsonTokenId.ID_STRING,
                JsonTokenId.ID_END_OBJECT,
                JsonTokenId.ID_FIELD_NAME,
                JsonTokenId.ID_STRING,
                JsonTokenId.ID_FIELD_NAME,
                JsonTokenId.ID_STRING,
                JsonTokenId.ID_END_OBJECT,
                JsonTokenId.ID_NO_TOKEN
        );
    }

    @Test
    void testJsonNodeWithFallbackCorrectlyProcessed() throws Exception {
        final JsonFactory factory = new JsonFactory();

        final URL resource = getClass().getResource("/sample1.json");
        Assertions.assertThat(resource).isNotNull();

        final JsonNodeEntityFallbackConsumer entityConsumer = new JsonNodeEntityFallbackConsumer(factory);
        final AtomicReference<JsonNode> resultRef = new AtomicReference<>();
        try (final InputStream inputStream = resource.openStream()) {
            entityConsumer.streamStart(
                    new BasicEntityDetails(-1, ContentType.APPLICATION_JSON),
                    new FutureCallback<JsonNode>() {

                        @Override
                        public void completed(final JsonNode result) {
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
                entityConsumer.consume(ByteBuffer.wrap(bytebuf, 0, len));
            }
            entityConsumer.streamEnd(null);
        }

        Assertions.assertThat(resultRef.get()).isInstanceOf(JsonNode.class);
    }

    @Test
    void testJsonNodeWithFallbackUnexpectedContentProcessed() throws Exception {
        final JsonFactory factory = new JsonFactory();

        final JsonNodeEntityFallbackConsumer entityConsumer = new JsonNodeEntityFallbackConsumer(factory);
        final AtomicReference<JsonNode> resultRef = new AtomicReference<>();
        entityConsumer.streamStart(
                new BasicEntityDetails(-1, ContentType.TEXT_PLAIN),
                new FutureCallback<JsonNode>() {

                    @Override
                    public void completed(final JsonNode result) {
                        resultRef.set(result);
                    }

                    @Override
                    public void failed(final Exception ex) {
                    }

                    @Override
                    public void cancelled() {
                    }

                });
        entityConsumer.consume(ByteBuffer.wrap("Kaaaaaaa".getBytes(StandardCharsets.UTF_8)));
        entityConsumer.consume(ByteBuffer.wrap("Boooooom".getBytes(StandardCharsets.UTF_8)));
        entityConsumer.streamEnd(null);

        AssertionsForClassTypes.assertThat(resultRef.get()).satisfies(e -> {
            Assertions.assertThat(e.isTextual()).isTrue();
            Assertions.assertThat(e.asText()).isEqualTo("KaaaaaaaBoooooom");
        });
    }

}

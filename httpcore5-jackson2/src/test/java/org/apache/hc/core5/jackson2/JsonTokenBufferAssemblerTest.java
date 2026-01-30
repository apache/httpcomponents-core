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
package org.apache.hc.core5.jackson2;

import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class JsonTokenBufferAssemblerTest {

    @Test
    void testTokenBufferAssembly() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final ObjectMapper objectMapper = new ObjectMapper(factory);
        final JsonAsyncTokenizer jsonTokenizer = new JsonAsyncTokenizer(factory);

        final URL resource = getClass().getResource("/sample1.json");
        Assertions.assertThat(resource).isNotNull();

        final AtomicReference<TokenBuffer> tokenBufferRef = new AtomicReference<>(null);
        try (final InputStream inputStream = resource.openStream()) {
            jsonTokenizer.initialize(new TokenBufferAssembler(tokenBufferRef::set));
            final byte[] bytebuf = new byte[1024];
            int len;
            while ((len = inputStream.read(bytebuf)) != -1) {
                jsonTokenizer.consume(ByteBuffer.wrap(bytebuf, 0, len));
            }
            jsonTokenizer.streamEnd();
        }

        final TokenBuffer tokenBuffer = tokenBufferRef.get();
        Assertions.assertThat(tokenBuffer).isNotNull();
        final JsonNode jsonNode = objectMapper.readTree(tokenBuffer.asParserOnFirstToken());

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

        Assertions.assertThat(jsonNode).isEqualTo(expectedObject);
    }

    @Test
    void testTokenBufferSequenceAssembly() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final ObjectMapper objectMapper = new ObjectMapper(factory);
        final JsonAsyncTokenizer jsonTokenizer = new JsonAsyncTokenizer(factory);

        final URL resource = getClass().getResource("/sample3.json");
        Assertions.assertThat(resource).isNotNull();

        final AtomicInteger started = new AtomicInteger(0);
        final List<TokenBuffer> tokenBufferList = new ArrayList<>();
        final AtomicInteger ended = new AtomicInteger(0);
        try (final InputStream inputStream = resource.openStream()) {
            jsonTokenizer.initialize(new TokenBufferAssembler(new JsonResultSink<TokenBuffer>() {

                @Override
                public void begin(final int sizeHint) {
                    started.incrementAndGet();
                }

                @Override
                public void accept(final TokenBuffer tokenBuffer) {
                    tokenBufferList.add(tokenBuffer);
                }

                @Override
                public void end() {
                    ended.incrementAndGet();
                }

            }));
            final byte[] bytebuf = new byte[1024];
            int len;
            while ((len = inputStream.read(bytebuf)) != -1) {
                jsonTokenizer.consume(ByteBuffer.wrap(bytebuf, 0, len));
            }
            jsonTokenizer.streamEnd();
        }

        Assertions.assertThat(tokenBufferList).hasSize(3);
        Assertions.assertThat(started.get()).isEqualTo(1);
        Assertions.assertThat(ended.get()).isEqualTo(1);

        final TokenBuffer tokenBuffer1 = tokenBufferList.get(0);
        Assertions.assertThat(tokenBuffer1).isNotNull();
        final JsonNode jsonNode1 = objectMapper.readTree(tokenBuffer1.asParserOnFirstToken());

        final ObjectNode expectedObject1 = JsonNodeFactory.instance.objectNode();
        expectedObject1.put("url", "http://httpbin.org/stream/3");
        expectedObject1.putObject("args");
        expectedObject1.putObject("headers")
                .put("Host", "httpbin.org")
                .put("Connection", "close")
                .put("Referer", "http://httpbin.org/")
                .put("Accept", "application/json")
                .put("Accept-Encoding", "gzip, deflate")
                .put("Accept-Language", "en-US,en;q=0.9")
                .put("Cookie", "_gauges_unique_year=1; _gauges_unique=1; _gauges_unique_month=1; _gauges_unique_day=1; " +
                        "_gauges_unique_hour=1")
                .put("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "snap Chromium/71.0.3578.98 Chrome/71.0.3578.98 Safari/537.36");
        expectedObject1.put("origin", "xxx.xxx.xxx.xxx");
        expectedObject1.put("id", 0);

        Assertions.assertThat(jsonNode1).isEqualTo(expectedObject1);

        final TokenBuffer tokenBuffer2 = tokenBufferList.get(1);
        Assertions.assertThat(tokenBuffer2).isNotNull();
        final JsonNode jsonNode2 = objectMapper.readTree(tokenBuffer2.asParserOnFirstToken());

        final ObjectNode expectedObject2 = JsonNodeFactory.instance.objectNode();
        expectedObject2.put("url", "http://httpbin.org/stream/3");
        expectedObject2.putObject("args");
        expectedObject2.putObject("headers")
                .put("Host", "httpbin.org")
                .put("Connection", "close")
                .put("Referer", "http://httpbin.org/")
                .put("Accept", "application/json")
                .put("Accept-Encoding", "gzip, deflate")
                .put("Accept-Language", "en-US,en;q=0.9")
                .put("Cookie", "_gauges_unique_year=1; _gauges_unique=1; _gauges_unique_month=1; _gauges_unique_day=1; " +
                        "_gauges_unique_hour=1")
                .put("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "snap Chromium/71.0.3578.98 Chrome/71.0.3578.98 Safari/537.36");
        expectedObject2.put("origin", "xxx.xxx.xxx.xxx");
        expectedObject2.put("id", 1);

        Assertions.assertThat(jsonNode2).isEqualTo(expectedObject2);

        final TokenBuffer tokenBuffer3 = tokenBufferList.get(2);
        Assertions.assertThat(tokenBuffer3).isNotNull();
        final JsonNode jsonNode3 = objectMapper.readTree(tokenBuffer3.asParserOnFirstToken());

        final ObjectNode expectedObject3 = JsonNodeFactory.instance.objectNode();
        expectedObject3.put("url", "http://httpbin.org/stream/3");
        expectedObject3.putObject("args");
        expectedObject3.putObject("headers")
                .put("Host", "httpbin.org")
                .put("Connection", "close")
                .put("Referer", "http://httpbin.org/")
                .put("Accept", "application/json")
                .put("Accept-Encoding", "gzip, deflate")
                .put("Accept-Language", "en-US,en;q=0.9")
                .put("Cookie", "_gauges_unique_year=1; _gauges_unique=1; _gauges_unique_month=1; _gauges_unique_day=1; " +
                        "_gauges_unique_hour=1")
                .put("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "snap Chromium/71.0.3578.98 Chrome/71.0.3578.98 Safari/537.36");
        expectedObject3.put("origin", "xxx.xxx.xxx.xxx");
        expectedObject3.put("id", 2);

        Assertions.assertThat(jsonNode3).isEqualTo(expectedObject3);
    }

    @Test
    void testTokenBufferAssemblyNoContent() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final JsonAsyncTokenizer jsonTokenizer = new JsonAsyncTokenizer(factory);

        final AtomicReference<TokenBuffer> tokenBufferRef = new AtomicReference<>(null);
        jsonTokenizer.initialize(new TokenBufferAssembler(tokenBufferRef::set));
        jsonTokenizer.streamEnd();

        Assertions.assertThat(tokenBufferRef.get()).isNull();
    }

    @Test
    void testTokenBufferArrayAssembly() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final ObjectMapper objectMapper = new ObjectMapper(factory);
        final JsonAsyncTokenizer jsonTokenizer = new JsonAsyncTokenizer(factory);

        final URL resource = getClass().getResource("/sample6.json");
        Assertions.assertThat(resource).isNotNull();

        final AtomicInteger started = new AtomicInteger(0);
        final List<TokenBuffer> tokenBufferList = new ArrayList<>();
        final AtomicInteger ended = new AtomicInteger(0);
        try (final InputStream inputStream = resource.openStream()) {
            jsonTokenizer.initialize(new TopLevelArrayTokenFilter(new TokenBufferAssembler(new JsonResultSink<TokenBuffer>() {

                @Override
                public void begin(final int sizeHint) {
                    started.incrementAndGet();
                }

                @Override
                public void accept(final TokenBuffer tokenBuffer) {
                    tokenBufferList.add(tokenBuffer);
                }

                @Override
                public void end() {
                    ended.incrementAndGet();
                }

            })));
            final byte[] bytebuf = new byte[1024];
            int len;
            while ((len = inputStream.read(bytebuf)) != -1) {
                jsonTokenizer.consume(ByteBuffer.wrap(bytebuf, 0, len));
            }
            jsonTokenizer.streamEnd();
        }

        Assertions.assertThat(tokenBufferList).hasSize(3);
        Assertions.assertThat(started.get()).isEqualTo(1);
        Assertions.assertThat(ended.get()).isEqualTo(1);

        final TokenBuffer tokenBuffer1 = tokenBufferList.get(0);
        Assertions.assertThat(tokenBuffer1).isNotNull();
        final JsonNode jsonNode1 = objectMapper.readTree(tokenBuffer1.asParserOnFirstToken());

        final ObjectNode expectedObject1 = JsonNodeFactory.instance.objectNode();
        expectedObject1.put("url", "http://httpbin.org/stream/3");
        expectedObject1.putObject("args");
        expectedObject1.putObject("headers")
                .put("Host", "httpbin.org")
                .put("Connection", "close")
                .put("Referer", "http://httpbin.org/")
                .put("Accept", "application/json")
                .put("Accept-Encoding", "gzip, deflate")
                .put("Accept-Language", "en-US,en;q=0.9")
                .put("Cookie", "_gauges_unique_year=1; _gauges_unique=1; _gauges_unique_month=1; _gauges_unique_day=1; " +
                        "_gauges_unique_hour=1")
                .put("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "snap Chromium/71.0.3578.98 Chrome/71.0.3578.98 Safari/537.36");
        expectedObject1.put("origin", "xxx.xxx.xxx.xxx");
        expectedObject1.put("id", 0);

        Assertions.assertThat(jsonNode1).isEqualTo(expectedObject1);

        final TokenBuffer tokenBuffer2 = tokenBufferList.get(1);
        Assertions.assertThat(tokenBuffer2).isNotNull();
        final JsonNode jsonNode2 = objectMapper.readTree(tokenBuffer2.asParserOnFirstToken());

        final ObjectNode expectedObject2 = JsonNodeFactory.instance.objectNode();
        expectedObject2.put("url", "http://httpbin.org/stream/3");
        expectedObject2.putObject("args");
        expectedObject2.putObject("headers")
                .put("Host", "httpbin.org")
                .put("Connection", "close")
                .put("Referer", "http://httpbin.org/")
                .put("Accept", "application/json")
                .put("Accept-Encoding", "gzip, deflate")
                .put("Accept-Language", "en-US,en;q=0.9")
                .put("Cookie", "_gauges_unique_year=1; _gauges_unique=1; _gauges_unique_month=1; _gauges_unique_day=1; " +
                        "_gauges_unique_hour=1")
                .put("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "snap Chromium/71.0.3578.98 Chrome/71.0.3578.98 Safari/537.36");
        expectedObject2.put("origin", "xxx.xxx.xxx.xxx");
        expectedObject2.put("id", 1);

        Assertions.assertThat(jsonNode2).isEqualTo(expectedObject2);

        final TokenBuffer tokenBuffer3 = tokenBufferList.get(2);
        Assertions.assertThat(tokenBuffer3).isNotNull();
        final JsonNode jsonNode3 = objectMapper.readTree(tokenBuffer3.asParserOnFirstToken());

        final ObjectNode expectedObject3 = JsonNodeFactory.instance.objectNode();
        expectedObject3.put("url", "http://httpbin.org/stream/3");
        expectedObject3.putObject("args");
        expectedObject3.putObject("headers")
                .put("Host", "httpbin.org")
                .put("Connection", "close")
                .put("Referer", "http://httpbin.org/")
                .put("Accept", "application/json")
                .put("Accept-Encoding", "gzip, deflate")
                .put("Accept-Language", "en-US,en;q=0.9")
                .put("Cookie", "_gauges_unique_year=1; _gauges_unique=1; _gauges_unique_month=1; _gauges_unique_day=1; " +
                        "_gauges_unique_hour=1")
                .put("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "snap Chromium/71.0.3578.98 Chrome/71.0.3578.98 Safari/537.36");
        expectedObject3.put("origin", "xxx.xxx.xxx.xxx");
        expectedObject3.put("id", 2);

        Assertions.assertThat(jsonNode3).isEqualTo(expectedObject3);
    }

}

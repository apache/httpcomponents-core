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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class JsonNodeAssemblerTest {

    @Test
    void testJsonNodeAssembly() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final JsonAsyncTokenizer jsonTokenizer = new JsonAsyncTokenizer(factory);

        final URL resource1 = getClass().getResource("/sample1.json");
        Assertions.assertThat(resource1).isNotNull();

        final URL resource2 = getClass().getResource("/sample2.json");
        Assertions.assertThat(resource2).isNotNull();

        final JsonNodeAssembler jsonNodeAssembler1 = JsonNodeAssembler.create();

        try (final InputStream inputStream = resource1.openStream()) {
            jsonTokenizer.initialize(new JsonTokenEventHandlerAdaptor(jsonNodeAssembler1));
            final byte[] bytebuf = new byte[1024];
            int len;
            while ((len = inputStream.read(bytebuf)) != -1) {
                jsonTokenizer.consume(ByteBuffer.wrap(bytebuf, 0, len));
            }
            jsonTokenizer.streamEnd();
        }

        final JsonNode jsonNode1 = jsonNodeAssembler1.getResult();
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

        Assertions.assertThat(jsonNode1).isEqualTo(expectedObject1);

        final JsonNodeAssembler jsonNodeAssembler2 = JsonNodeAssembler.create();

        try (final InputStream inputStream = resource2.openStream()) {
            jsonTokenizer.initialize(new JsonTokenEventHandlerAdaptor(jsonNodeAssembler2));
            final byte[] bytebuf = new byte[1024];
            int len;
            while ((len = inputStream.read(bytebuf)) != -1) {
                jsonTokenizer.consume(ByteBuffer.wrap(bytebuf, 0, len));
            }
            jsonTokenizer.streamEnd();
        }

        final JsonNode jsonNode2 = jsonNodeAssembler2.getResult();
        final ArrayNode expectedObject2 = JsonNodeFactory.instance.arrayNode();
        expectedObject2.addArray().add(1).add(2).add(3);
        expectedObject2.addArray().add(1.1).add(2.2).add(3.3);
        expectedObject2.addArray()
                .add(JsonNodeFactory.instance.objectNode().put("name1", "value1"))
                .add(JsonNodeFactory.instance.objectNode().put("name2", "value2"))
                .add(JsonNodeFactory.instance.objectNode().put("name3", "value3"));
        expectedObject2.addArray()
                .add(2)
                .add(2.2)
                .add(JsonNodeFactory.instance.objectNode().put("name2", "value2"));
        expectedObject2.addArray().add(JsonNodeFactory.instance.objectNode().put("long", 2153599188L));

        Assertions.assertThat(jsonNode2).isEqualTo(expectedObject2);
    }

}

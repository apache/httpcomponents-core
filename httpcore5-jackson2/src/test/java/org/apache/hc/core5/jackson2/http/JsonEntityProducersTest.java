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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class JsonEntityProducersTest {

    static class MockDataStreamChannel implements DataStreamChannel {

        private final WritableByteChannel byteChannel;

        public MockDataStreamChannel(final WritableByteChannel byteChannel) {
            this.byteChannel = byteChannel;
        }

        @Override
        public void requestOutput() {
        }

        @Override
        public int write(final ByteBuffer src) throws IOException {
            return byteChannel.write(src);
        }

        @Override
        public void endStream() throws IOException {
            if (byteChannel.isOpen()) {
                byteChannel.close();
            }
        }

        @Override
        public void endStream(final List<? extends Header> trailers) throws IOException {
            endStream();
        }

        public boolean isOpen() {
            return byteChannel.isOpen();
        }

    }

    @Test
    void testJsonObjectEntityProducer() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final ObjectMapper objectMapper = new ObjectMapper(factory);

        final JsonObjectEntityProducer<List<NameValuePair>> producer = new JsonObjectEntityProducer<>(
                Arrays.asList(
                        new BasicNameValuePair("param1", "value"),
                        new BasicNameValuePair("param2", "blah-blah-blah-blah-blah-blah-blah-blah-blah-blah-blah"))
                , objectMapper);

        final int[][] params = new int[][]{{1024, -1}, {16, 16}, {32, 32}};

        for (int i = 0; i < params.length; i++) {
            final WritableByteChannelMock byteChannel = new WritableByteChannelMock(params[i][0], params[i][1]);
            final MockDataStreamChannel dataChannel = new MockDataStreamChannel(byteChannel);
            while (dataChannel.isOpen()) {
                producer.produce(dataChannel);
                byteChannel.flush();
            }
            Assertions.assertThat(byteChannel.dump(StandardCharsets.US_ASCII)).isEqualTo("[" +
                    "{\"name\":\"param1\",\"value\":\"value\"}," +
                    "{\"name\":\"param2\",\"value\":\"blah-blah-blah-blah-blah-blah-blah-blah-blah-blah-blah\"}" +
                    "]");
            producer.releaseResources();
        }
    }

    @Test
    void testJsonNodeEntityProducer() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final ObjectMapper objectMapper = new ObjectMapper(factory);

        final ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
        arrayNode.add(JsonNodeFactory.instance.
                objectNode().put("name", "param1").put("value", "value"));
        arrayNode.add(JsonNodeFactory.instance.
                objectNode().put("name", "param2").put("value", "blah-blah-blah-blah-blah-blah-blah-blah-blah-blah-blah"));

        final JsonNodeEntityProducer producer = new JsonNodeEntityProducer(arrayNode, objectMapper);

        final int[][] params = new int[][]{{1024, -1}, {16, 16}, {32, 32}};

        for (int i = 0; i < params.length; i++) {
            final WritableByteChannelMock byteChannel = new WritableByteChannelMock(params[i][0], params[i][1]);
            final MockDataStreamChannel dataChannel = new MockDataStreamChannel(byteChannel);
            while (dataChannel.isOpen()) {
                producer.produce(dataChannel);
                byteChannel.flush();
            }
            Assertions.assertThat(byteChannel.dump(StandardCharsets.US_ASCII)).isEqualTo("[" +
                    "{\"name\":\"param1\",\"value\":\"value\"}," +
                    "{\"name\":\"param2\",\"value\":\"blah-blah-blah-blah-blah-blah-blah-blah-blah-blah-blah\"}" +
                    "]");
            producer.releaseResources();
        }
    }

    @Test
    void testJsonObjectSequenceEntityProducer() throws Exception {
        final JsonFactory factory = new JsonFactory();
        final ObjectMapper objectMapper = new ObjectMapper(factory);

        final AtomicInteger count = new AtomicInteger(0);

        final JsonSequenceEntityProducer<NameValuePair> producer = new JsonSequenceEntityProducer<>(
                objectMapper,
                1024,
                channel -> {
                    switch (count.incrementAndGet()) {
                        case 1:
                            channel.write(
                                    new BasicNameValuePair("param1", "value"));
                            break;
                        case 2:
                            channel.write(
                                    new BasicNameValuePair("param2", "blah-blah-blah-blah-blah-blah-blah-blah-blah-blah-blah"));
                            break;
                        default:
                            channel.endStream();
                    }

                });

        final int[][] params = new int[][]{{1024, -1}, {16, 16}, {32, 32}};

        for (int i = 0; i < params.length; i++) {
            final WritableByteChannelMock byteChannel = new WritableByteChannelMock(params[i][0], params[i][1]);
            final MockDataStreamChannel dataChannel = new MockDataStreamChannel(byteChannel);
            while (dataChannel.isOpen()) {
                producer.produce(dataChannel);
                byteChannel.flush();
            }
            Assertions.assertThat(byteChannel.dump(StandardCharsets.US_ASCII)).isEqualTo(
                    "{\"name\":\"param1\",\"value\":\"value\"}" +
                            "{\"name\":\"param2\",\"value\":\"blah-blah-blah-blah-blah-blah-blah-blah-blah-blah-blah\"}"
            );
            producer.releaseResources();
            count.set(0);
        }
    }

}

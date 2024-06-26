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

package org.apache.hc.core5.http.nio.entity;

import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.WritableByteChannelMock;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.BasicDataStreamChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.StreamChannel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestAbstractCharAsyncEntityProducer {

    static private class ChunkCharAsyncEntityProducer extends AbstractCharAsyncEntityProducer {

        private final String[] content;
        private int count = 0;

        public ChunkCharAsyncEntityProducer(
                final int bufferSize,
                final int fragmentSizeHint,
                final ContentType contentType,
                final String... content) {
            super(bufferSize, fragmentSizeHint, contentType);
            this.content = content;
        }

        @Override
        public boolean isRepeatable() {
            return false;
        }

        @Override
        protected int availableData() {
            return Integer.MAX_VALUE;
        }

        @Override
        protected void produceData(final StreamChannel<CharBuffer> channel) throws IOException {
            if (count < content.length) {
                channel.write(CharBuffer.wrap(content[count]));
            }
            count++;
            if (count >= content.length) {
                channel.endStream();
            }
        }

        @Override
        public void failed(final Exception cause) {
        }

    }

    @Test
    void testProduceDataNoBuffering() throws Exception {

        final AsyncEntityProducer producer = new ChunkCharAsyncEntityProducer(
                256, 0, ContentType.TEXT_PLAIN, "this", "this and that");

        Assertions.assertEquals(-1, producer.getContentLength());
        Assertions.assertEquals(ContentType.TEXT_PLAIN.toString(), producer.getContentType());
        Assertions.assertNull(producer.getContentEncoding());

        final WritableByteChannelMock byteChannel = new WritableByteChannelMock(1024);
        final DataStreamChannel streamChannel = new BasicDataStreamChannel(byteChannel);

        producer.produce(streamChannel);

        Assertions.assertTrue(byteChannel.isOpen());
        Assertions.assertEquals("this", byteChannel.dump(StandardCharsets.US_ASCII));

        producer.produce(streamChannel);

        Assertions.assertFalse(byteChannel.isOpen());
        Assertions.assertEquals("this and that", byteChannel.dump(StandardCharsets.US_ASCII));
    }

    @Test
    void testProduceDataWithBuffering() throws Exception {

        final AsyncEntityProducer producer = new ChunkCharAsyncEntityProducer(
                256, 5, ContentType.TEXT_PLAIN, "this", " and that", "all", " sorts of stuff");

        final WritableByteChannelMock byteChannel = new WritableByteChannelMock(1024);
        final DataStreamChannel streamChannel = new BasicDataStreamChannel(byteChannel);

        producer.produce(streamChannel);
        Assertions.assertTrue(byteChannel.isOpen());
        Assertions.assertEquals("", byteChannel.dump(StandardCharsets.US_ASCII));

        producer.produce(streamChannel);
        Assertions.assertTrue(byteChannel.isOpen());
        Assertions.assertEquals("this and that", byteChannel.dump(StandardCharsets.US_ASCII));

        producer.produce(streamChannel);
        Assertions.assertTrue(byteChannel.isOpen());
        Assertions.assertEquals("", byteChannel.dump(StandardCharsets.US_ASCII));

        producer.produce(streamChannel);
        Assertions.assertFalse(byteChannel.isOpen());
        Assertions.assertEquals("all sorts of stuff", byteChannel.dump(StandardCharsets.US_ASCII));
    }

}

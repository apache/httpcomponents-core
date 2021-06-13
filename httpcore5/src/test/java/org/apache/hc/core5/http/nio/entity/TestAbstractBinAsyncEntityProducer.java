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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.WritableByteChannelMock;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.BasicDataStreamChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.StreamChannel;
import org.junit.Assert;
import org.junit.Test;

public class TestAbstractBinAsyncEntityProducer {

    static private class ChunkByteAsyncEntityProducer extends AbstractBinAsyncEntityProducer {

        private final byte[][] content;
        private int count = 0;

        public ChunkByteAsyncEntityProducer(
                final int fragmentSizeHint,
                final ContentType contentType,
                final byte[]... content) {
            super(fragmentSizeHint, contentType);
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
        protected void produceData(final StreamChannel<ByteBuffer> channel) throws IOException {
            if (count < content.length) {
                channel.write(ByteBuffer.wrap(content[count]));
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
    public void testProduceDataNoBuffering() throws Exception {

        final AsyncEntityProducer producer = new ChunkByteAsyncEntityProducer(
                0, ContentType.TEXT_PLAIN,
                new byte[] { '1', '2', '3' },
                new byte[] { '4', '5', '6' });

        Assert.assertEquals(-1, producer.getContentLength());
        Assert.assertEquals(ContentType.TEXT_PLAIN.toString(), producer.getContentType());
        Assert.assertNull(producer.getContentEncoding());

        final WritableByteChannelMock byteChannel = new WritableByteChannelMock(1024);
        final DataStreamChannel streamChannel = new BasicDataStreamChannel(byteChannel);

        producer.produce(streamChannel);

        Assert.assertTrue(byteChannel.isOpen());
        Assert.assertEquals("123", byteChannel.dump(StandardCharsets.US_ASCII));

        producer.produce(streamChannel);

        Assert.assertFalse(byteChannel.isOpen());
        Assert.assertEquals("456", byteChannel.dump(StandardCharsets.US_ASCII));
    }

    @Test
    public void testProduceDataWithBuffering1() throws Exception {

        final AsyncEntityProducer producer = new ChunkByteAsyncEntityProducer(
                5, ContentType.TEXT_PLAIN,
                new byte[] { '1', '2', '3' },
                new byte[] { '4', '5', '6' },
                new byte[] { '7', '8' },
                new byte[] { '9', '0' });

        final WritableByteChannelMock byteChannel = new WritableByteChannelMock(1024);
        final DataStreamChannel streamChannel = new BasicDataStreamChannel(byteChannel);

        producer.produce(streamChannel);
        Assert.assertTrue(byteChannel.isOpen());
        Assert.assertEquals("", byteChannel.dump(StandardCharsets.US_ASCII));

        producer.produce(streamChannel);
        Assert.assertTrue(byteChannel.isOpen());
        Assert.assertEquals("123", byteChannel.dump(StandardCharsets.US_ASCII));

        producer.produce(streamChannel);
        Assert.assertTrue(byteChannel.isOpen());
        Assert.assertEquals("45678", byteChannel.dump(StandardCharsets.US_ASCII));

        producer.produce(streamChannel);
        Assert.assertFalse(byteChannel.isOpen());
        Assert.assertEquals("90", byteChannel.dump(StandardCharsets.US_ASCII));
    }

    @Test
    public void testProduceDataWithBuffering2() throws Exception {

        final AsyncEntityProducer producer = new ChunkByteAsyncEntityProducer(
                5, ContentType.TEXT_PLAIN,
                new byte[] { '1' },
                new byte[] { '2' },
                new byte[] { '3' },
                new byte[] { '4', '5' },
                new byte[] { '6' },
                new byte[] { '7', '8' },
                new byte[] { '9', '0' });

        final WritableByteChannelMock byteChannel = new WritableByteChannelMock(1024);
        final DataStreamChannel streamChannel = new BasicDataStreamChannel(byteChannel);

        producer.produce(streamChannel);
        Assert.assertTrue(byteChannel.isOpen());
        Assert.assertEquals("", byteChannel.dump(StandardCharsets.US_ASCII));

        producer.produce(streamChannel);
        Assert.assertTrue(byteChannel.isOpen());
        Assert.assertEquals("", byteChannel.dump(StandardCharsets.US_ASCII));

        producer.produce(streamChannel);
        Assert.assertTrue(byteChannel.isOpen());
        Assert.assertEquals("", byteChannel.dump(StandardCharsets.US_ASCII));

        producer.produce(streamChannel);
        Assert.assertTrue(byteChannel.isOpen());
        Assert.assertEquals("12345", byteChannel.dump(StandardCharsets.US_ASCII));

        producer.produce(streamChannel);
        Assert.assertTrue(byteChannel.isOpen());
        Assert.assertEquals("", byteChannel.dump(StandardCharsets.US_ASCII));

        producer.produce(streamChannel);
        Assert.assertTrue(byteChannel.isOpen());
        Assert.assertEquals("", byteChannel.dump(StandardCharsets.US_ASCII));

        producer.produce(streamChannel);
        Assert.assertFalse(byteChannel.isOpen());
        Assert.assertEquals("67890", byteChannel.dump(StandardCharsets.US_ASCII));

    }

}

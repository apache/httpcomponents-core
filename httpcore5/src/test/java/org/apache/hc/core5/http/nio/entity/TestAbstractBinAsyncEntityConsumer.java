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

import org.apache.hc.core5.concurrent.CountingFutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestAbstractBinAsyncEntityConsumer {

    static private class ByteArrayAsyncEntityConsumer extends AbstractBinAsyncEntityConsumer<byte[]> {

        private final ByteArrayBuffer buffer;

        public ByteArrayAsyncEntityConsumer() {
            super();
            this.buffer = new ByteArrayBuffer(1024);
        }

        @Override
        protected void streamStart(final ContentType contentType) throws HttpException, IOException {
        }

        @Override
        protected int capacityIncrement() {
            return Integer.MAX_VALUE;
        }

        @Override
        protected void data(final ByteBuffer src, final boolean endOfStream) throws IOException {
            buffer.append(src);
        }

        @Override
        protected byte[] generateContent() throws IOException {
            return buffer.toByteArray();
        }

        @Override
        public void releaseResources() {
        }

    }

    @Test
    void testConsumeData() throws Exception {
        final AsyncEntityConsumer<byte[]> consumer = new ByteArrayAsyncEntityConsumer();
        final CountingFutureCallback<byte[]> countingCallback = new CountingFutureCallback<>();
        consumer.streamStart(new BasicEntityDetails(-1, ContentType.APPLICATION_OCTET_STREAM), countingCallback);

        consumer.consume(ByteBuffer.wrap(new byte[]{'1', '2', '3'}));
        consumer.consume(ByteBuffer.wrap(new byte[]{'4', '5'}));
        consumer.consume(ByteBuffer.wrap(new byte[]{}));

        Assertions.assertNull(consumer.getContent());
        consumer.streamEnd(null);

        Assertions.assertArrayEquals(new byte[] {'1', '2', '3', '4', '5'}, consumer.getContent());
        Assertions.assertEquals(1L, countingCallback.getCount());
    }

}

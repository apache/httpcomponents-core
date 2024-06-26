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
package org.apache.hc.core5.reactive;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.HttpStreamResetException;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.reactivex.rxjava3.core.Flowable;

class TestReactiveDataProducer {
    @Test
    void testStreamThatEndsNormally() throws Exception {
        final Flowable<ByteBuffer> publisher = Flowable.just(
            ByteBuffer.wrap(new byte[]{ '1', '2', '3' }),
            ByteBuffer.wrap(new byte[]{ '4', '5', '6' }));
        final ReactiveDataProducer producer = new ReactiveDataProducer(publisher);

        final WritableByteChannelMock byteChannel = new WritableByteChannelMock(1024);
        final DataStreamChannel streamChannel = new BasicDataStreamChannel(byteChannel);

        producer.produce(streamChannel);

        Assertions.assertTrue(byteChannel.isOpen());
        Assertions.assertEquals("123456", byteChannel.dump(StandardCharsets.US_ASCII));

        producer.produce(streamChannel);

        Assertions.assertFalse(byteChannel.isOpen());
        Assertions.assertEquals("", byteChannel.dump(StandardCharsets.US_ASCII));
    }

    @Test
    void testStreamThatEndsWithError() throws Exception {
        final Flowable<ByteBuffer> publisher = Flowable.concatArray(
            Flowable.just(
                ByteBuffer.wrap(new byte[]{ '1' }),
                ByteBuffer.wrap(new byte[]{ '2' }),
                ByteBuffer.wrap(new byte[]{ '3' }),
                ByteBuffer.wrap(new byte[]{ '4' }),
                ByteBuffer.wrap(new byte[]{ '5' }),
                ByteBuffer.wrap(new byte[]{ '6' })),
            Flowable.error(new RuntimeException())
        );
        final ReactiveDataProducer producer = new ReactiveDataProducer(publisher);

        final WritableByteChannelMock byteChannel = new WritableByteChannelMock(1024);
        final DataStreamChannel streamChannel = new BasicDataStreamChannel(byteChannel);

        producer.produce(streamChannel);
        Assertions.assertEquals("12345", byteChannel.dump(StandardCharsets.US_ASCII));

        final HttpStreamResetException exception = Assertions.assertThrows(HttpStreamResetException.class, () ->
                producer.produce(streamChannel));
        Assertions.assertTrue(exception.getCause() instanceof RuntimeException, "Expected published exception to be rethrown");
        Assertions.assertEquals("", byteChannel.dump(StandardCharsets.US_ASCII));
    }
}

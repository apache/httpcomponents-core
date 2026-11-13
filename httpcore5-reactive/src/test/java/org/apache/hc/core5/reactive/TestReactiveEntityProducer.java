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
import java.util.Collections;

import io.reactivex.rxjava3.core.Flowable;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStreamResetException;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

class TestReactiveEntityProducer {

    private static final long CONTENT_LENGTH = 1;
    private static final ContentType CONTENT_TYPE = ContentType.APPLICATION_JSON;
    private static final String GZIP_CONTENT_ENCODING = "gzip";

    @Test
    void testStreamThatEndsNormally() throws Exception {
        final Flowable<ByteBuffer> publisher = Flowable.just(
                ByteBuffer.wrap(new byte[]{'1', '2', '3'}),
                ByteBuffer.wrap(new byte[]{'4', '5', '6'}));
        final ReactiveEntityProducer entityProducer = new ReactiveEntityProducer(publisher, CONTENT_LENGTH, CONTENT_TYPE, GZIP_CONTENT_ENCODING);

        final WritableByteChannelMock byteChannel = new WritableByteChannelMock(1024);
        final DataStreamChannel streamChannel = new BasicDataStreamChannel(byteChannel);

        entityProducer.produce(streamChannel);

        Assertions.assertThat(byteChannel.isOpen()).withFailMessage("Should be open").isTrue();
        Assertions.assertThat(byteChannel.dump(StandardCharsets.US_ASCII)).isEqualTo("123456");

        entityProducer.produce(streamChannel);

        Assertions.assertThat(byteChannel.isOpen()).withFailMessage("Should be closed").isFalse();
        Assertions.assertThat(byteChannel.dump(StandardCharsets.US_ASCII)).isEqualTo("");
        Assertions.assertThat(entityProducer.isChunked()).isFalse();
        Assertions.assertThat(entityProducer.getContentEncoding()).isEqualTo(GZIP_CONTENT_ENCODING);
        Assertions.assertThat(entityProducer.getTrailerNames()).isEqualTo(Collections.emptySet());
        Assertions.assertThat(entityProducer.getContentLength()).isEqualTo(CONTENT_LENGTH);
        Assertions.assertThat(entityProducer.getContentType()).isEqualTo(CONTENT_TYPE.toString());
        Assertions.assertThat(entityProducer.isRepeatable()).isFalse();
        Assertions.assertThat(entityProducer.available()).isEqualTo(1);

        entityProducer.releaseResources();
    }

    @Test

    void testStreamThatEndsWithError() throws Exception {
        final Flowable<ByteBuffer> publisher = Flowable.concatArray(
                Flowable.just(
                        ByteBuffer.wrap(new byte[]{'1'}),
                        ByteBuffer.wrap(new byte[]{'2'}),
                        ByteBuffer.wrap(new byte[]{'3'}),
                        ByteBuffer.wrap(new byte[]{'4'}),
                        ByteBuffer.wrap(new byte[]{'5'}),
                        ByteBuffer.wrap(new byte[]{'6'})),
                Flowable.error(new RuntimeException())
        );
        final ReactiveEntityProducer entityProducer = new ReactiveEntityProducer(publisher, CONTENT_LENGTH, CONTENT_TYPE, GZIP_CONTENT_ENCODING);

        final WritableByteChannelMock byteChannel = new WritableByteChannelMock(1024);
        final DataStreamChannel streamChannel = new BasicDataStreamChannel(byteChannel);

        entityProducer.produce(streamChannel);
        Assertions.assertThat(byteChannel.dump(StandardCharsets.US_ASCII)).isEqualTo("12345");

        final HttpStreamResetException exception = Assertions.assertThatThrownBy(() ->
                entityProducer.produce(streamChannel)).isInstanceOf(HttpStreamResetException.class).asInstanceOf(InstanceOfAssertFactories.type(HttpStreamResetException.class)).actual();
        Assertions.assertThat(exception.getCause()).withFailMessage("Expected published exception to be rethrown").isInstanceOf(RuntimeException.class);
        Assertions.assertThat(byteChannel.dump(StandardCharsets.US_ASCII)).isEqualTo("");
        entityProducer.failed(exception);
        Assertions.assertThat(entityProducer.available()).isEqualTo(1);

        Assertions.assertThat(byteChannel.isOpen()).isTrue();
        Assertions.assertThat(byteChannel.dump(StandardCharsets.US_ASCII)).isEqualTo("");
        Assertions.assertThat(entityProducer.isChunked()).isFalse();
        Assertions.assertThat(entityProducer.getContentEncoding()).isEqualTo(GZIP_CONTENT_ENCODING);
        Assertions.assertThat(entityProducer.getTrailerNames()).isEqualTo(Collections.emptySet());
        Assertions.assertThat(entityProducer.getContentLength()).isEqualTo(CONTENT_LENGTH);
        Assertions.assertThat(entityProducer.getContentType()).isEqualTo(CONTENT_TYPE.toString());
        Assertions.assertThat(entityProducer.isRepeatable()).isFalse();
        Assertions.assertThat(entityProducer.available()).isEqualTo(1);

        entityProducer.releaseResources();
    }

}

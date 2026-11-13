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

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.WritableByteChannelMock;
import org.apache.hc.core5.http.nio.BasicDataStreamChannel;
import org.apache.hc.core5.util.TextUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestDigestingEntityProducer {

    @Test
    void testProduceData() throws Exception {

        final DigestingEntityProducer producer = new DigestingEntityProducer("MD5",
                new StringAsyncEntityProducer("12345", ContentType.TEXT_PLAIN));

        final WritableByteChannelMock byteChannel = new WritableByteChannelMock(1024);
        final BasicDataStreamChannel dataStreamChannel = new BasicDataStreamChannel(byteChannel);
        while (byteChannel.isOpen()) {
            producer.produce(dataStreamChannel);
        }

        Assertions.assertThat(byteChannel.dump(StandardCharsets.US_ASCII)).isEqualTo("12345");
        final List<Header> trailers = dataStreamChannel.getTrailers();
        Assertions.assertThat(trailers).isNotNull();
        Assertions.assertThat(trailers.size()).isEqualTo(2);

        Assertions.assertThat(trailers.get(0).getName()).isEqualTo("digest-algo");
        Assertions.assertThat(trailers.get(0).getValue()).isEqualTo("MD5");
        Assertions.assertThat(trailers.get(1).getName()).isEqualTo("digest");
        Assertions.assertThat(trailers.get(1).getValue()).isEqualTo("827ccb0eea8a706c4c34a16891f84e7b");
    }

    @Test
    void testProduceDataRepeatableAfterPartialWrite() throws Exception {
        final DigestingEntityProducer producer = new DigestingEntityProducer("MD5",
                new StringAsyncEntityProducer("abcdef", 6, 6, ContentType.TEXT_PLAIN));

        final WritableByteChannelMock partialByteChannel = new WritableByteChannelMock(1024, 3);
        producer.produce(new BasicDataStreamChannel(partialByteChannel));
        Assertions.assertThat(partialByteChannel.dump(StandardCharsets.US_ASCII)).isEqualTo("abc");
        producer.releaseResources();

        final WritableByteChannelMock byteChannel = new WritableByteChannelMock(1024);
        final BasicDataStreamChannel dataStreamChannel = new BasicDataStreamChannel(byteChannel);
        while (byteChannel.isOpen()) {
            producer.produce(dataStreamChannel);
        }

        Assertions.assertThat(byteChannel.dump(StandardCharsets.US_ASCII)).isEqualTo("abcdef");
        Assertions.assertThat(TextUtils.toHexString(producer.getDigest())).isEqualTo("e80b5017098950fc58aad83c8c14978e");

        final List<Header> trailers = dataStreamChannel.getTrailers();
        Assertions.assertThat(trailers).isNotNull();
        Assertions.assertThat(trailers.get(1).getName()).isEqualTo("digest");
        Assertions.assertThat(trailers.get(1).getValue()).isEqualTo("e80b5017098950fc58aad83c8c14978e");
    }

}

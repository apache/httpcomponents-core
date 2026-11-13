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
package org.apache.hc.core5.http2.frame;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.config.H2Param;
import org.apache.hc.core5.http2.config.H2Setting;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestDefaultFrameFactory {

    @Test
    void testDataFrame() {

        final FrameFactory frameFactory = new DefaultFrameFactory();

        final byte[] data = new byte[]{'a', 'b', 'c', 'd', 'e', 'f'};
        final Frame<ByteBuffer> dataFrame = frameFactory.createData(23, ByteBuffer.wrap(data), true);

        Assertions.assertThat(dataFrame.getType()).isEqualTo(FrameType.DATA.value);
        Assertions.assertThat(dataFrame.getStreamId()).isEqualTo(23);
        Assertions.assertThat(dataFrame.getFlags()).isEqualTo(1L);
    }

    @Test
    void testSettingFrame() {

        final FrameFactory frameFactory = new DefaultFrameFactory();
        final Frame<ByteBuffer> settingsFrame = frameFactory.createSettings(
                new H2Setting(H2Param.HEADER_TABLE_SIZE, 1024),
                new H2Setting(H2Param.MAX_CONCURRENT_STREAMS, 1));

        Assertions.assertThat(settingsFrame.getType()).isEqualTo(FrameType.SETTINGS.value);
        Assertions.assertThat(settingsFrame.getStreamId()).isEqualTo(0);
        Assertions.assertThat(settingsFrame.getFlags()).isEqualTo(0);
        final ByteBuffer payload = settingsFrame.getPayload();
        Assertions.assertThat(payload).isNotNull();
        Assertions.assertThat(payload.remaining()).isEqualTo(12);
    }

    @Test
    void testResetStreamFrame() {

        final FrameFactory frameFactory = new DefaultFrameFactory();
        final Frame<ByteBuffer> rstStreamFrame = frameFactory.createResetStream(12, H2Error.INTERNAL_ERROR);

        Assertions.assertThat(rstStreamFrame.getType()).isEqualTo(FrameType.RST_STREAM.value);
        Assertions.assertThat(rstStreamFrame.getStreamId()).isEqualTo(12);
        Assertions.assertThat(rstStreamFrame.getFlags()).isEqualTo(0);
        final ByteBuffer payload = rstStreamFrame.getPayload();
        Assertions.assertThat(payload).isNotNull();
        Assertions.assertThat(payload.remaining()).isEqualTo(4);
        Assertions.assertThat(payload.getInt()).isEqualTo(H2Error.INTERNAL_ERROR.getCode());
    }

    @Test
    void testGoAwayFrame() {

        final FrameFactory frameFactory = new DefaultFrameFactory();
        final Frame<ByteBuffer> goAwayFrame = frameFactory.createGoAway(13, H2Error.INTERNAL_ERROR, "Oopsie");

        Assertions.assertThat(goAwayFrame.getType()).isEqualTo(FrameType.GOAWAY.value);
        Assertions.assertThat(goAwayFrame.getStreamId()).isEqualTo(0);
        Assertions.assertThat(goAwayFrame.getFlags()).isEqualTo(0);
        final ByteBuffer payload = goAwayFrame.getPayload();
        Assertions.assertThat(payload).isNotNull();
        Assertions.assertThat(payload.getInt()).isEqualTo(13);
        Assertions.assertThat(payload.getInt()).isEqualTo(H2Error.INTERNAL_ERROR.getCode());
        final byte[] tmp = new byte[payload.remaining()];
        payload.get(tmp);
        Assertions.assertThat(new String(tmp, StandardCharsets.US_ASCII)).isEqualTo("Oopsie");
    }

}

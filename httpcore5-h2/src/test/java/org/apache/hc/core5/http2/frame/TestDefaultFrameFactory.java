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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestDefaultFrameFactory {

    @Test
    void testDataFrame() {

        final FrameFactory frameFactory = new DefaultFrameFactory();

        final byte[] data = new byte[]{'a', 'b', 'c', 'd', 'e', 'f'};
        final Frame<ByteBuffer> dataFrame = frameFactory.createData(23, ByteBuffer.wrap(data), true);

        Assertions.assertEquals(FrameType.DATA.value, dataFrame.getType());
        Assertions.assertEquals(23, dataFrame.getStreamId());
        Assertions.assertEquals(1L, dataFrame.getFlags());
    }

    @Test
    void testSettingFrame() {

        final FrameFactory frameFactory = new DefaultFrameFactory();
        final Frame<ByteBuffer> settingsFrame = frameFactory.createSettings(
                new H2Setting(H2Param.HEADER_TABLE_SIZE, 1024),
                new H2Setting(H2Param.MAX_CONCURRENT_STREAMS, 1));

        Assertions.assertEquals(FrameType.SETTINGS.value, settingsFrame.getType());
        Assertions.assertEquals(0, settingsFrame.getStreamId());
        Assertions.assertEquals(0, settingsFrame.getFlags());
        final ByteBuffer payload = settingsFrame.getPayload();
        Assertions.assertNotNull(payload);
        Assertions.assertEquals(12, payload.remaining());
    }

    @Test
    void testResetStreamFrame() {

        final FrameFactory frameFactory = new DefaultFrameFactory();
        final Frame<ByteBuffer> rstStreamFrame = frameFactory.createResetStream(12, H2Error.INTERNAL_ERROR);

        Assertions.assertEquals(FrameType.RST_STREAM.value, rstStreamFrame.getType());
        Assertions.assertEquals(12, rstStreamFrame.getStreamId());
        Assertions.assertEquals(0, rstStreamFrame.getFlags());
        final ByteBuffer payload = rstStreamFrame.getPayload();
        Assertions.assertNotNull(payload);
        Assertions.assertEquals(4, payload.remaining());
        Assertions.assertEquals(H2Error.INTERNAL_ERROR.getCode(), payload.getInt());
    }

    @Test
    void testGoAwayFrame() {

        final FrameFactory frameFactory = new DefaultFrameFactory();
        final Frame<ByteBuffer> goAwayFrame = frameFactory.createGoAway(13, H2Error.INTERNAL_ERROR, "Oopsie");

        Assertions.assertEquals(FrameType.GOAWAY.value, goAwayFrame.getType());
        Assertions.assertEquals(0, goAwayFrame.getStreamId());
        Assertions.assertEquals(0, goAwayFrame.getFlags());
        final ByteBuffer payload = goAwayFrame.getPayload();
        Assertions.assertNotNull(payload);
        Assertions.assertEquals(13, payload.getInt());
        Assertions.assertEquals(H2Error.INTERNAL_ERROR.getCode(), payload.getInt());
        final byte[] tmp = new byte[payload.remaining()];
        payload.get(tmp);
        Assertions.assertEquals("Oopsie", new String(tmp, StandardCharsets.US_ASCII));
    }

}

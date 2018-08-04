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
import org.junit.Assert;
import org.junit.Test;

public class TestDefaultFrameFactory {

    @Test
    public void testDataFrame() throws Exception {

        final FrameFactory frameFactory = new DefaultFrameFactory();

        final byte[] data = new byte[]{'a', 'b', 'c', 'd', 'e', 'f'};
        final Frame<ByteBuffer> dataFrame = frameFactory.createData(23, ByteBuffer.wrap(data), true);

        Assert.assertEquals(FrameType.DATA.value, dataFrame.getType());
        Assert.assertEquals(23, dataFrame.getStreamId());
        Assert.assertEquals(1L, dataFrame.getFlags());
    }

    @Test
    public void testSettingFrame() throws Exception {

        final FrameFactory frameFactory = new DefaultFrameFactory();
        final Frame<ByteBuffer> settingsFrame = frameFactory.createSettings(
                new H2Setting(H2Param.HEADER_TABLE_SIZE, 1024),
                new H2Setting(H2Param.MAX_CONCURRENT_STREAMS, 1));

        Assert.assertEquals(FrameType.SETTINGS.value, settingsFrame.getType());
        Assert.assertEquals(0, settingsFrame.getStreamId());
        Assert.assertEquals(0, settingsFrame.getFlags());
        final ByteBuffer payload = settingsFrame.getPayload();
        Assert.assertNotNull(payload);
        Assert.assertEquals(12, payload.remaining());
    }

    @Test
    public void testResetStreamFrame() throws Exception {

        final FrameFactory frameFactory = new DefaultFrameFactory();
        final Frame<ByteBuffer> rstStreamFrame = frameFactory.createResetStream(12, H2Error.INTERNAL_ERROR);

        Assert.assertEquals(FrameType.RST_STREAM.value, rstStreamFrame.getType());
        Assert.assertEquals(12, rstStreamFrame.getStreamId());
        Assert.assertEquals(0, rstStreamFrame.getFlags());
        final ByteBuffer payload = rstStreamFrame.getPayload();
        Assert.assertNotNull(payload);
        Assert.assertEquals(4, payload.remaining());
        Assert.assertEquals(H2Error.INTERNAL_ERROR.getCode(), payload.getInt());
    }

    @Test
    public void testGoAwayFrame() throws Exception {

        final FrameFactory frameFactory = new DefaultFrameFactory();
        final Frame<ByteBuffer> goAwayFrame = frameFactory.createGoAway(13, H2Error.INTERNAL_ERROR, "Oopsie");

        Assert.assertEquals(FrameType.GOAWAY.value, goAwayFrame.getType());
        Assert.assertEquals(0, goAwayFrame.getStreamId());
        Assert.assertEquals(0, goAwayFrame.getFlags());
        final ByteBuffer payload = goAwayFrame.getPayload();
        Assert.assertNotNull(payload);
        Assert.assertEquals(13, payload.getInt());
        Assert.assertEquals(H2Error.INTERNAL_ERROR.getCode(), payload.getInt());
        final byte[] tmp = new byte[payload.remaining()];
        payload.get(tmp);
        Assert.assertEquals("Oopsie", new String(tmp, StandardCharsets.US_ASCII));
    }

}

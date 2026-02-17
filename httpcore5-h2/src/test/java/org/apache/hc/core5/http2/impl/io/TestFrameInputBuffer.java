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
package org.apache.hc.core5.http2.impl.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;

import org.apache.hc.core5.http2.frame.RawFrame;
import org.junit.jupiter.api.Test;

class TestFrameInputBuffer {


    @Test
    void reservedBitInStreamIdIsIgnored() throws Exception {
        final byte[] frame = new byte[]{
                0x00, 0x00, 0x00,   // length = 0
                0x00,               // type = DATA
                0x00,               // flags
                (byte) 0x8A, 0x12, 0x34, 0x56 // streamId with reserved bit set
        };

        final FrameInputBuffer inBuf = new FrameInputBuffer(16384);
        final RawFrame rawFrame = inBuf.read(new ByteArrayInputStream(frame));

        assertEquals(0x0A123456, rawFrame.getStreamId());
    }

}

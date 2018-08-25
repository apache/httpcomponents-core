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

/**
 * Standard HTTP/2 frame types.
 *
 * @since 5.0
 */
public enum FrameType {

    DATA          (0x00),
    HEADERS       (0x01),
    PRIORITY      (0x02),
    RST_STREAM    (0x03),
    SETTINGS      (0x04),
    PUSH_PROMISE  (0x05),
    PING          (0x06),
    GOAWAY        (0x07),
    WINDOW_UPDATE (0x08),
    CONTINUATION  (0x09);

    int value;

    FrameType(final int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    private static final FrameType[] LOOKUP_TABLE = new FrameType[10];
    static {
        for (final FrameType frameType: FrameType.values()) {
            LOOKUP_TABLE[frameType.value] = frameType;
        }
    }

    public static FrameType valueOf(final int value) {
        if (value < 0 || value >= LOOKUP_TABLE.length) {
            return null;
        }
        return LOOKUP_TABLE[value];
    }

    public static String toString(final int value) {
        if (value < 0 || value >= LOOKUP_TABLE.length) {
            return Integer.toString(value);
        }
        return LOOKUP_TABLE[value].name();
    }

}

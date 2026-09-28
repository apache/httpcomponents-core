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
package org.apache.hc.core5.http2.hpack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.junit.jupiter.api.Test;

class TestHPackEncoderUtf8 {

    @Test
    void testLargeMultibyteHeaderValueRoundTrip() throws Exception {
        final StringBuilder buffer = new StringBuilder(1000);
        for (int i = 0; i < 1000; i++) {
            buffer.append('\u4e2d');
        }
        final String value = buffer.toString();

        final HPackEncoder encoder = new HPackEncoder(4096, StandardCharsets.UTF_8);
        final ByteArrayBuffer encoded = new ByteArrayBuffer(128);
        encoder.encodeHeader(encoded, "x-test", value, false);

        final HPackDecoder decoder = new HPackDecoder(4096, StandardCharsets.UTF_8);
        final ByteBuffer src = ByteBuffer.wrap(encoded.array(), 0, encoded.length());
        final List<Header> headers = decoder.decodeHeaders(src);

        assertEquals(1, headers.size());
        assertEquals("x-test", headers.get(0).getName());
        assertEquals(value, headers.get(0).getValue());
        assertEquals(0, src.remaining());
    }

}

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
package org.apache.hc.core5.http2.impl.nio;

import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestH2OriginFrameCodec {

    @Test
    void parsesAndNormalizesAsciiOrigins() throws Exception {
        Assertions.assertEquals(
                new HttpHost("https", "example.com", 443),
                H2OriginFrameCodec.parse("HTTPS://EXAMPLE.COM"));
        Assertions.assertEquals(
                new HttpHost("https", "example.com", 8443),
                H2OriginFrameCodec.parse("https://example.com:8443"));
        Assertions.assertEquals(
                new HttpHost("https", "2001:db8::1", 443),
                H2OriginFrameCodec.parse("https://[2001:db8::1]"));
        Assertions.assertEquals(
                new HttpHost("https", "bücher.example", 443),
                H2OriginFrameCodec.parse("https://xn--bcher-kva.example"));
        Assertions.assertEquals(
                new HttpHost("custom", "example.com", 9443),
                H2OriginFrameCodec.parse("custom://example.com:9443"));
    }

    @Test
    void formatsRfc6454AsciiSerialization() {
        Assertions.assertEquals(
                "https://example.com",
                H2OriginFrameCodec.format(new HttpHost("https", "example.com", 443)));
        Assertions.assertEquals(
                "https://example.com",
                H2OriginFrameCodec.format(new HttpHost("HTTPS", "EXAMPLE.COM", 443)));
        Assertions.assertEquals(
                "https://example.com:8443",
                H2OriginFrameCodec.format(new HttpHost("https", "example.com", 8443)));
        Assertions.assertEquals(
                "https://[2001:db8::1]",
                H2OriginFrameCodec.format(new HttpHost("https", "2001:db8::1", 443)));
        Assertions.assertEquals(
                "https://xn--bcher-kva.example",
                H2OriginFrameCodec.format(new HttpHost("https", "bücher.example", 443)));
    }

    @Test
    void rejectsValuesThatAreNotAsciiOriginSerializations() {
        final List<String> invalid = Arrays.asList(
                "",
                "example.com",
                "1https://example.com",
                "https:/example.com",
                "https://",
                "https://user@example.com",
                "https://*.example.com",
                "https://[fe80::1%25eth0]",
                "https://example.com/",
                "https://example.com?x=1",
                "https://example.com#fragment",
                "https://example.com:",
                "https://example.com:+443",
                "https://example.com:44x",
                "https://bücher.example",
                "custom://example.com");
        for (final String value : invalid) {
            Assertions.assertThrows(URISyntaxException.class, () -> H2OriginFrameCodec.parse(value), value);
        }
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                H2OriginFrameCodec.format(new HttpHost("https", "bad/host", 443)));
    }

    @Test
    void decodesValidEntriesAndIgnoresInvalidOnes() {
        final byte[] valid = "https://one.example".getBytes(StandardCharsets.US_ASCII);
        final byte[] withPath = "https://bad.example/".getBytes(StandardCharsets.US_ASCII);
        final byte[] nonAscii = new byte[] {'h', 't', 't', 'p', 's', ':', '/', '/', (byte) 0xff};
        final ByteBuffer payload = ByteBuffer.allocate(
                2 + valid.length + 2 + withPath.length + 2 + nonAscii.length + 3);
        payload.putShort((short) valid.length).put(valid);
        payload.putShort((short) withPath.length).put(withPath);
        payload.putShort((short) nonAscii.length).put(nonAscii);
        payload.putShort((short) 10).put((byte) 'x'); // truncated final entry
        payload.flip();

        final Set<HttpHost> result = H2OriginFrameCodec.decode(payload);

        Assertions.assertEquals(Collections.singleton(new HttpHost("https", "one.example", 443)), result);
    }

    @Test
    void encodesEntriesAndSplitsOnlyAtEntryBoundaries() {
        final List<HttpHost> origins = Arrays.asList(
                new HttpHost("https", "a.example", 443),
                new HttpHost("https", "b.example", 8443));

        final List<ByteBuffer> payloads = H2OriginFrameCodec.encode(origins, 25);

        Assertions.assertEquals(2, payloads.size());
        Assertions.assertEquals(Collections.singleton(new HttpHost("https", "a.example", 443)),
                H2OriginFrameCodec.decode(payloads.get(0)));
        Assertions.assertEquals(Collections.singleton(new HttpHost("https", "b.example", 8443)),
                H2OriginFrameCodec.decode(payloads.get(1)));
    }

    @Test
    void encodesEmptyOriginSetAsOneEmptyPayload() {
        final List<ByteBuffer> payloads = H2OriginFrameCodec.encode(Collections.emptyList(), 16384);
        Assertions.assertEquals(1, payloads.size());
        Assertions.assertEquals(0, payloads.get(0).remaining());
    }
}

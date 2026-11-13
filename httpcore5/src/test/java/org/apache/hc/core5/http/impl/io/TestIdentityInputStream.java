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

package org.apache.hc.core5.http.impl.io;

import java.io.ByteArrayInputStream;

import org.apache.hc.core5.http.StreamClosedException;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link IdentityInputStream}.
 */
class TestIdentityInputStream {

    @Test
    void testBasicRead() throws Exception {
        final byte[] input = new byte[] {'a', 'b', 'c'};
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(input);
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        try (IdentityInputStream in = new IdentityInputStream(inBuffer, inputStream)) {
            final byte[] tmp = new byte[2];
            Assertions.assertThat(in.read(tmp, 0, tmp.length)).isEqualTo(2);
            Assertions.assertThat(tmp[0]).isEqualTo((byte) 'a');
            Assertions.assertThat(tmp[1]).isEqualTo((byte) 'b');
            Assertions.assertThat(in.read()).isEqualTo((int) 'c');
            Assertions.assertThat(in.read(tmp, 0, tmp.length)).isEqualTo(-1);
            Assertions.assertThat(in.read()).isEqualTo(-1);
            Assertions.assertThat(in.read(tmp, 0, tmp.length)).isEqualTo(-1);
            Assertions.assertThat(in.read()).isEqualTo(-1);
        }
    }

    @Test
    void testClosedCondition() throws Exception {
        final byte[] input = new byte[] {'a', 'b', 'c'};
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(input);
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(16);
        final IdentityInputStream in = new IdentityInputStream(inBuffer, inputStream);

        in.close();
        in.close();

        Assertions.assertThat(in.available()).isEqualTo(0);
        final byte[] tmp = new byte[2];
        Assertions.assertThatThrownBy(() ->
                        in.read(tmp, 0, tmp.length))
                .isInstanceOf(StreamClosedException.class);
        Assertions.assertThatThrownBy(() ->
                        in.read())
                .isInstanceOf(StreamClosedException.class);
    }

    @Test
    void testAvailable() throws Exception {
        final byte[] input = new byte[] {'a', 'b', 'c'};
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(input);
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(new BasicHttpTransportMetrics(), 16, 16, 1024, null);
        try (IdentityInputStream in = new IdentityInputStream(inBuffer, inputStream)) {
            in.read();
            Assertions.assertThat(in.available()).isEqualTo(2);
        }
    }

    @Test
    void testAvailableInStream() throws Exception {
        final byte[] input = new byte[] {'a', 'b', 'c', 'd', 'e', 'f'};
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(input);
        final SessionInputBuffer inBuffer = new SessionInputBufferImpl(new BasicHttpTransportMetrics(), 16, 0, 1024, null);
        try (IdentityInputStream in = new IdentityInputStream(inBuffer, inputStream)) {
            final byte[] tmp = new byte[3];
            Assertions.assertThat(in.read(tmp)).isEqualTo(3);
            Assertions.assertThat(in.available()).isEqualTo(3);
        }
    }

}

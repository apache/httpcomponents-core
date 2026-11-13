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

package org.apache.hc.core5.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test cases for HTTP version class
 */
class TestHttpVersion {

    @Test
    void testEqualsMajorMinor() {
        Assertions.assertThat(HttpVersion.HTTP_0_9.equals(0, 9)).isTrue();
        Assertions.assertThat(HttpVersion.HTTP_1_0.equals(1, 0)).isTrue();
        Assertions.assertThat(HttpVersion.HTTP_1_1.equals(1, 1)).isTrue();
        Assertions.assertThat(HttpVersion.HTTP_2.equals(2, 0)).isTrue();
        Assertions.assertThat(HttpVersion.HTTP_2_0.equals(2, 0)).isTrue();
        //
        Assertions.assertThat(HttpVersion.HTTP_0_9.equals(2, 0)).isFalse();
    }

    @Test
    void testGet() {
        Assertions.assertThat(HttpVersion.get(0, 9)).isEqualTo(HttpVersion.HTTP_0_9);
        Assertions.assertThat(HttpVersion.get(1, 0)).isEqualTo(HttpVersion.HTTP_1_0);
        Assertions.assertThat(HttpVersion.get(1, 1)).isEqualTo(HttpVersion.HTTP_1_1);
        Assertions.assertThat(HttpVersion.get(2, 0)).isEqualTo(HttpVersion.HTTP_2_0);
        Assertions.assertThat(HttpVersion.get(2, 0)).isEqualTo(HttpVersion.HTTP_2);
        Assertions.assertThat(HttpVersion.get(2, 1)).isNotEqualTo(HttpVersion.HTTP_2_0);
        //
        Assertions.assertThat(HttpVersion.get(1, 0)).isSameAs(HttpVersion.HTTP_1_0);
        Assertions.assertThat(HttpVersion.get(1, 1)).isSameAs(HttpVersion.HTTP_1_1);
        Assertions.assertThat(HttpVersion.get(2, 0)).isSameAs(HttpVersion.HTTP_2_0);
        Assertions.assertThat(HttpVersion.get(2, 0)).isSameAs(HttpVersion.HTTP_2);
        Assertions.assertThat(HttpVersion.get(2, 1)).isNotSameAs(HttpVersion.HTTP_2_0);
        Assertions.assertThat(HttpVersion.get(0, 9)).isNotSameAs(HttpVersion.HTTP_0_9);
    }

    @SuppressWarnings("unused")
    @Test
    void testHttpVersionInvalidConstructorInput() {
        Assertions.assertThatThrownBy(() ->
                        new HttpVersion(-1, -1))
                .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() ->
                        new HttpVersion(0, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testHttpVersionEquality() {
        final HttpVersion ver1 = new HttpVersion(1, 1);
        final HttpVersion ver2 = new HttpVersion(1, 1);

        Assertions.assertThat(ver2.hashCode()).isEqualTo(ver1.hashCode());
        Assertions.assertThat(ver1).isEqualTo(ver1);
        Assertions.assertThat(ver2).isEqualTo(ver1);
        Assertions.assertThat(ver1).isEqualTo(ver1);
        Assertions.assertThat(ver2).isEqualTo(ver1);

        Assertions.assertThat(Float.valueOf(1.1f)).isNotEqualTo(ver1);

        Assertions.assertThat(new HttpVersion(0, 9)).isEqualTo(HttpVersion.HTTP_0_9);
        Assertions.assertThat(new HttpVersion(1, 0)).isEqualTo(HttpVersion.HTTP_1_0);
        Assertions.assertThat(new HttpVersion(1, 1)).isEqualTo(HttpVersion.HTTP_1_1);
        Assertions.assertThat(new HttpVersion(1, 1)).isNotEqualTo(HttpVersion.HTTP_1_0);

        Assertions.assertThat(new ProtocolVersion("HTTP", 0, 9)).isEqualTo(HttpVersion.HTTP_0_9);
        Assertions.assertThat(new ProtocolVersion("HTTP", 1, 0)).isEqualTo(HttpVersion.HTTP_1_0);
        Assertions.assertThat(new ProtocolVersion("HTTP", 1, 1)).isEqualTo(HttpVersion.HTTP_1_1);
        Assertions.assertThat(new ProtocolVersion("http", 1, 1)).isNotEqualTo(HttpVersion.HTTP_1_1);

        Assertions.assertThat(new ProtocolVersion("HTTP", 0, 9)).isEqualTo(HttpVersion.HTTP_0_9);
        Assertions.assertThat(new ProtocolVersion("HTTP", 1, 0)).isEqualTo(HttpVersion.HTTP_1_0);
        Assertions.assertThat(new ProtocolVersion("HTTP", 1, 1)).isEqualTo(HttpVersion.HTTP_1_1);
        Assertions.assertThat(new ProtocolVersion("http", 1, 1)).isNotEqualTo(HttpVersion.HTTP_1_1);
    }

    @Test
    void testHttpVersionComparison() {
        Assertions.assertThat(HttpVersion.HTTP_0_9.lessEquals(HttpVersion.HTTP_1_1)).isTrue();
        Assertions.assertThat(HttpVersion.HTTP_0_9.greaterEquals(HttpVersion.HTTP_0_9)).isTrue();
        Assertions.assertThat(HttpVersion.HTTP_0_9.greaterEquals(HttpVersion.HTTP_1_0)).isFalse();

        Assertions.assertThat(HttpVersion.HTTP_1_0.compareToVersion(HttpVersion.HTTP_1_1) < 0).isTrue();
        Assertions.assertThat(HttpVersion.HTTP_1_0.compareToVersion(HttpVersion.HTTP_0_9) > 0).isTrue();
        Assertions.assertThat(HttpVersion.HTTP_1_0.compareToVersion(HttpVersion.HTTP_1_0)).isEqualTo(0);
   }

    @Test
    void testSerialization() throws Exception {
        final HttpVersion orig = HttpVersion.HTTP_1_1;
        final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        try (final ObjectOutputStream outStream = new ObjectOutputStream(outbuffer)) {
            outStream.writeObject(orig);
            outStream.close();
            final byte[] raw = outbuffer.toByteArray();
            final ByteArrayInputStream inBuffer = new ByteArrayInputStream(raw);
            final ObjectInputStream inStream = new ObjectInputStream(inBuffer);
            final HttpVersion clone = (HttpVersion) inStream.readObject();
            Assertions.assertThat(clone).isEqualTo(orig);
        }
    }

}


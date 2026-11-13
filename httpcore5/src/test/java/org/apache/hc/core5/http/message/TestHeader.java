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

package org.apache.hc.core5.http.message;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.hc.core5.http.Header;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Header}.
 */
class TestHeader {

    @Test
    void testBasicConstructor() {
        final Header header = new BasicHeader("name", "value");
        Assertions.assertThat(header.getName()).isEqualTo("name");
        Assertions.assertThat(header.getValue()).isEqualTo("value");
    }

    @Test
    void testBasicConstructorNullValue() {
        final Header header = new BasicHeader("name", null);
        Assertions.assertThat(header.getName()).isEqualTo("name");
        Assertions.assertThat(header.getValue()).isNull();
    }

    @Test
    void testInvalidName() {
        Assertions.assertThatThrownBy(() ->
                        new BasicHeader(null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testToString() {
        final Header header1 = new BasicHeader("name1", "value1");
        Assertions.assertThat(header1.toString()).isEqualTo("name1: value1");
        final Header header2 = new BasicHeader("name2", null);
        Assertions.assertThat(header2.toString()).isEqualTo("name2: ");
    }

    @Test
    void testSerialization() throws Exception {
        final BasicHeader orig = new BasicHeader("name1", "value1");
        final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        try (ObjectOutputStream outStream = new ObjectOutputStream(outbuffer)) {
            outStream.writeObject(orig);
        }
        final byte[] raw = outbuffer.toByteArray();
        final ByteArrayInputStream inBuffer = new ByteArrayInputStream(raw);
        final ObjectInputStream inStream = new ObjectInputStream(inBuffer);
        final BasicHeader clone = (BasicHeader) inStream.readObject();
        Assertions.assertThat(clone.getName()).isEqualTo(orig.getName());
        Assertions.assertThat(clone.getValue()).isEqualTo(orig.getValue());
    }


    @Test
    void testClone() throws Exception {
        final BasicHeader orig = new BasicHeader("name1", "value1");
        final BasicHeader clone = orig.clone();
        Assertions.assertThat(clone.getName()).isEqualTo(orig.getName());
        Assertions.assertThat(clone.getValue()).isEqualTo(orig.getValue());
    }

}


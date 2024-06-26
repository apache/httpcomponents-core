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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Header}.
 */
class TestHeader {

    @Test
    void testBasicConstructor() {
        final Header header = new BasicHeader("name", "value");
        Assertions.assertEquals("name", header.getName());
        Assertions.assertEquals("value", header.getValue());
    }

    @Test
    void testBasicConstructorNullValue() {
        final Header header = new BasicHeader("name", null);
        Assertions.assertEquals("name", header.getName());
        Assertions.assertNull(header.getValue());
    }

    @Test
    void testInvalidName() {
        Assertions.assertThrows(NullPointerException.class, () -> new BasicHeader(null, null));
    }

    @Test
    void testToString() {
        final Header header1 = new BasicHeader("name1", "value1");
        Assertions.assertEquals("name1: value1", header1.toString());
        final Header header2 = new BasicHeader("name2", null);
        Assertions.assertEquals("name2: ", header2.toString());
    }

    @Test
    void testSerialization() throws Exception {
        final BasicHeader orig = new BasicHeader("name1", "value1");
        final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        final ObjectOutputStream outStream = new ObjectOutputStream(outbuffer);
        outStream.writeObject(orig);
        outStream.close();
        final byte[] raw = outbuffer.toByteArray();
        final ByteArrayInputStream inBuffer = new ByteArrayInputStream(raw);
        final ObjectInputStream inStream = new ObjectInputStream(inBuffer);
        final BasicHeader clone = (BasicHeader) inStream.readObject();
        Assertions.assertEquals(orig.getName(), clone.getName());
        Assertions.assertEquals(orig.getValue(), clone.getValue());
    }


    @Test
    void testClone() throws Exception {
        final BasicHeader orig = new BasicHeader("name1", "value1");
        final BasicHeader clone = orig.clone();
        Assertions.assertEquals(orig.getName(), clone.getName());
        Assertions.assertEquals(orig.getValue(), clone.getValue());
    }

}


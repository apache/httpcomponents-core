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

package org.apache.hc.core5.http.io.entity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestSerializableEntity {

    public static class SerializableObject implements Serializable {

        private static final long serialVersionUID = 1833335861188359573L;

        public final int intValue = 4;

        public final String stringValue = "Hello";

        public SerializableObject() {}
    }

    @Test
    public void testBasics() throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final ObjectOutputStream out = new ObjectOutputStream(baos);

        final Serializable serializableObj = new SerializableObject();
        out.writeObject(serializableObj);

        try (final SerializableEntity httpentity = new SerializableEntity(serializableObj, null)) {

            Assertions.assertEquals(-1, httpentity.getContentLength());
            Assertions.assertNotNull(httpentity.getContent());
            Assertions.assertTrue(httpentity.isRepeatable());
            Assertions.assertFalse(httpentity.isStreaming());
        }
    }

    @Test
    public void testWriteTo() throws Exception {
        final Serializable serializableObj = new SerializableObject();
        try (final SerializableEntity httpentity = new SerializableEntity(serializableObj, null)) {

            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            httpentity.writeTo(out);
            final byte[] bytes = out.toByteArray();
            Assertions.assertNotNull(bytes);
            final ObjectInputStream oin = new ObjectInputStream(new ByteArrayInputStream(bytes));
            final SerializableObject serIn = (SerializableObject) oin.readObject();
            Assertions.assertEquals(4, serIn.intValue);
            Assertions.assertEquals("Hello", serIn.stringValue);

            Assertions.assertThrows(NullPointerException.class, () -> httpentity.writeTo(null));
        }
    }

}

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

package org.apache.http.entity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.junit.Assert;
import org.junit.Test;

public class TestSerializableEntity {

    public static class SerializableObject implements Serializable {

        private static final long serialVersionUID = 1833335861188359573L;

        public int intValue = 4;

        public String stringValue = "Hello";

        public SerializableObject() {}
    }

    @Test
    public void testBasicsBuff() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(baos);

        Serializable serializableObj = new SerializableObject();
        out.writeObject(serializableObj);

        SerializableEntity httpentity = new SerializableEntity(serializableObj, true);

        Assert.assertEquals(baos.toByteArray().length, httpentity.getContentLength());
        Assert.assertNotNull(httpentity.getContent());
        Assert.assertTrue(httpentity.isRepeatable());
        Assert.assertFalse(httpentity.isStreaming());
    }

    @Test
    public void testBasicsDirect() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(baos);

        Serializable serializableObj = new SerializableObject();
        out.writeObject(serializableObj);

        SerializableEntity httpentity = new SerializableEntity(serializableObj, false);

        Assert.assertEquals(-1, httpentity.getContentLength());
        Assert.assertNotNull(httpentity.getContent());
        Assert.assertTrue(httpentity.isRepeatable());
        Assert.assertFalse(httpentity.isStreaming());
    }

    @Test
    public void testIllegalConstructor() throws Exception {
        try {
            new SerializableEntity(null, false);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testWriteToBuff() throws Exception {
        Serializable serializableObj = new SerializableObject();
        SerializableEntity httpentity = new SerializableEntity(serializableObj, true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        httpentity.writeTo(out);
        byte[] bytes = out.toByteArray();
        Assert.assertNotNull(bytes);
        ObjectInputStream oin = new ObjectInputStream(new ByteArrayInputStream(
                bytes));
        SerializableObject serIn = (SerializableObject) oin.readObject();
        Assert.assertEquals(4, serIn.intValue);
        Assert.assertEquals("Hello", serIn.stringValue);

        try {
            httpentity.writeTo(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testWriteToDirect() throws Exception {
        Serializable serializableObj = new SerializableObject();
        SerializableEntity httpentity = new SerializableEntity(serializableObj, false);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        httpentity.writeTo(out);
        byte[] bytes = out.toByteArray();
        Assert.assertNotNull(bytes);
        ObjectInputStream oin = new ObjectInputStream(new ByteArrayInputStream(
                bytes));
        SerializableObject serIn = (SerializableObject) oin.readObject();
        Assert.assertEquals(4, serIn.intValue);
        Assert.assertEquals("Hello", serIn.stringValue);

        try {
            httpentity.writeTo(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

}

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

package org.apache.hc.core5.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link URIHost}.
 *
 */
public class TestURIHost {

    @Test
    public void testConstructor() {
        final URIHost host1 = new URIHost("somehost");
        Assert.assertEquals("somehost", host1.getHostName());
        Assert.assertEquals(-1, host1.getPort());
        final URIHost host2 = new URIHost("somehost", 8080);
        Assert.assertEquals("somehost", host2.getHostName());
        Assert.assertEquals(8080, host2.getPort());
        final URIHost host3 = new URIHost("somehost", -1);
        Assert.assertEquals("somehost", host3.getHostName());
        Assert.assertEquals(-1, host3.getPort());
    }

    @Test
    public void testHashCode() throws Exception {
        final URIHost host1 = new URIHost("somehost", 8080);
        final URIHost host2 = new URIHost("somehost", 80);
        final URIHost host3 = new URIHost("someotherhost", 8080);
        final URIHost host4 = new URIHost("somehost", 80);
        final URIHost host5 = new URIHost("SomeHost", 80);

        Assert.assertTrue(host1.hashCode() == host1.hashCode());
        Assert.assertTrue(host1.hashCode() != host2.hashCode());
        Assert.assertTrue(host1.hashCode() != host3.hashCode());
        Assert.assertTrue(host2.hashCode() == host4.hashCode());
        Assert.assertTrue(host2.hashCode() == host5.hashCode());
    }

    @Test
    public void testEquals() throws Exception {
        final URIHost host1 = new URIHost("somehost", 8080);
        final URIHost host2 = new URIHost("somehost", 80);
        final URIHost host3 = new URIHost("someotherhost", 8080);
        final URIHost host4 = new URIHost("somehost", 80);
        final URIHost host5 = new URIHost("SomeHost", 80);

        Assert.assertTrue(host1.equals(host1));
        Assert.assertFalse(host1.equals(host2));
        Assert.assertFalse(host1.equals(host3));
        Assert.assertTrue(host2.equals(host4));
        Assert.assertTrue(host2.equals(host5));
    }

    @Test
    public void testToString() throws Exception {
        final URIHost host1 = new URIHost("somehost");
        Assert.assertEquals("somehost", host1.toString());
        final URIHost host2 = new URIHost("somehost", -1);
        Assert.assertEquals("somehost", host2.toString());
        final URIHost host3 = new URIHost("somehost", 8888);
        Assert.assertEquals("somehost:8888", host3.toString());
    }

    @Test
    public void testSerialization() throws Exception {
        final URIHost orig = new URIHost("somehost", 8080);
        final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        final ObjectOutputStream outstream = new ObjectOutputStream(outbuffer);
        outstream.writeObject(orig);
        outstream.close();
        final byte[] raw = outbuffer.toByteArray();
        final ByteArrayInputStream inbuffer = new ByteArrayInputStream(raw);
        final ObjectInputStream instream = new ObjectInputStream(inbuffer);
        final URIHost clone = (URIHost) instream.readObject();
        Assert.assertEquals(orig, clone);
    }

    @Test
    public void testCreateFromString() throws Exception {
        Assert.assertEquals(new URIHost("somehost", 8080), URIHost.create("somehost:8080"));
        Assert.assertEquals(new URIHost("somehost", 8080), URIHost.create("SomeHost:8080"));
        Assert.assertEquals(new URIHost("somehost", 1234), URIHost.create("somehost:1234"));
        Assert.assertEquals(new URIHost("somehost", -1), URIHost.create("somehost"));
    }

    @Test
    public void testCreateFromStringInvalid() throws Exception {
        try {
            URIHost.create(" host ");
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            URIHost.create("host :8080");
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
    }

}

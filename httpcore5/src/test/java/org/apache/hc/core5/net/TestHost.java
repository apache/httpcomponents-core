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
import java.net.URISyntaxException;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link Host}.
 *
 */
public class TestHost {

    @Test
    public void testConstructor() {
        final Host host1 = new Host("somehost", 8080);
        Assert.assertEquals("somehost", host1.getHostName());
        Assert.assertEquals(8080, host1.getPort());
        final Host host2 = new Host("somehost", 0);
        Assert.assertEquals("somehost", host2.getHostName());
        Assert.assertEquals(0, host2.getPort());
        try {
            new Host(null, 0);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException expected) {
        }
        try {
            new Host("blah", -1);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException expected) {
        }
    }

    @Test
    public void testHashCode() throws Exception {
        final Host host1 = new Host("somehost", 8080);
        final Host host2 = new Host("somehost", 80);
        final Host host3 = new Host("someotherhost", 8080);
        final Host host4 = new Host("somehost", 80);

        Assert.assertTrue(host1.hashCode() == host1.hashCode());
        Assert.assertTrue(host1.hashCode() != host2.hashCode());
        Assert.assertTrue(host1.hashCode() != host3.hashCode());
        Assert.assertTrue(host2.hashCode() == host4.hashCode());
    }

    @Test
    public void testEquals() throws Exception {
        final Host host1 = new Host("somehost", 8080);
        final Host host2 = new Host("somehost", 80);
        final Host host3 = new Host("someotherhost", 8080);
        final Host host4 = new Host("somehost", 80);

        Assert.assertTrue(host1.equals(host1));
        Assert.assertFalse(host1.equals(host2));
        Assert.assertFalse(host1.equals(host3));
        Assert.assertTrue(host2.equals(host4));
    }

    @Test
    public void testToString() throws Exception {
        final Host host1 = new Host("somehost", 8888);
        Assert.assertEquals("somehost:8888", host1.toString());
    }

    @Test
    public void testSerialization() throws Exception {
        final Host orig = new Host("somehost", 8080);
        final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        final ObjectOutputStream outStream = new ObjectOutputStream(outbuffer);
        outStream.writeObject(orig);
        outStream.close();
        final byte[] raw = outbuffer.toByteArray();
        final ByteArrayInputStream inBuffer = new ByteArrayInputStream(raw);
        final ObjectInputStream inStream = new ObjectInputStream(inBuffer);
        final Host clone = (Host) inStream.readObject();
        Assert.assertEquals(orig, clone);
    }

    @Test
    public void testCreateFromString() throws Exception {
        Assert.assertEquals(new Host("somehost", 8080), Host.create("somehost:8080"));
        Assert.assertEquals(new Host("somehost", 1234), Host.create("somehost:1234"));
        Assert.assertEquals(new Host("somehost", 0), Host.create("somehost:0"));
    }

    @Test
    public void testCreateFromStringInvalid() throws Exception {
        try {
            Host.create(" host ");
            Assert.fail("URISyntaxException expected");
        } catch (final URISyntaxException expected) {
        }
        try {
            Host.create("host :8080");
            Assert.fail("URISyntaxException expected");
        } catch (final URISyntaxException expected) {
        }
        try {
            Host.create("");
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException expected) {
        }
    }

}

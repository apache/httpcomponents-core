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

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link URIAuthority}.
 *
 */
public class TestURIAuthority {

    @Test
    public void testConstructor() {
        final URIAuthority host1 = new URIAuthority("somehost");
        Assert.assertEquals("somehost", host1.getHostName());
        Assert.assertEquals(-1, host1.getPort());
        final URIAuthority host2 = new URIAuthority("somehost", 8080);
        Assert.assertEquals("somehost", host2.getHostName());
        Assert.assertEquals(8080, host2.getPort());
        final URIAuthority host3 = new URIAuthority("somehost", -1);
        Assert.assertEquals("somehost", host3.getHostName());
        Assert.assertEquals(-1, host3.getPort());
    }

    @Test
    public void testHashCode() throws Exception {
        final URIAuthority host1 = new URIAuthority("somehost", 8080);
        final URIAuthority host2 = new URIAuthority("somehost", 80);
        final URIAuthority host3 = new URIAuthority("someotherhost", 8080);
        final URIAuthority host4 = new URIAuthority("somehost", 80);
        final URIAuthority host5 = new URIAuthority("SomeHost", 80);
        final URIAuthority host6 = new URIAuthority("user", "SomeHost", 80);
        final URIAuthority host7 = new URIAuthority("user", "somehost", 80);

        Assert.assertTrue(host1.hashCode() == host1.hashCode());
        Assert.assertTrue(host1.hashCode() != host2.hashCode());
        Assert.assertTrue(host1.hashCode() != host3.hashCode());
        Assert.assertTrue(host2.hashCode() == host4.hashCode());
        Assert.assertTrue(host2.hashCode() == host5.hashCode());
        Assert.assertTrue(host5.hashCode() != host6.hashCode());
        Assert.assertTrue(host6.hashCode() == host7.hashCode());
    }

    @Test
    public void testEquals() throws Exception {
        final URIAuthority host1 = new URIAuthority("somehost", 8080);
        final URIAuthority host2 = new URIAuthority("somehost", 80);
        final URIAuthority host3 = new URIAuthority("someotherhost", 8080);
        final URIAuthority host4 = new URIAuthority("somehost", 80);
        final URIAuthority host5 = new URIAuthority("SomeHost", 80);
        final URIAuthority host6 = new URIAuthority("user", "SomeHost", 80);
        final URIAuthority host7 = new URIAuthority("user", "somehost", 80);

        Assert.assertTrue(host1.equals(host1));
        Assert.assertFalse(host1.equals(host2));
        Assert.assertFalse(host1.equals(host3));
        Assert.assertTrue(host2.equals(host4));
        Assert.assertTrue(host2.equals(host5));
        Assert.assertFalse(host5.equals(host6));
        Assert.assertTrue(host6.equals(host7));
    }

    @Test
    public void testToString() throws Exception {
        final URIAuthority host1 = new URIAuthority("somehost");
        Assert.assertEquals("somehost", host1.toString());
        final URIAuthority host2 = new URIAuthority("somehost", -1);
        Assert.assertEquals("somehost", host2.toString());
        final URIAuthority host3 = new URIAuthority("somehost", 8888);
        Assert.assertEquals("somehost:8888", host3.toString());
    }

    @Test
    public void testSerialization() throws Exception {
        final URIAuthority orig = new URIAuthority("somehost", 8080);
        final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        final ObjectOutputStream outStream = new ObjectOutputStream(outbuffer);
        outStream.writeObject(orig);
        outStream.close();
        final byte[] raw = outbuffer.toByteArray();
        final ByteArrayInputStream inBuffer = new ByteArrayInputStream(raw);
        final ObjectInputStream inStream = new ObjectInputStream(inBuffer);
        final URIAuthority clone = (URIAuthority) inStream.readObject();
        Assert.assertEquals(orig, clone);
    }

    @Test
    public void testParse() throws Exception {
        MatcherAssert.assertThat(URIAuthority.parse("somehost"),
                CoreMatchers.equalTo(new URIAuthority("somehost", -1)));
        MatcherAssert.assertThat(URIAuthority.parse("somehost/blah"),
                CoreMatchers.equalTo(new URIAuthority("somehost", -1)));
        MatcherAssert.assertThat(URIAuthority.parse("somehost?blah"),
                CoreMatchers.equalTo(new URIAuthority("somehost", -1)));
        MatcherAssert.assertThat(URIAuthority.parse("somehost#blah"),
                CoreMatchers.equalTo(new URIAuthority("somehost", -1)));
        MatcherAssert.assertThat(URIAuthority.parse("aaaa@:8080"),
                CoreMatchers.equalTo(new URIAuthority("aaaa", "", 8080)));
        MatcherAssert.assertThat(URIAuthority.parse("@:"),
                CoreMatchers.equalTo(new URIAuthority(null, "", -1)));
        MatcherAssert.assertThat(URIAuthority.parse("somehost:8080"),
                CoreMatchers.equalTo(new URIAuthority("somehost", 8080)));
        MatcherAssert.assertThat(URIAuthority.parse("somehost:8080/blah"),
                CoreMatchers.equalTo(new URIAuthority("somehost", 8080)));
        MatcherAssert.assertThat(URIAuthority.parse("somehost:8080?blah"),
                CoreMatchers.equalTo(new URIAuthority("somehost", 8080)));
        MatcherAssert.assertThat(URIAuthority.parse("somehost:8080#blah"),
                CoreMatchers.equalTo(new URIAuthority("somehost", 8080)));
        MatcherAssert.assertThat(URIAuthority.parse("somehost:008080"),
                CoreMatchers.equalTo(new URIAuthority("somehost", 8080)));
        try {
            URIAuthority.create("somehost:aaaaa");
            Assert.fail("URISyntaxException expected");
        } catch (final URISyntaxException expected) {
        }
        try {
            URIAuthority.create("somehost:90ab");
            Assert.fail("URISyntaxException expected");
        } catch (final URISyntaxException expected) {
        }

        MatcherAssert.assertThat(URIAuthority.parse("someuser@somehost"),
                CoreMatchers.equalTo(new URIAuthority("someuser", "somehost", -1)));
        MatcherAssert.assertThat(URIAuthority.parse("someuser@somehost/blah"),
                CoreMatchers.equalTo(new URIAuthority("someuser", "somehost", -1)));
        MatcherAssert.assertThat(URIAuthority.parse("someuser@somehost?blah"),
                CoreMatchers.equalTo(new URIAuthority("someuser", "somehost", -1)));
        MatcherAssert.assertThat(URIAuthority.parse("someuser@somehost#blah"),
                CoreMatchers.equalTo(new URIAuthority("someuser", "somehost", -1)));

        MatcherAssert.assertThat(URIAuthority.parse("someuser@somehost:"),
                CoreMatchers.equalTo(new URIAuthority("someuser", "somehost", -1)));
        MatcherAssert.assertThat(URIAuthority.parse("someuser@somehost:/blah"),
                CoreMatchers.equalTo(new URIAuthority("someuser", "somehost", -1)));
        MatcherAssert.assertThat(URIAuthority.parse("someuser@somehost:?blah"),
                CoreMatchers.equalTo(new URIAuthority("someuser", "somehost", -1)));
        MatcherAssert.assertThat(URIAuthority.parse("someuser@somehost:#blah"),
                CoreMatchers.equalTo(new URIAuthority("someuser", "somehost", -1)));

        MatcherAssert.assertThat(URIAuthority.parse("someuser@somehost:8080"),
                CoreMatchers.equalTo(new URIAuthority("someuser", "somehost", 8080)));
        MatcherAssert.assertThat(URIAuthority.parse("someuser@somehost:8080/blah"),
                CoreMatchers.equalTo(new URIAuthority("someuser", "somehost", 8080)));
        MatcherAssert.assertThat(URIAuthority.parse("someuser@somehost:8080?blah"),
                CoreMatchers.equalTo(new URIAuthority("someuser", "somehost", 8080)));
        MatcherAssert.assertThat(URIAuthority.parse("someuser@somehost:8080#blah"),
                CoreMatchers.equalTo(new URIAuthority("someuser", "somehost", 8080)));
        MatcherAssert.assertThat(URIAuthority.parse("@somehost:8080"),
                CoreMatchers.equalTo(new URIAuthority("somehost", 8080)));
        MatcherAssert.assertThat(URIAuthority.parse("test:test@localhost:38339"),
                CoreMatchers.equalTo(new URIAuthority("test:test", "localhost", 38339)));
        try {
            URIAuthority.create("blah@goggle.com:80@google.com/");
            Assert.fail("URISyntaxException expected");
        } catch (final URISyntaxException expected) {
        }
    }

    @Test
    public void testCreateFromString() throws Exception {
        Assert.assertEquals(new URIAuthority("somehost", 8080), URIAuthority.create("somehost:8080"));
        Assert.assertEquals(new URIAuthority("SomeHost", 8080), URIAuthority.create("SomeHost:8080"));
        Assert.assertEquals(new URIAuthority("somehost", 1234), URIAuthority.create("somehost:1234"));
        Assert.assertEquals(new URIAuthority("somehost", -1), URIAuthority.create("somehost"));
        Assert.assertEquals(new URIAuthority("user", "somehost", -1), URIAuthority.create("user@somehost"));
        try {
            URIAuthority.create(" host");
            Assert.fail("URISyntaxException expected");
        } catch (final URISyntaxException expected) {
        }
        try {
            URIAuthority.create("host  ");
            Assert.fail("URISyntaxException expected");
        } catch (final URISyntaxException expected) {
        }
        try {
            URIAuthority.create("host :8080");
            Assert.fail("URISyntaxException expected");
        } catch (final URISyntaxException expected) {
        }
        try {
            URIAuthority.create("user @ host:8080");
            Assert.fail("URISyntaxException expected");
        } catch (final URISyntaxException expected) {
        }
    }

}

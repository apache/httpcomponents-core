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

import org.apache.hc.core5.http.Header;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link BasicHeader}.
 */
public class TestBasicHeader {

    @Test
    public void testNullNotEqual() throws Exception {
        final Header header = new BasicHeader("name", "value");

        Assert.assertFalse(header.equals(null));
    }

    @Test
    public void testObjectNotEqual() throws Exception {
        final Header header = new BasicHeader("name", "value");

        Assert.assertFalse(header.equals(new Object()));
    }

    @Test
    public void testNameEquals() throws Exception {
        final Header header = new BasicHeader("name", "value");
        final Header header2 = new BasicHeader("NAME", "value");

        Assert.assertEquals(header, header2);
        Assert.assertEquals(header.hashCode(), header2.hashCode());

        final Header header3 = new BasicHeader("NAME", "value");
        final Header header4 = new BasicHeader("name", "value");

        Assert.assertEquals(header3, header4);
        Assert.assertEquals(header3.hashCode(), header4.hashCode());
    }

    @Test
    public void testValueEquals() throws Exception {
        final Header header = new BasicHeader("name", "value");
        final Header header2 = new BasicHeader("name", "value");

        Assert.assertEquals(header, header2);
        Assert.assertEquals(header.hashCode(), header2.hashCode());
    }

    @Test
    public void testNameNotEqual() throws Exception {
        final Header header = new BasicHeader("name", "value");
        final Header header2 = new BasicHeader("name2", "value");

        Assert.assertNotEquals(header, header2);
    }

    @Test
    public void testValueNotEqual() throws Exception {
        final Header header = new BasicHeader("name", "value");
        final Header header2 = new BasicHeader("name", "value2");

        Assert.assertNotEquals(header, header2);

        final Header header3 = new BasicHeader("name", "value");
        final Header header4 = new BasicHeader("name", "VALUE");

        Assert.assertNotEquals(header3, header4);

        final Header header5 = new BasicHeader("name", "VALUE");
        final Header header6 = new BasicHeader("name", "value");

        Assert.assertNotEquals(header5, header6);
    }

    @Test
    public void testNullValuesEquals() throws Exception {
        final Header header = new BasicHeader("name", null);
        final Header header2 = new BasicHeader("name", null);

        Assert.assertEquals(header, header2);
        Assert.assertEquals(header.hashCode(), header2.hashCode());
    }

    @Test
    public void testNullValueNotEqual() throws Exception {
        final Header header = new BasicHeader("name", null);
        final Header header2 = new BasicHeader("name", "value");

        Assert.assertNotEquals(header, header2);
    }

    @Test
    public void testNullValue2NotEqual() throws Exception {
        final Header header = new BasicHeader("name", "value");
        final Header header2 = new BasicHeader("name", null);

        Assert.assertNotEquals(header, header2);
    }

    @Test
    public void testEquals() throws Exception {
        final Header header = new BasicHeader("name", "value");
        final Header header2 = new BasicHeader("name", "value");

        Assert.assertEquals(header, header);
        Assert.assertEquals(header.hashCode(), header.hashCode());
        Assert.assertEquals(header2, header2);
        Assert.assertEquals(header2.hashCode(), header2.hashCode());
        Assert.assertEquals(header, header2);
        Assert.assertEquals(header.hashCode(), header2.hashCode());
    }

    @Test
    public void testHashCode() throws Exception {
        final Header header = new BasicHeader("name", null);
        final Header header2 = new BasicHeader("name2", null);

        Assert.assertNotEquals(header.hashCode(), header2.hashCode());

        final Header header3 = new BasicHeader("name", "value");
        final Header header4 = new BasicHeader("name", "value2");

        Assert.assertNotEquals(header3.hashCode(), header4.hashCode());

        final Header header5 = new BasicHeader("name", "value");
        final Header header6 = new BasicHeader("name", null);

        Assert.assertNotEquals(header5.hashCode(), header6.hashCode());
    }

    @Test
    public void testSensitive() throws Exception {
        // sensitivity has no affect on equality.
        final Header header = new BasicHeader("name", "value", true);
        final Header header2 = new BasicHeader("name", "value", false);

        Assert.assertEquals(header, header2);
        Assert.assertEquals(header.hashCode(), header2.hashCode());
    }
}

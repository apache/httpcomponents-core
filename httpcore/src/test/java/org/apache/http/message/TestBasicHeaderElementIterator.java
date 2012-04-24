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

package org.apache.http.message;

import java.util.NoSuchElementException;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link BasicHeaderElementIterator}.
 *
 */
public class TestBasicHeaderElementIterator {

    @Test
    public void testMultiHeader() {
        Header[] headers = new Header[]{
                new BasicHeader("Name", "value0"),
                new BasicHeader("Name", "value1")
        };

        HeaderElementIterator hei =
                new BasicHeaderElementIterator(
                        new BasicHeaderIterator(headers, "Name"));

        Assert.assertTrue(hei.hasNext());
        HeaderElement elem = hei.nextElement();
        Assert.assertEquals("The two header values must be equal",
                "value0", elem.getName());

        Assert.assertTrue(hei.hasNext());
        elem = hei.nextElement();
        Assert.assertEquals("The two header values must be equal",
                "value1", elem.getName());

        Assert.assertFalse(hei.hasNext());
        try {
            hei.next();
            Assert.fail("NoSuchElementException should have been thrown");
        } catch (NoSuchElementException ex) {
            // expected
        }

        Assert.assertFalse(hei.hasNext());
        try {
            hei.next();
            Assert.fail("NoSuchElementException should have been thrown");
        } catch (NoSuchElementException ex) {
            // expected
        }
    }

    @Test
    public void testMultiHeaderSameLine() {
        Header[] headers = new Header[]{
                new BasicHeader("name", "value0,value1"),
                new BasicHeader("nAme", "cookie1=1,cookie2=2")
        };

        HeaderElementIterator hei =
                new BasicHeaderElementIterator(new BasicHeaderIterator(headers, "Name"));

        HeaderElement elem = hei.nextElement();
        Assert.assertEquals("The two header values must be equal",
                "value0", elem.getName());
        elem = hei.nextElement();
        Assert.assertEquals("The two header values must be equal",
                "value1", elem.getName());
        elem = hei.nextElement();
        Assert.assertEquals("The two header values must be equal",
                "cookie1", elem.getName());
        Assert.assertEquals("The two header values must be equal",
                "1", elem.getValue());

        elem = hei.nextElement();
        Assert.assertEquals("The two header values must be equal",
                "cookie2", elem.getName());
        Assert.assertEquals("The two header values must be equal",
                "2", elem.getValue());
    }

    @Test
    public void testFringeCases() {
        Header[] headers = new Header[]{
                new BasicHeader("Name", null),
                new BasicHeader("Name", "    "),
                new BasicHeader("Name", ",,,")
        };

        HeaderElementIterator hei =
                new BasicHeaderElementIterator(
                        new BasicHeaderIterator(headers, "Name"));

        Assert.assertFalse(hei.hasNext());
        try {
            hei.next();
            Assert.fail("NoSuchElementException should have been thrown");
        } catch (NoSuchElementException ex) {
            // expected
        }

        Assert.assertFalse(hei.hasNext());
        try {
            hei.next();
            Assert.fail("NoSuchElementException should have been thrown");
        } catch (NoSuchElementException ex) {
            // expected
        }
    }

}

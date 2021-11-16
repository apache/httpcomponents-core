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

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.hc.core5.http.Header;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 * Tests for {@link java.util.Iterator} of {@link org.apache.hc.core5.http.Header}s.
 *
 */
public class TestBasicHeaderIterator {

    @Test
    public void testAllSame() {
        final Header[] headers = new Header[]{
            new BasicHeader("Name", "value0"),
            new BasicHeader("nAme", "value1, value1.1"),
            new BasicHeader("naMe", "value2=whatever"),
            new BasicHeader("namE", "value3;tag=nil"),
        };

        // without filter
        Iterator<Header> hit = new BasicHeaderIterator(headers, null);
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[0], hit.next(), "0");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[1], hit.next(), "1");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[2], hit.next(), "2");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[3], hit.next(), "3");
        Assertions.assertFalse(hit.hasNext());

        // with filter
        hit = new BasicHeaderIterator(headers, "name");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[0], hit.next(), "0");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[1], hit.next(), "1");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[2], hit.next(), "2");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[3], hit.next(), "3");
        Assertions.assertFalse(hit.hasNext());
    }


    @Test
    public void testFirstLastOneNone() {
        final Header[] headers = new Header[]{
            new BasicHeader("match"   , "value0"),
            new BasicHeader("mismatch", "value1, value1.1"),
            new BasicHeader("single"  , "value2=whatever"),
            new BasicHeader("match"   , "value3;tag=nil"),
        };

        // without filter
        Iterator<Header> hit = new BasicHeaderIterator(headers, null);
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[0], hit.next(), "0");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[1], hit.next(), "1");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[2], hit.next(), "2");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[3], hit.next(), "3");
        Assertions.assertFalse(hit.hasNext());

        // with filter, first & last
        hit = new BasicHeaderIterator(headers, "match");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[0], hit.next());
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[3], hit.next());
        Assertions.assertFalse(hit.hasNext());

        // with filter, one match
        hit = new BasicHeaderIterator(headers, "single");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[2], hit.next());
        Assertions.assertFalse(hit.hasNext());

        // with filter, no match
        hit = new BasicHeaderIterator(headers, "way-off");
        Assertions.assertFalse(hit.hasNext());
    }


    @Test
    public void testInterspersed() {
        final Header[] headers = new Header[]{
            new BasicHeader("yellow", "00"),
            new BasicHeader("maroon", "01"),
            new BasicHeader("orange", "02"),
            new BasicHeader("orange", "03"),
            new BasicHeader("orange", "04"),
            new BasicHeader("yellow", "05"),
            new BasicHeader("maroon", "06"),
            new BasicHeader("maroon", "07"),
            new BasicHeader("maroon", "08"),
            new BasicHeader("yellow", "09"),
            new BasicHeader("maroon", "0a"),
            new BasicHeader("yellow", "0b"),
            new BasicHeader("orange", "0c"),
            new BasicHeader("yellow", "0d"),
            new BasicHeader("orange", "0e"),
        };

        // without filter
        Iterator<Header> hit = new BasicHeaderIterator(headers, null);
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[0], hit.next(), "0");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[1], hit.next(), "1");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[2], hit.next(), "2");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[3], hit.next(), "3");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[4], hit.next(), "4");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[5], hit.next(), "5");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[6], hit.next(), "6");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[7], hit.next(), "7");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[8], hit.next(), "8");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[9], hit.next(), "9");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[10], hit.next(), "a");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[11], hit.next(), "b");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[12], hit.next(), "c");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[13], hit.next(), "d");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[14], hit.next(), "e");
        Assertions.assertFalse(hit.hasNext());

        // yellow 0, 5, 9, 11, 13
        hit = new BasicHeaderIterator(headers, "Yellow");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[0], hit.next());
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[5], hit.next(), "5");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[9], hit.next(), "9");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[11], hit.next(), "b");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[13], hit.next(), "d");
        Assertions.assertFalse(hit.hasNext());

        // maroon 1, 6, 7, 8, 10
        hit = new BasicHeaderIterator(headers, "marOOn");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[1], hit.next(),"1");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[6], hit.next(),"6");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[7], hit.next(), "7");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[8], hit.next(), "8");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[10], hit.next(), "a");
        Assertions.assertFalse(hit.hasNext());

        // orange 2, 3, 4, 12, 14
        hit = new BasicHeaderIterator(headers, "OranGe");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[2], hit.next());
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[3], hit.next());
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[4], hit.next(), "4");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[12], hit.next(), "b");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[14], hit.next(), "e");
        Assertions.assertFalse(hit.hasNext());
    }


    @Test
    public void testInvalid() {
        Assertions.assertThrows(NullPointerException.class, () -> new BasicHeaderIterator(null, "whatever"));
        // this is not invalid
        final Iterator<Header> hit = new BasicHeaderIterator(new Header[0], "whatever");
        Assertions.assertFalse(hit.hasNext());

        // but this is
        Assertions.assertThrows(NoSuchElementException.class, () -> hit.next());
    }

    @Test
    public void testRemaining() {
        // to satisfy Clover and take coverage to 100%

        final Header[] headers = new Header[]{
            new BasicHeader("Name", "value0"),
            new BasicHeader("nAme", "value1, value1.1"),
            new BasicHeader("naMe", "value2=whatever"),
            new BasicHeader("namE", "value3;tag=nil"),
        };

        // without filter, using plain next()
        final Iterator<Header> hit = new BasicHeaderIterator(headers, null);
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[0], hit.next(), "0");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[1], hit.next(), "1");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[2], hit.next(), "2");
        Assertions.assertTrue(hit.hasNext());
        Assertions.assertEquals(headers[3], hit.next(), "3");
        Assertions.assertFalse(hit.hasNext());

        final Iterator<Header> hit2 = new BasicHeaderIterator(headers, null);
        Assertions.assertTrue(hit2.hasNext());
        Assertions.assertThrows(UnsupportedOperationException.class, () -> hit2.remove());
    }
}

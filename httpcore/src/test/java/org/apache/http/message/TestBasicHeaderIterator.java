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

import junit.framework.TestCase;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;


/**
 * Tests for {@link BasicHeaderIterator}.
 *
 */
public class TestBasicHeaderIterator extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestBasicHeaderIterator(String testName) {
        super(testName);
    }

    // ------------------------------------------------------- TestCase Methods

    public void testAllSame() {
        Header[] headers = new Header[]{
            new BasicHeader("Name", "value0"),
            new BasicHeader("nAme", "value1, value1.1"),
            new BasicHeader("naMe", "value2=whatever"),
            new BasicHeader("namE", "value3;tag=nil"),
        };

        // without filter
        HeaderIterator hit = new BasicHeaderIterator(headers, null);
        assertTrue(hit.hasNext());
        assertEquals("0", headers[0], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("1", headers[1], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("2", headers[2], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("3", headers[3], hit.nextHeader());
        assertFalse(hit.hasNext());

        // with filter
        hit = new BasicHeaderIterator(headers, "name");
        assertTrue(hit.hasNext());
        assertEquals("0", headers[0], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("1", headers[1], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("2", headers[2], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("3", headers[3], hit.nextHeader());
        assertFalse(hit.hasNext());
    }


    public void testFirstLastOneNone() {
        Header[] headers = new Header[]{
            new BasicHeader("match"   , "value0"),
            new BasicHeader("mismatch", "value1, value1.1"),
            new BasicHeader("single"  , "value2=whatever"),
            new BasicHeader("match"   , "value3;tag=nil"),
        };

        // without filter
        HeaderIterator hit = new BasicHeaderIterator(headers, null);
        assertTrue(hit.hasNext());
        assertEquals("0", headers[0], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("1", headers[1], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("2", headers[2], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("3", headers[3], hit.nextHeader());
        assertFalse(hit.hasNext());

        // with filter, first & last
        hit = new BasicHeaderIterator(headers, "match");
        assertTrue(hit.hasNext());
        assertEquals("0", headers[0], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("3", headers[3], hit.nextHeader());
        assertFalse(hit.hasNext());

        // with filter, one match
        hit = new BasicHeaderIterator(headers, "single");
        assertTrue(hit.hasNext());
        assertEquals("2", headers[2], hit.nextHeader());
        assertFalse(hit.hasNext());

        // with filter, no match
        hit = new BasicHeaderIterator(headers, "way-off");
        assertFalse(hit.hasNext());
    }


    public void testInterspersed() {
        Header[] headers = new Header[]{
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
        HeaderIterator hit = new BasicHeaderIterator(headers, null);
        assertTrue(hit.hasNext());
        assertEquals("0", headers[0], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("1", headers[1], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("2", headers[2], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("3", headers[3], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("4", headers[4], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("5", headers[5], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("6", headers[6], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("7", headers[7], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("8", headers[8], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("9", headers[9], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("a", headers[10], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("b", headers[11], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("c", headers[12], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("d", headers[13], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("e", headers[14], hit.nextHeader());
        assertFalse(hit.hasNext());

        // yellow 0, 5, 9, 11, 13
        hit = new BasicHeaderIterator(headers, "Yellow");
        assertTrue(hit.hasNext());
        assertEquals("0", headers[0], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("5", headers[5], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("9", headers[9], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("b", headers[11], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("d", headers[13], hit.nextHeader());
        assertFalse(hit.hasNext());

        // maroon 1, 6, 7, 8, 10
        hit = new BasicHeaderIterator(headers, "marOOn");
        assertTrue(hit.hasNext());
        assertEquals("1", headers[1], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("6", headers[6], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("7", headers[7], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("8", headers[8], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("a", headers[10], hit.nextHeader());
        assertFalse(hit.hasNext());

        // orange 2, 3, 4, 12, 14
        hit = new BasicHeaderIterator(headers, "OranGe");
        assertTrue(hit.hasNext());
        assertEquals("2", headers[2], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("3", headers[3], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("4", headers[4], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("b", headers[12], hit.nextHeader());
        assertTrue(hit.hasNext());
        assertEquals("e", headers[14], hit.nextHeader());
        assertFalse(hit.hasNext());
    }


    public void testInvalid() {

        HeaderIterator hit = null;
        try {
            hit = new BasicHeaderIterator(null, "whatever");
            fail("null headers not detected");
        } catch (IllegalArgumentException iax) {
            // expected
        }

        // this is not invalid
        hit = new BasicHeaderIterator(new Header[0], "whatever");
        assertFalse(hit.hasNext());

        // but this is
        try {
            hit.nextHeader();
            fail("next beyond end not detected");
        } catch (NoSuchElementException nsx) {
            // expected
        }
    }


    public void testRemaining() {
        // to satisfy Clover and take coverage to 100%

        Header[] headers = new Header[]{
            new BasicHeader("Name", "value0"),
            new BasicHeader("nAme", "value1, value1.1"),
            new BasicHeader("naMe", "value2=whatever"),
            new BasicHeader("namE", "value3;tag=nil"),
        };

        // without filter, using plain next()
        HeaderIterator hit = new BasicHeaderIterator(headers, null);
        assertTrue(hit.hasNext());
        assertEquals("0", headers[0], hit.next());
        assertTrue(hit.hasNext());
        assertEquals("1", headers[1], hit.next());
        assertTrue(hit.hasNext());
        assertEquals("2", headers[2], hit.next());
        assertTrue(hit.hasNext());
        assertEquals("3", headers[3], hit.next());
        assertFalse(hit.hasNext());

        hit = new BasicHeaderIterator(headers, null);
        assertTrue(hit.hasNext());
        try {
            hit.remove();
            fail("remove not detected");
        } catch (UnsupportedOperationException uox) {
            // expected
        }

        assertTrue("no next", ((BasicHeaderIterator)hit).findNext(-3) < 0);
    }
}

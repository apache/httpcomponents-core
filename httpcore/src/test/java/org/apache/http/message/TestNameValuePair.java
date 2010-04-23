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

import junit.framework.TestCase;

import org.apache.http.NameValuePair;

/**
 * Unit tests for {@link NameValuePair}.
 *
 */
public class TestNameValuePair extends TestCase {

    public TestNameValuePair(String testName) {
        super(testName);
    }

    public void testConstructor() {
        NameValuePair param = new BasicNameValuePair("name", "value");
        assertEquals("name", param.getName());
        assertEquals("value", param.getValue());
    }

    public void testInvalidName() {
        try {
            new BasicNameValuePair(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
    }

    public void testHashCode() {
        NameValuePair param1 = new BasicNameValuePair("name1", "value1");
        NameValuePair param2 = new BasicNameValuePair("name2", "value2");
        NameValuePair param3 = new BasicNameValuePair("name1", "value1");
        assertTrue(param1.hashCode() != param2.hashCode());
        assertTrue(param1.hashCode() == param3.hashCode());
    }

    public void testEquals() {
        NameValuePair param1 = new BasicNameValuePair("name1", "value1");
        NameValuePair param2 = new BasicNameValuePair("name2", "value2");
        NameValuePair param3 = new BasicNameValuePair("name1", "value1");
        assertFalse(param1.equals(param2));
        assertFalse(param1.equals(null));
        assertFalse(param1.equals("name1 = value1"));
        assertTrue(param1.equals(param1));
        assertTrue(param2.equals(param2));
        assertTrue(param1.equals(param3));
    }

    public void testToString() {
        NameValuePair param1 = new BasicNameValuePair("name1", "value1");
        assertEquals("name1=value1", param1.toString());
        NameValuePair param2 = new BasicNameValuePair("name1", null);
        assertEquals("name1", param2.toString());
    }

    public void testCloning() throws Exception {
        BasicNameValuePair orig = new BasicNameValuePair("name1", "value1");
        BasicNameValuePair clone = (BasicNameValuePair) orig.clone();
        assertEquals(orig, clone);
    }

}

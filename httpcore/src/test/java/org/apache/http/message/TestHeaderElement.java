/*
 * $Header: /home/jerenkrantz/tmp/commons/commons-convert/cvs/home/cvs/jakarta-commons//httpclient/src/test/org/apache/commons/httpclient/TestHeaderElement.java,v 1.7 2004/02/22 18:08:49 olegk Exp $
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
 * [Additional notices, if required by prior licensing conditions]
 *
 */

package org.apache.http.message;

import org.apache.http.HeaderElement;
import org.apache.http.NameValuePair;
import org.junit.Assert;
import org.junit.Test;

/**
 * Simple tests for {@link HeaderElement}.
 */
public class TestHeaderElement {

    @Test
    public void testConstructor3() throws Exception {
        HeaderElement element = new BasicHeaderElement("name", "value",
                new NameValuePair[] {
                    new BasicNameValuePair("param1", "value1"),
                    new BasicNameValuePair("param2", "value2")
                } );
        Assert.assertEquals("name", element.getName());
        Assert.assertEquals("value", element.getValue());
        Assert.assertEquals(2, element.getParameters().length);
        Assert.assertEquals("value1", element.getParameterByName("param1").getValue());
        Assert.assertEquals("value2", element.getParameterByName("param2").getValue());
    }

    @Test
    public void testConstructor2() throws Exception {
        HeaderElement element = new BasicHeaderElement("name", "value");
        Assert.assertEquals("name", element.getName());
        Assert.assertEquals("value", element.getValue());
        Assert.assertEquals(0, element.getParameters().length);
    }


    @Test
    public void testInvalidName() {
        try {
            new BasicHeaderElement(null, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
    }

    @Test
    public void testParamByName() throws Exception {
        String s = "name = value; param1 = value1; param2 = value2";
        HeaderElement element = BasicHeaderValueParser.parseHeaderElement(s, null);
        Assert.assertEquals("value1", element.getParameterByName("param1").getValue());
        Assert.assertEquals("value2", element.getParameterByName("param2").getValue());
        Assert.assertNull(element.getParameterByName("param3"));
        try {
            element.getParameterByName(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
    }

    @Test
    public void testHashCode() {
        HeaderElement element1 = new BasicHeaderElement("name", "value",
                new NameValuePair[] {
                    new BasicNameValuePair("param1", "value1"),
                    new BasicNameValuePair("param2", "value2")
                } );
        HeaderElement element2 = new BasicHeaderElement("name", "value",
                new NameValuePair[] {
                    new BasicNameValuePair("param2", "value2"),
                    new BasicNameValuePair("param1", "value1")
                } );
        HeaderElement element3 = new BasicHeaderElement("name", "value");
        HeaderElement element4 = new BasicHeaderElement("name", "value");
        HeaderElement element5 = new BasicHeaderElement("name", "value",
                new NameValuePair[] {
                    new BasicNameValuePair("param1", "value1"),
                    new BasicNameValuePair("param2", "value2")
                } );
        Assert.assertTrue(element1.hashCode() != element2.hashCode());
        Assert.assertTrue(element1.hashCode() != element3.hashCode());
        Assert.assertTrue(element2.hashCode() != element3.hashCode());
        Assert.assertTrue(element3.hashCode() == element4.hashCode());
        Assert.assertTrue(element1.hashCode() == element5.hashCode());
    }

    @Test
    public void testEquals() {
        HeaderElement element1 = new BasicHeaderElement("name", "value",
                new NameValuePair[] {
                    new BasicNameValuePair("param1", "value1"),
                    new BasicNameValuePair("param2", "value2")
                } );
        HeaderElement element2 = new BasicHeaderElement("name", "value",
                new NameValuePair[] {
                    new BasicNameValuePair("param2", "value2"),
                    new BasicNameValuePair("param1", "value1")
                } );
        HeaderElement element3 = new BasicHeaderElement("name", "value");
        HeaderElement element4 = new BasicHeaderElement("name", "value");
        HeaderElement element5 = new BasicHeaderElement("name", "value",
                new NameValuePair[] {
                    new BasicNameValuePair("param1", "value1"),
                    new BasicNameValuePair("param2", "value2")
                } );
        Assert.assertTrue(element1.equals(element1));
        Assert.assertTrue(!element1.equals(element2));
        Assert.assertTrue(!element1.equals(element3));
        Assert.assertTrue(!element2.equals(element3));
        Assert.assertTrue(element3.equals(element4));
        Assert.assertTrue(element1.equals(element5));
        Assert.assertFalse(element1.equals(null));
        Assert.assertFalse(element1.equals("name = value; param1 = value1; param2 = value2"));
    }

    @Test
    public void testToString() {
        String s = "name=value; param1=value1; param2=value2";
        HeaderElement element = BasicHeaderValueParser.parseHeaderElement(s, null);
        Assert.assertEquals(s, element.toString());
        s = "name; param1=value1; param2=value2";
        element = BasicHeaderValueParser.parseHeaderElement(s, null);
        Assert.assertEquals(s, element.toString());
    }

    @Test
    public void testCloning() throws Exception {
        BasicHeaderElement orig = new BasicHeaderElement("name", "value",
                new NameValuePair[] {
                    new BasicNameValuePair("param1", "value1"),
                    new BasicNameValuePair("param2", "value2")
                } );
        BasicHeaderElement clone = (BasicHeaderElement) orig.clone();
        Assert.assertEquals(orig, clone);
    }

}

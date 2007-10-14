/*
 * $HeadURL$
 * $Revision$
 * $Date$
 * 
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

import java.util.Iterator;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.HeaderGroup;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit tests for {@link HeaderGroup}.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class TestHeaderGroup extends TestCase {

    public TestHeaderGroup(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestHeaderGroup.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestHeaderGroup.class);
    }

    public void testConstructor() {
        HeaderGroup headergroup = new HeaderGroup();
        assertNotNull(headergroup.getAllHeaders());
        assertEquals(0, headergroup.getAllHeaders().length);
    }
    
    public void testClear() {
        HeaderGroup headergroup = new HeaderGroup();
        headergroup.addHeader(new BasicHeader("name", "value"));
        assertEquals(1, headergroup.getAllHeaders().length);
        headergroup.clear();
        assertEquals(0, headergroup.getAllHeaders().length);
    }
    
    public void testAddRemoveHeader() {
        HeaderGroup headergroup = new HeaderGroup();
        Header header = new BasicHeader("name", "value");
        headergroup.addHeader(header);
        headergroup.addHeader(null);
        assertEquals(1, headergroup.getAllHeaders().length);
        headergroup.removeHeader(header);
        headergroup.removeHeader(null);
        assertEquals(0, headergroup.getAllHeaders().length);
    }

    public void testUpdateHeader() {
        HeaderGroup headergroup = new HeaderGroup();
        Header header1 = new BasicHeader("name1", "value1");
        Header header2 = new BasicHeader("name2", "value2");
        Header header3 = new BasicHeader("name3", "value3");
        headergroup.addHeader(header1);
        headergroup.addHeader(header2);
        headergroup.addHeader(header3);
        headergroup.updateHeader(new BasicHeader("name2", "newvalue"));
        headergroup.updateHeader(new BasicHeader("name4", "value4"));
        headergroup.updateHeader(null);
        assertEquals(4, headergroup.getAllHeaders().length);
        assertEquals("newvalue", headergroup.getFirstHeader("name2").getValue());
    }

    public void testSetHeaders() {
        HeaderGroup headergroup = new HeaderGroup();
        Header header1 = new BasicHeader("name1", "value1");
        Header header2 = new BasicHeader("name2", "value2");
        Header header3 = new BasicHeader("name3", "value3");
        headergroup.addHeader(header1);
        headergroup.setHeaders(new Header[] { header2, header3 });
        assertEquals(2, headergroup.getAllHeaders().length);
        assertEquals(0, headergroup.getHeaders("name1").length);
        assertFalse(headergroup.containsHeader("name1"));
        assertEquals(1, headergroup.getHeaders("name2").length);
        assertTrue(headergroup.containsHeader("name2"));
        assertEquals(1, headergroup.getHeaders("name3").length);
        assertTrue(headergroup.containsHeader("name3"));
        headergroup.setHeaders(null);
        assertEquals(0, headergroup.getAllHeaders().length);
    }

    public void testFirstLastHeaders() {
        HeaderGroup headergroup = new HeaderGroup();
        Header header1 = new BasicHeader("name", "value1");
        Header header2 = new BasicHeader("name", "value2");
        Header header3 = new BasicHeader("name", "value3");
        headergroup.setHeaders(new Header[] { header1, header2, header3 });
        
        assertNull(headergroup.getFirstHeader("whatever"));
        assertNull(headergroup.getLastHeader("whatever"));
        
        assertEquals("value1", headergroup.getFirstHeader("name").getValue());
        assertEquals("value3", headergroup.getLastHeader("name").getValue());
    }

    public void testCondensedHeader() {
        HeaderGroup headergroup = new HeaderGroup();
        assertNull(headergroup.getCondensedHeader("name"));
        
        Header header1 = new BasicHeader("name", "value1");
        Header header2 = new BasicHeader("name", "value2");
        Header header3 = new BasicHeader("name", "value3");
        headergroup.setHeaders(new Header[] { header1, header2, header3 });
        
        assertEquals("value1, value2, value3", headergroup.getCondensedHeader("name").getValue());

        headergroup.setHeaders(new Header[] { header1 });
        assertEquals(header1, headergroup.getCondensedHeader("name"));
    }

    public void testIterator() {
        HeaderGroup headergroup = new HeaderGroup();
        Iterator i = headergroup.iterator();
        assertNotNull(i);
        assertFalse(i.hasNext());
    }
}

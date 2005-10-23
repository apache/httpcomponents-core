/*
 * $HeadURL$
 * $Revision$
 * $Date$
 * 
 * ====================================================================
 *
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.util;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit tests for {@link CharArrayBuffer}.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class TestCharArrayBuffer extends TestCase {

    public TestCharArrayBuffer(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestCharArrayBuffer.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestCharArrayBuffer.class);
    }

    public void testConstructor() throws Exception {
    	CharArrayBuffer buffer = new CharArrayBuffer(16);
    	assertEquals(16, buffer.capacity()); 
    	assertEquals(0, buffer.length());
    	assertEquals(16, buffer.getBuffer().length);
    	try {
    		new CharArrayBuffer(-1);
    		fail("IllegalArgumentException should have been thrown");
    	} catch (IllegalArgumentException ex) {
    		// expected
    	}
    }
    
    public void testSimpleAppend() throws Exception {
    	CharArrayBuffer buffer = new CharArrayBuffer(16);
    	assertEquals(16, buffer.capacity()); 
    	assertEquals(0, buffer.length());
    	char[] b1 = buffer.toCharArray();
    	assertNotNull(b1);
    	assertEquals(0, b1.length);
    	assertTrue(buffer.isEmpty());
    	
    	char[] tmp = new char[] { '1', '2', '3', '4'};
    	buffer.append(tmp, 0, tmp.length);
    	assertEquals(16, buffer.capacity()); 
    	assertEquals(4, buffer.length());
    	assertFalse(buffer.isEmpty());
    	
    	char[] b2 = buffer.toCharArray();
    	assertNotNull(b2);
    	assertEquals(4, b2.length);
    	for (int i = 0; i < tmp.length; i++) {
        	assertEquals(tmp[i], b2[i]);
        	assertEquals(tmp[i], buffer.charAt(i));
    	}
    	assertEquals("1234", buffer.toString());
    	
    	buffer.clear();
    	assertEquals(16, buffer.capacity()); 
    	assertEquals(0, buffer.length());
    	assertTrue(buffer.isEmpty());
    }
    
    public void testExpandAppend() throws Exception {
    	CharArrayBuffer buffer = new CharArrayBuffer(4);
    	assertEquals(4, buffer.capacity()); 
    	
    	char[] tmp = new char[] { '1', '2', '3', '4'};
    	buffer.append(tmp, 0, 2);
    	buffer.append(tmp, 0, 4);
    	buffer.append(tmp, 0, 0);

    	assertEquals(8, buffer.capacity()); 
    	assertEquals(6, buffer.length());
    	
    	buffer.append(tmp, 0, 4);
    	
    	assertEquals(16, buffer.capacity()); 
    	assertEquals(10, buffer.length());
    	
    	assertEquals("1212341234", buffer.toString());
    }

    public void testAppendString() throws Exception {
    	CharArrayBuffer buffer = new CharArrayBuffer(8);
    	buffer.append("stuff");
    	buffer.append(" and more stuff");
    	assertEquals("stuff and more stuff", buffer.toString());
    }
    
    public void testAppendNullString() throws Exception {
    	CharArrayBuffer buffer = new CharArrayBuffer(8);
    	buffer.append(null);
    	assertEquals("null", buffer.toString());
    }
    
    public void testAppendSingleChar() throws Exception {
    	CharArrayBuffer buffer = new CharArrayBuffer(4);
    	buffer.append('1');
    	buffer.append('2');
    	buffer.append('3');
    	buffer.append('4');
    	buffer.append('5');
    	buffer.append('6');
    	assertEquals("123456", buffer.toString());
    }
    
    public void testInvalidAppend() throws Exception {
    	CharArrayBuffer buffer = new CharArrayBuffer(4);
    	buffer.append(null, 0, 0);

    	char[] tmp = new char[] { '1', '2', '3', '4'};
    	try {
        	buffer.append(tmp, -1, 0);
    		fail("IndexOutOfBoundsException should have been thrown");
    	} catch (IndexOutOfBoundsException ex) {
    		// expected
    	}
    	try {
        	buffer.append(tmp, 0, -1);
    		fail("IndexOutOfBoundsException should have been thrown");
    	} catch (IndexOutOfBoundsException ex) {
    		// expected
    	}
    	try {
        	buffer.append(tmp, 0, 8);
    		fail("IndexOutOfBoundsException should have been thrown");
    	} catch (IndexOutOfBoundsException ex) {
    		// expected
    	}
    	try {
        	buffer.append(tmp, 10, Integer.MAX_VALUE);
    		fail("IndexOutOfBoundsException should have been thrown");
    	} catch (IndexOutOfBoundsException ex) {
    		// expected
    	}
    	try {
        	buffer.append(tmp, 2, 4);
    		fail("IndexOutOfBoundsException should have been thrown");
    	} catch (IndexOutOfBoundsException ex) {
    		// expected
    	}
    }

    public void testSetLength() throws Exception {
    	CharArrayBuffer buffer = new CharArrayBuffer(4);
    	buffer.setLength(2);
    	assertEquals(2, buffer.length());
    }
    
    public void testSetInvalidLength() throws Exception {
    	CharArrayBuffer buffer = new CharArrayBuffer(4);
    	try {
        	buffer.setLength(-2);
    		fail("IndexOutOfBoundsException should have been thrown");
    	} catch (IndexOutOfBoundsException ex) {
    		// expected
    	}
    	try {
        	buffer.setLength(200);
    		fail("IndexOutOfBoundsException should have been thrown");
    	} catch (IndexOutOfBoundsException ex) {
    		// expected
    	}
    }

    public void testEnsureCapacity() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(4);
        buffer.ensureCapacity(2);
        assertEquals(4, buffer.capacity());
        buffer.ensureCapacity(8);
        assertEquals(8, buffer.capacity());
    }
        
}

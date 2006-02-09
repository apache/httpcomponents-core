/*
 * $HeadURL$
 * $Revision$
 * $Date$
 * ====================================================================
 *
 *  Copyright 1999-2006 The Apache Software Foundation
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

import org.apache.http.io.CharArrayBuffer;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit tests for {@link ParameterFormatter}.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class TestNumUtils extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestNumUtils(String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestNumUtils.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestNumUtils.class);
    }

    public void testBasicParseUnsignedInt() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append("  100 ");
        int num = NumUtils.parseUnsignedInt(buffer, 0, buffer.length());
        assertEquals(100, num);

        buffer.clear();
        buffer.append("132");
        num = NumUtils.parseUnsignedInt(buffer, 0, buffer.length());
        assertEquals(132, num);

        buffer.clear();
        buffer.append("010101  ");
        num = NumUtils.parseUnsignedBinInt(buffer, 0, buffer.length());
        assertEquals(21, num);

        buffer.clear();
        buffer.append("FFFF");
        num = NumUtils.parseUnsignedHexInt(buffer, 0, buffer.length());
        assertEquals(65535, num);

        buffer.clear();
        buffer.append("aAaA");
        num = NumUtils.parseUnsignedHexInt(buffer, 0, buffer.length());
        assertEquals(43690, num);

        buffer.clear();
        buffer.append("0");
        num = NumUtils.parseUnsignedInt(buffer, 0, buffer.length());
        assertEquals(0, num);
    }

    public void testInvalidInputParseUnsignedInt() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append("   ");
        try {
            NumUtils.parseUnsignedInt(buffer, 0, buffer.length());
            fail("NumberFormatException should have been thrown");
        } catch (NumberFormatException ex) {
            // expected
        }
        buffer.clear();
        buffer.append("");
        try {
            NumUtils.parseUnsignedInt(buffer, 0, buffer.length());
            fail("NumberFormatException should have been thrown");
        } catch (NumberFormatException ex) {
            // expected
        }
        buffer.clear();
        buffer.append("1000");
        try {
            NumUtils.parseUnsignedInt(null, 0, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            NumUtils.parseUnsignedInt(buffer, -1, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            NumUtils.parseUnsignedInt(buffer, 0, 1000);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
        try {
            NumUtils.parseUnsignedInt(buffer, 2, 1);
            fail("IllegalArgumentException should have been thrown");
        } catch (IndexOutOfBoundsException ex) {
            // expected
        }
    }

    public void testInvalidRadixParseUnsignedInt() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append("10000");
        try {
            NumUtils.parseUnsignedInt(buffer, 0, buffer.length(), 1);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            NumUtils.parseUnsignedInt(buffer, 0, buffer.length(),
                    Character.MAX_RADIX + 1);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }
    
    public void testInvalidNumberParseUnsignedInt() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append("aa");
        try {
            NumUtils.parseUnsignedInt(buffer, 0, buffer.length());
            fail("NumberFormatException should have been thrown");
        } catch (NumberFormatException ex) {
            // expected
        }
        buffer.clear();
        buffer.append("3");
        try {
            NumUtils.parseUnsignedBinInt(buffer, 0, buffer.length());
            fail("NumberFormatException should have been thrown");
        } catch (NumberFormatException ex) {
            // expected
        }
        buffer.clear();
        buffer.append("-3");
        try {
            NumUtils.parseUnsignedInt(buffer, 0, buffer.length());
            fail("NumberFormatException should have been thrown");
        } catch (NumberFormatException ex) {
            // expected
        }
        buffer.clear();
        buffer.append("3 3");
        try {
            NumUtils.parseUnsignedInt(buffer, 0, buffer.length());
            fail("NumberFormatException should have been thrown");
        } catch (NumberFormatException ex) {
            // expected
        }
    }

    public void testParseUnsignedIntLargeNumbers() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append(Integer.toString(Integer.MAX_VALUE));
        int num = NumUtils.parseUnsignedInt(buffer, 0, buffer.length());
        assertEquals(Integer.MAX_VALUE, num);

        buffer.clear();
        buffer.append(Integer.toBinaryString(Integer.MAX_VALUE));
        num = NumUtils.parseUnsignedBinInt(buffer, 0, buffer.length());
        assertEquals(Integer.MAX_VALUE, num);
        
        buffer.clear();
        buffer.append(Integer.toHexString(Integer.MAX_VALUE));
        num = NumUtils.parseUnsignedHexInt(buffer, 0, buffer.length());
        assertEquals(Integer.MAX_VALUE, num);
    }
    
    public void testParseUnsignedIntLargeNumberOverflow() throws Exception {
        CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append(Long.toString((long)Integer.MAX_VALUE + 1));
        try {
            NumUtils.parseUnsignedInt(buffer, 0, buffer.length());
            fail("NumberFormatException should have been thrown");
        } catch (NumberFormatException ex) {
            // expected
        }
    }
}

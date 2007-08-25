/*
 * $HeadURL$
 * $Revision$
 * $Date$
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

import org.apache.http.Header;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.util.CharArrayBuffer;

import junit.framework.*;

/**
 * Tests for {@link BasicLineFormatter}.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 */
public class TestBasicLineFormatter extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestBasicLineFormatter(String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestBasicLineFormatter.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestBasicLineFormatter.class);
    }



    public void testHeaderFormatting() throws Exception {
        Header header1 = new BasicHeader("name", "value");
        String s = BasicLineFormatter.formatHeader(header1, null);
        assertEquals("name: value", s);
        Header header2 = new BasicHeader("name", null);
        s = BasicLineFormatter.formatHeader(header2, null); 
        assertEquals("name: ", s);
    }
    
    public void testHeaderFormattingInvalidInput() throws Exception {
        try {
            BasicLineFormatter.formatHeader
                (null, BasicLineFormatter.DEFAULT);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            BasicLineFormatter.DEFAULT.formatHeader
                (new CharArrayBuffer(10), null);
            fail("IllegalArgumentException should habe been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }
     
}

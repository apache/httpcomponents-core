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

package org.apache.http.protocol;

import junit.framework.TestCase;

// the name of this test is historic, the implementation classes of HttpContext
// have been renamed to BasicHttpContext and SyncBasicHttpContext
public class TestHttpExecutionContext extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestHttpExecutionContext(String testName) {
        super(testName);
    }

    // ------------------------------------------------------- TestCase Methods

    public void testContextOperations() {
        HttpContext parentContext = new SyncBasicHttpContext(null);
        HttpContext currentContext = new SyncBasicHttpContext(parentContext);

        parentContext.setAttribute("param1", "1");
        parentContext.setAttribute("param2", "2");
        currentContext.setAttribute("param3", "3");
        currentContext.setAttribute("param2", "4");

        assertEquals("1", parentContext.getAttribute("param1"));
        assertEquals("2", parentContext.getAttribute("param2"));
        assertEquals(null, parentContext.getAttribute("param3"));

        assertEquals("1", currentContext.getAttribute("param1"));
        assertEquals("4", currentContext.getAttribute("param2"));
        assertEquals("3", currentContext.getAttribute("param3"));
        assertEquals(null, currentContext.getAttribute("param4"));

        currentContext.removeAttribute("param1");
        currentContext.removeAttribute("param2");
        currentContext.removeAttribute("param3");
        currentContext.removeAttribute("param4");

        assertEquals("1", currentContext.getAttribute("param1"));
        assertEquals("2", currentContext.getAttribute("param2"));
        assertEquals(null, currentContext.getAttribute("param3"));
        assertEquals(null, currentContext.getAttribute("param4"));
    }

    public void testEmptyContextOperations() {
        HttpContext currentContext = new SyncBasicHttpContext(null);
        assertEquals(null, currentContext.getAttribute("param1"));
        currentContext.removeAttribute("param1");
        assertEquals(null, currentContext.getAttribute("param1"));
    }

    public void testContextInvalidInput() throws Exception {
        HttpContext currentContext = new SyncBasicHttpContext(null);
        try {
            currentContext.setAttribute(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            currentContext.getAttribute(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            currentContext.removeAttribute(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

}

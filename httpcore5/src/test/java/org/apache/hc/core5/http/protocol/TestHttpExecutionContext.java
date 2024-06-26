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

package org.apache.hc.core5.http.protocol;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

// the name of this test is historic, the implementation classes of HttpContext
// have been renamed to BasicHttpContext and SyncBasicHttpContext
class TestHttpExecutionContext {

    @Test
    void testContextOperations() {
        final HttpCoreContext parentContext = new HttpCoreContext();
        final HttpCoreContext currentContext = new HttpCoreContext(parentContext);

        parentContext.setAttribute("param1", "1");
        parentContext.setAttribute("param2", "2");
        currentContext.setAttribute("param3", "3");
        currentContext.setAttribute("param2", "4");

        Assertions.assertEquals("1", parentContext.getAttribute("param1"));
        Assertions.assertEquals("2", parentContext.getAttribute("param2"));
        Assertions.assertNull(parentContext.getAttribute("param3"));

        Assertions.assertEquals("1", currentContext.getAttribute("param1"));
        Assertions.assertEquals("4", currentContext.getAttribute("param2"));
        Assertions.assertEquals("3", currentContext.getAttribute("param3"));
        Assertions.assertNull(currentContext.getAttribute("param4"));

        currentContext.removeAttribute("param1");
        currentContext.removeAttribute("param2");
        currentContext.removeAttribute("param3");
        currentContext.removeAttribute("param4");

        Assertions.assertEquals("1", currentContext.getAttribute("param1"));
        Assertions.assertEquals("2", currentContext.getAttribute("param2"));
        Assertions.assertNull(currentContext.getAttribute("param3"));
        Assertions.assertNull(currentContext.getAttribute("param4"));
    }

    @Test
    void testEmptyContextOperations() {
        final HttpCoreContext currentContext = new HttpCoreContext();
        Assertions.assertNull(currentContext.getAttribute("param1"));
        currentContext.removeAttribute("param1");
        Assertions.assertNull(currentContext.getAttribute("param1"));
    }

}

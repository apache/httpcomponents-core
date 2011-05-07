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

package org.apache.http.params;

import junit.framework.TestCase;

/**
 * Unit tests for {@link BasicHttpParams}.
 *
 */
public class TestBasicHttpParams extends TestCase {

    public TestBasicHttpParams(String testName) {
        super(testName);
    }

    public void testRemoveParam() {
        BasicHttpParams params = new BasicHttpParams();
        params.setParameter("param1", "paramValue1");
        assertTrue("The parameter should be removed successfully",
                params.removeParameter("param1"));
        assertFalse("The parameter should not be present",
                params.removeParameter("param1"));

        //try a remove from an empty params
        params = new BasicHttpParams();
        assertFalse("The parameter should not be present",
                params.removeParameter("param1"));
    }

}

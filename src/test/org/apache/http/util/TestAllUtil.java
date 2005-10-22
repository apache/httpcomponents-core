/*
 * $HeadURL$
 * $Revision$
 * $Date$
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

import junit.framework.*;

public class TestAllUtil extends TestCase {

    public TestAllUtil(String testName) {
        super(testName);
    }

    public static Test suite() {
        TestSuite suite = new TestSuite();
        suite.addTest(TestLangUtils.suite());
        suite.addTest(TestExceptionUtils.suite());
        suite.addTest(TestEncodingUtils.suite());
        suite.addTest(TestParameterParser.suite());
        suite.addTest(TestParameterFormatter.suite());
        suite.addTest(TestHeadersParser.suite());
        suite.addTest(TestByteArrayBuffer.suite());
        suite.addTest(TestCharArrayBuffer.suite());
        suite.addTest(TestDateUtils.suite());
        suite.addTest(TestEntityUtils.suite());
        return suite;
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestAllUtil.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

}

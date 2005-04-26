/*
 * $HeadURL$
 * $Revision$
 * $Date$
 * ====================================================================
 *
 *  Copyright 2002-2004 The Apache Software Foundation
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

package org.apache.http.impl;

import org.apache.http.Header;
import org.apache.http.HttpMutableEntity;
import org.apache.http.HttpMutableResponse;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestDefaultConnectionReuseStrategy extends TestCase {

    public TestDefaultConnectionReuseStrategy(String testName) {
        super(testName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestDefaultConnectionReuseStrategy.class);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestDefaultConnectionReuseStrategy.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public void testIllegalResponseArg() throws Exception {
        ConnectionReuseStrategy s = new DefaultConnectionReuseStrategy();
        try {
            s.keepAlive(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testNoContentLengthResponse() throws Exception {
        HttpMutableEntity entity = new BasicHttpEntity();
        entity.setChunked(false);
        entity.setContentLength(-1);
        StatusLine statusline = new StatusLine(HttpVersion.HTTP_1_0, 200, "OK");
        HttpMutableResponse response = new BasicHttpResponse(statusline);
        response.setEntity(entity);

        ConnectionReuseStrategy s = new DefaultConnectionReuseStrategy();
        assertFalse(s.keepAlive(response));
    }

    public void testExplicitClose() throws Exception {
        HttpMutableEntity entity = new BasicHttpEntity();
        entity.setChunked(true);
        entity.setContentLength(-1);
        // Use HTTP 1.1
        StatusLine statusline = new StatusLine(HttpVersion.HTTP_1_1, 200, "OK");
        HttpMutableResponse response = new BasicHttpResponse(statusline);
        response.addHeader(new Header("Connection", "close"));
        response.setEntity(entity);

        ConnectionReuseStrategy s = new DefaultConnectionReuseStrategy();
        assertFalse(s.keepAlive(response));
    }
    
    public void testExplicitKeepAlive() throws Exception {
        HttpMutableEntity entity = new BasicHttpEntity();
        entity.setChunked(false);
        entity.setContentLength(10);
        // Use HTTP 1.0
        StatusLine statusline = new StatusLine(HttpVersion.HTTP_1_0, 200, "OK"); 
        HttpMutableResponse response = new BasicHttpResponse(statusline);
        response.addHeader(new Header("Connection", "keep-alive"));
        response.setEntity(entity);

        ConnectionReuseStrategy s = new DefaultConnectionReuseStrategy();
        assertTrue(s.keepAlive(response));
    }

    public void testHTTP10Default() throws Exception {
        StatusLine statusline = new StatusLine(HttpVersion.HTTP_1_0, 200, "OK"); 
        HttpMutableResponse response = new BasicHttpResponse(statusline);

        ConnectionReuseStrategy s = new DefaultConnectionReuseStrategy();
        assertFalse(s.keepAlive(response));
    }
    
    public void testHTTP11Default() throws Exception {
        StatusLine statusline = new StatusLine(HttpVersion.HTTP_1_1, 200, "OK"); 
        HttpMutableResponse response = new BasicHttpResponse(statusline);

        ConnectionReuseStrategy s = new DefaultConnectionReuseStrategy();
        assertTrue(s.keepAlive(response));
    }

    public void testFutureHTTP() throws Exception {
        StatusLine statusline = new StatusLine(new HttpVersion(3, 45), 200, "OK"); 
        HttpMutableResponse response = new BasicHttpResponse(statusline);

        ConnectionReuseStrategy s = new DefaultConnectionReuseStrategy();
        assertTrue(s.keepAlive(response));
    }
    
    public void testBrokenConnectionDirective1() throws Exception {
        // Use HTTP 1.0
        StatusLine statusline = new StatusLine(HttpVersion.HTTP_1_0, 200, "OK"); 
        HttpMutableResponse response = new BasicHttpResponse(statusline);
        response.addHeader(new Header("Connection", "keep--alive"));

        ConnectionReuseStrategy s = new DefaultConnectionReuseStrategy();
        assertFalse(s.keepAlive(response));
    }

    public void testBrokenConnectionDirective2() throws Exception {
        // Use HTTP 1.0
        StatusLine statusline = new StatusLine(HttpVersion.HTTP_1_0, 200, "OK"); 
        HttpMutableResponse response = new BasicHttpResponse(statusline);
        response.addHeader(new Header("Connection", null));

        ConnectionReuseStrategy s = new DefaultConnectionReuseStrategy();
        assertFalse(s.keepAlive(response));
    }
}


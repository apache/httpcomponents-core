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

package org.apache.http;

import org.apache.http.impl.NIOSocketFactory;
import org.apache.http.impl.OldIOSocketFactory;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit tests for {@link HttpHost}.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class TestHttpHost extends TestCase {

    public TestHttpHost(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestHttpHost.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestHttpHost.class);
    }

    protected void setUp() throws Exception {
        super.setUp();
        SocketFactory socketfactory = null;
        if (HttpRuntime.isNIOCapable()) {
            socketfactory = NIOSocketFactory.getSocketFactory();
        } else {
            socketfactory = OldIOSocketFactory.getSocketFactory();
        }
        final Protocol http = new Protocol("http", socketfactory, 80);
        Protocol.registerProtocol("http", http);
    }
    
    public void testConstructor() {
        Protocol http = Protocol.getProtocol("http");
        HttpHost host1 = new HttpHost("somehost");
        assertEquals("somehost", host1.getHostName()); 
        assertEquals(http.getDefaultPort(), host1.getPort()); 
        assertEquals(http, host1.getProtocol()); 
        HttpHost host2 = new HttpHost("somehost", 8080);
        assertEquals("somehost", host2.getHostName()); 
        assertEquals(8080, host2.getPort()); 
        assertEquals(http, host2.getProtocol()); 
        HttpHost host3 = new HttpHost("somehost", -1);
        assertEquals("somehost", host3.getHostName()); 
        assertEquals(http.getDefaultPort(), host3.getPort()); 
        assertEquals(http, host3.getProtocol()); 
        HttpHost host4 = new HttpHost("somehost", 8080, http);
        assertEquals("somehost", host4.getHostName()); 
        assertEquals(8080, host4.getPort()); 
        assertEquals(http, host4.getProtocol()); 
        try {
            HttpHost host = new HttpHost(null, -1, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
        try {
            HttpHost host = new HttpHost("somehost", -1, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            //expected
        }
    }
    
    public void testHashCode() {
        Protocol http = Protocol.getProtocol("http");
        Protocol myhttp = new Protocol("myhttp", 
                NIOSocketFactory.getSocketFactory(), 8080);
        HttpHost host1 = new HttpHost("somehost", 8080, http);
        HttpHost host2 = new HttpHost("somehost", 80, http);
        HttpHost host3 = new HttpHost("someotherhost", 8080, http);
        HttpHost host4 = new HttpHost("somehost", 80, http);
        HttpHost host5 = new HttpHost("SomeHost", 80, http);
        HttpHost host6 = new HttpHost("SomeHost", 80, myhttp);

        assertTrue(host1.hashCode() == host1.hashCode());
        assertTrue(host1.hashCode() != host2.hashCode());
        assertTrue(host1.hashCode() != host3.hashCode());
        assertTrue(host2.hashCode() == host4.hashCode());
        assertTrue(host2.hashCode() == host5.hashCode());
        assertTrue(host5.hashCode() != host6.hashCode());
    }
    
    public void testEquals() {
        Protocol http = Protocol.getProtocol("http");
        Protocol myhttp = new Protocol("myhttp", 
                NIOSocketFactory.getSocketFactory(), 8080);
        HttpHost host1 = new HttpHost("somehost", 8080, http);
        HttpHost host2 = new HttpHost("somehost", 80, http);
        HttpHost host3 = new HttpHost("someotherhost", 8080, http);
        HttpHost host4 = new HttpHost("somehost", 80, http);
        HttpHost host5 = new HttpHost("SomeHost", 80, http);
        HttpHost host6 = new HttpHost("SomeHost", 80, myhttp);

        assertTrue(host1.equals(host1));
        assertFalse(host1.equals(host2));
        assertFalse(host1.equals(host3));
        assertTrue(host2.equals(host4));
        assertTrue(host2.equals(host5));
        assertFalse(host5.equals(host6));
        assertFalse(host1.equals(null));
        assertFalse(host1.equals("http://somehost"));
    }
    
    public void testToString() {
        Protocol http = Protocol.getProtocol("http");
        Protocol myhttp = new Protocol("myhttp", 
                NIOSocketFactory.getSocketFactory(), 8080);
        HttpHost host1 = new HttpHost("somehost");
        assertEquals("http://somehost", host1.toString());
        HttpHost host2 = new HttpHost("somehost", http.getDefaultPort());
        assertEquals("http://somehost", host2.toString());
        HttpHost host3 = new HttpHost("somehost", -1);
        assertEquals("http://somehost", host3.toString());
        HttpHost host4 = new HttpHost("somehost", 8888);
        assertEquals("http://somehost:8888", host4.toString());
        HttpHost host5 = new HttpHost("somehost", -1, myhttp);
        assertEquals("myhttp://somehost", host5.toString());
        HttpHost host6 = new HttpHost("somehost", 80, myhttp);
        assertEquals("myhttp://somehost:80", host6.toString());
    }

    public void testClone() {
        HttpHost host1 = new HttpHost("somehost", 8888, Protocol.getProtocol("http"));
        HttpHost host2 = (HttpHost) host1.clone(); 
        assertEquals(host1, host2);
    }
}

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

package org.apache.http.entity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit tests for {@link NameValuePair}.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 */
public class TestHttpEntities extends TestCase {

    public TestHttpEntities(String testName) {
        super(testName);
    }

    public static void main(String args[]) {
        String[] testCaseName = { TestHttpEntities.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public static Test suite() {
        return new TestSuite(TestHttpEntities.class);
    }

    public void testBasicHttpEntity() throws Exception {
    	
    	byte[] bytes = "Message content".getBytes("US-ASCII");
    	InputStream content = new ByteArrayInputStream(bytes);
    	BasicHttpEntity httpentity = new BasicHttpEntity();
    	httpentity.setContent(content);
    	httpentity.setContentLength(bytes.length);
    	httpentity.setContentType(new Header(HttpEntity.CONTENT_TYPE, "text/plain"));
    	httpentity.setContentEncoding(new Header(HttpEntity.CONTENT_ENCODING, "identity"));
    	httpentity.setChunked(false);
    	
    	assertEquals(content, httpentity.getContent());
    	assertEquals(bytes.length, httpentity.getContentLength());
        assertEquals(HttpEntity.CONTENT_TYPE, httpentity.getContentType().getName());
    	assertEquals("text/plain", httpentity.getContentType().getValue());
        assertEquals(HttpEntity.CONTENT_ENCODING, httpentity.getContentEncoding().getName());
    	assertEquals("identity", httpentity.getContentEncoding().getValue());
    	assertFalse(httpentity.isChunked());
    	assertFalse(httpentity.isRepeatable());
    }
    
    public void testBasicHttpEntityWriteTo() throws Exception {
    	byte[] bytes = "Message content".getBytes("US-ASCII");
    	InputStream content = new ByteArrayInputStream(bytes);
    	BasicHttpEntity httpentity = new BasicHttpEntity();
    	httpentity.setContent(content);
    	
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	assertTrue(httpentity.writeTo(out));
    	byte[] bytes2 = out.toByteArray();
    	assertNotNull(bytes2);
    	assertEquals(bytes.length, bytes2.length);
    	for (int i = 0; i < bytes.length; i++) {
    		assertEquals(bytes[i], bytes2[i]);
    	}

    	out = new ByteArrayOutputStream();
    	assertTrue(httpentity.writeTo(out));
    	bytes2 = out.toByteArray();
    	assertNotNull(bytes2);
    	assertEquals(0, bytes2.length);
    	
    	httpentity.setContent(null);
    	out = new ByteArrayOutputStream();
    	assertTrue(httpentity.writeTo(out));
    	bytes2 = out.toByteArray();
    	assertNotNull(bytes2);
    	assertEquals(0, bytes2.length);

    	try {
    		httpentity.writeTo(null);
    		fail("IllegalArgumentException should have been thrown");
    	} catch (IllegalArgumentException ex) {
    		// expected
    	}
    }
    
    public void testByteArrayEntity() throws Exception {
    	byte[] bytes = "Message content".getBytes("US-ASCII");
    	ByteArrayEntity httpentity = new ByteArrayEntity(bytes);
    	httpentity.setContentType("application/octet-stream");
    	httpentity.setContentEncoding("identity");
    	httpentity.setChunked(false);
    	
    	assertEquals(bytes.length, httpentity.getContentLength());
        assertEquals(HttpEntity.CONTENT_TYPE, httpentity.getContentType().getName());
        assertEquals("application/octet-stream", httpentity.getContentType().getValue());
        assertEquals(HttpEntity.CONTENT_ENCODING, httpentity.getContentEncoding().getName());
        assertEquals("identity", httpentity.getContentEncoding().getValue());
    	assertNotNull(httpentity.getContent());
    	assertFalse(httpentity.isChunked());
    	assertTrue(httpentity.isRepeatable());
    }
        
    public void testByteArrayEntityIllegalConstructor() throws Exception {
    	try {
    		new ByteArrayEntity(null);
    		fail("IllegalArgumentException should have been thrown");
    	} catch (IllegalArgumentException ex) {
    		// expected
    	}
    }

    public void testByteArrayEntityWriteTo() throws Exception {
    	byte[] bytes = "Message content".getBytes("US-ASCII");
    	ByteArrayEntity httpentity = new ByteArrayEntity(bytes);
    	
    	ByteArrayOutputStream out = new ByteArrayOutputStream();
    	assertTrue(httpentity.writeTo(out));
    	byte[] bytes2 = out.toByteArray();
    	assertNotNull(bytes2);
    	assertEquals(bytes.length, bytes2.length);
    	for (int i = 0; i < bytes.length; i++) {
    		assertEquals(bytes[i], bytes2[i]);
    	}

    	out = new ByteArrayOutputStream();
    	assertTrue(httpentity.writeTo(out));
    	bytes2 = out.toByteArray();
    	assertNotNull(bytes2);
    	assertEquals(bytes.length, bytes2.length);
    	for (int i = 0; i < bytes.length; i++) {
    		assertEquals(bytes[i], bytes2[i]);
    	}
        
    	try {
    		httpentity.writeTo(null);
    		fail("IllegalArgumentException should have been thrown");
    	} catch (IllegalArgumentException ex) {
    		// expected
    	}
    }
    
    public void testStringEntity() throws Exception {
        String s = "Message content";
        StringEntity httpentity = new StringEntity(s, "ISO-8859-1");
        httpentity.setContentType("text/plain");
        httpentity.setContentEncoding("identity");
        httpentity.setChunked(false);
        
        byte[] bytes = s.getBytes("ISO-8859-1");
        assertEquals(bytes.length, httpentity.getContentLength());
        assertEquals(HttpEntity.CONTENT_TYPE, httpentity.getContentType().getName());
        assertEquals("text/plain", httpentity.getContentType().getValue());
        assertEquals(HttpEntity.CONTENT_ENCODING, httpentity.getContentEncoding().getName());
        assertEquals("identity", httpentity.getContentEncoding().getValue());
        assertNotNull(httpentity.getContent());
        assertFalse(httpentity.isChunked());
        assertTrue(httpentity.isRepeatable());
    }

    public void testStringEntityIllegalConstructor() throws Exception {
        try {
            new StringEntity(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testStringEntityDefaultContent() throws Exception {
        String s = "Message content";
        StringEntity httpentity = new StringEntity(s, "US-ASCII");
        assertEquals("text/plain; charset=US-ASCII", 
                httpentity.getContentType().getValue());
        httpentity = new StringEntity(s);
        assertEquals("text/plain; charset=ISO-8859-1", 
                httpentity.getContentType().getValue());
    }

    public void testStringEntityWriteTo() throws Exception {
        String s = "Message content";
        byte[] bytes = s.getBytes("ISO-8859-1");
        StringEntity httpentity = new StringEntity(s);
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(httpentity.writeTo(out));
        byte[] bytes2 = out.toByteArray();
        assertNotNull(bytes2);
        assertEquals(bytes.length, bytes2.length);
        for (int i = 0; i < bytes.length; i++) {
            assertEquals(bytes[i], bytes2[i]);
        }

        out = new ByteArrayOutputStream();
        assertTrue(httpentity.writeTo(out));
        bytes2 = out.toByteArray();
        assertNotNull(bytes2);
        assertEquals(bytes.length, bytes2.length);
        for (int i = 0; i < bytes.length; i++) {
            assertEquals(bytes[i], bytes2[i]);
        }
        
        try {
            httpentity.writeTo(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testInputStreamEntity() throws Exception {
        byte[] bytes = "Message content".getBytes("ISO-8859-1");
        InputStream instream = new ByteArrayInputStream(bytes);
        InputStreamEntity httpentity = new InputStreamEntity(instream, bytes.length);
        httpentity.setContentType("text/plain");
        httpentity.setContentEncoding("identity");
        httpentity.setChunked(false);
        
        assertEquals(bytes.length, httpentity.getContentLength());
        assertEquals(HttpEntity.CONTENT_TYPE, httpentity.getContentType().getName());
        assertEquals("text/plain", httpentity.getContentType().getValue());
        assertEquals(HttpEntity.CONTENT_ENCODING, httpentity.getContentEncoding().getName());
        assertEquals("identity", httpentity.getContentEncoding().getValue());
        assertEquals(instream, httpentity.getContent());
        assertNotNull(httpentity.getContent());
        assertFalse(httpentity.isChunked());
        assertFalse(httpentity.isRepeatable());
    }

    public void testInputStreamEntityIllegalConstructor() throws Exception {
        try {
            new InputStreamEntity(null, 0);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testInputStreamyEntityWriteTo() throws Exception {
        byte[] bytes = "Message content".getBytes("ISO-8859-1");
        InputStream instream = new ByteArrayInputStream(bytes);
        InputStreamEntity httpentity = new InputStreamEntity(instream, 7);
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(httpentity.writeTo(out));
        byte[] bytes2 = out.toByteArray();
        assertNotNull(bytes2);
        assertEquals(7, bytes2.length);
        String s = new String(bytes2, "ISO-8859-1");
        assertEquals("Message", s);

        instream = new ByteArrayInputStream(bytes);
        httpentity = new InputStreamEntity(instream, 20);
        out = new ByteArrayOutputStream();
        assertTrue(httpentity.writeTo(out));
        bytes2 = out.toByteArray();
        assertNotNull(bytes2);
        assertEquals(bytes.length, bytes2.length);
        
        try {
            httpentity.writeTo(null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }
        
}

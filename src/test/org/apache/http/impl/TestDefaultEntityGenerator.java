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

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpMutableMessage;
import org.apache.http.ProtocolException;
import org.apache.http.io.ChunkedInputStream;
import org.apache.http.io.ContentLengthInputStream;
import org.apache.http.io.HttpDataInputStream;
import org.apache.http.io.HttpDataReceiver;
import org.apache.http.io.InputStreamHttpDataReceiver;
import org.apache.http.mockup.HttpDataReceiverMockup;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

public class TestDefaultEntityGenerator extends TestCase {

    public TestDefaultEntityGenerator(String testName) {
        super(testName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestDefaultEntityGenerator.class);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestDefaultEntityGenerator.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public void testIllegalResponseArg() throws Exception {
        EntityGenerator entitygen = new DefaultEntityGenerator();
        try {
            entitygen.generate(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            entitygen.generate(new HttpDataReceiverMockup(new byte[] {}) , null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testEntityWithContentLength() throws Exception {
        HttpDataReceiver datareceiver = new HttpDataReceiverMockup(new byte[] {});
        HttpMutableMessage message = new BasicHttpMessage();
        HttpParams params = new DefaultHttpParams(null);
        message.setParams(params);
        
        // lenient mode 
        params.setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        message.addHeader(new Header("Content-Type", "unknown"));
        message.addHeader(new Header("Content-Length", "0"));
        EntityGenerator entitygen = new DefaultEntityGenerator();
        HttpEntity entity = entitygen.generate(datareceiver, message);
        assertNotNull(entity);
        assertEquals(0, entity.getContentLength());
        assertFalse(entity.isChunked());
        assertTrue(entity.getInputStream() instanceof ContentLengthInputStream);
    }

    public void testEntityWithMultipleContentLength() throws Exception {
        HttpDataReceiver datareceiver = new HttpDataReceiverMockup(new byte[] {'0'});
        HttpMutableMessage message = new BasicHttpMessage();
        HttpParams params = new DefaultHttpParams(null);
        message.setParams(params);

        // lenient mode 
        params.setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        message.addHeader(new Header("Content-Type", "unknown"));
        message.addHeader(new Header("Content-Length", "0"));
        message.addHeader(new Header("Content-Length", "0"));
        message.addHeader(new Header("Content-Length", "1"));
        EntityGenerator entitygen = new DefaultEntityGenerator();
        HttpEntity entity = entitygen.generate(datareceiver, message);
        assertNotNull(entity);
        assertEquals(1, entity.getContentLength());
        assertFalse(entity.isChunked());
        assertNotNull(entity.getInputStream());
        assertTrue(entity.getInputStream() instanceof ContentLengthInputStream);
        
        // strict mode 
        params.setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, true);
        try {
            entitygen.generate(datareceiver, message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }
    
    public void testEntityWithMultipleContentLengthSomeWrong() throws Exception {
        HttpDataReceiver datareceiver = new HttpDataReceiverMockup(new byte[] {'0'});
        HttpMutableMessage message = new BasicHttpMessage();
        HttpParams params = new DefaultHttpParams(null);
        message.setParams(params);

        // lenient mode 
        params.setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        message.addHeader(new Header("Content-Type", "unknown"));
        message.addHeader(new Header("Content-Length", "1"));
        message.addHeader(new Header("Content-Length", "yyy"));
        message.addHeader(new Header("Content-Length", "xxx"));
        EntityGenerator entitygen = new DefaultEntityGenerator();
        HttpEntity entity = entitygen.generate(datareceiver, message);
        assertNotNull(entity);
        assertEquals(1, entity.getContentLength());
        assertFalse(entity.isChunked());
        assertNotNull(entity.getInputStream());
        assertTrue(entity.getInputStream() instanceof ContentLengthInputStream);
        
        // strict mode 
        params.setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, true);
        try {
            entitygen.generate(datareceiver, message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }
    
    public void testEntityWithMultipleContentLengthAllWrong() throws Exception {
        HttpDataReceiver datareceiver = new HttpDataReceiverMockup(new byte[] {'0'});
        HttpMutableMessage message = new BasicHttpMessage();
        HttpParams params = new DefaultHttpParams(null);
        message.setParams(params);

        // lenient mode 
        params.setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        message.addHeader(new Header("Content-Type", "unknown"));
        message.addHeader(new Header("Content-Length", "yyy"));
        message.addHeader(new Header("Content-Length", "xxx"));
        EntityGenerator entitygen = new DefaultEntityGenerator();
        HttpEntity entity = entitygen.generate(datareceiver, message);
        assertNotNull(entity);
        assertEquals(-1, entity.getContentLength());
        assertFalse(entity.isChunked());
        assertNotNull(entity.getInputStream());
        assertFalse(entity.getInputStream() instanceof ContentLengthInputStream);
        assertTrue(entity.getInputStream() instanceof HttpDataInputStream);
        
        // strict mode 
        params.setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, true);
        try {
            entitygen.generate(datareceiver, message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    public void testEntityWithInvalidContentLength() throws Exception {
        HttpDataReceiver datareceiver = new HttpDataReceiverMockup(new byte[] {'0'});
        HttpMutableMessage message = new BasicHttpMessage();
        HttpParams params = new DefaultHttpParams(null);
        message.setParams(params);

        // lenient mode 
        params.setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        message.addHeader(new Header("Content-Type", "unknown"));
        message.addHeader(new Header("Content-Length", "xxx"));
        EntityGenerator entitygen = new DefaultEntityGenerator();
        HttpEntity entity = entitygen.generate(datareceiver, message);
        assertNotNull(entity);
        assertEquals(-1, entity.getContentLength());
        assertFalse(entity.isChunked());
        assertNotNull(entity.getInputStream());
        assertFalse(entity.getInputStream() instanceof ContentLengthInputStream);
        assertTrue(entity.getInputStream() instanceof HttpDataInputStream);
        
        // strict mode 
        params.setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, true);
        try {
            entitygen.generate(datareceiver, message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    public void testEntityNeitherContentLengthNorTransferEncoding() throws Exception {
        HttpDataReceiver datareceiver = new HttpDataReceiverMockup(new byte[] {'0'});
        HttpMutableMessage message = new BasicHttpMessage();
        HttpParams params = new DefaultHttpParams(null);
        message.setParams(params);

        // lenient mode 
        params.setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        EntityGenerator entitygen = new DefaultEntityGenerator();
        HttpEntity entity = entitygen.generate(datareceiver, message);
        assertNotNull(entity);
        assertEquals(-1, entity.getContentLength());
        assertFalse(entity.isChunked());
        assertNotNull(entity.getInputStream());
        assertFalse(entity.getInputStream() instanceof ContentLengthInputStream);
        assertFalse(entity.getInputStream() instanceof ChunkedInputStream);
        assertTrue(entity.getInputStream() instanceof HttpDataInputStream);
    }

    public void testOldIOWrapper() throws Exception {
        InputStream instream = new ByteArrayInputStream(new byte[] {});
        HttpDataReceiver datareceiver = new InputStreamHttpDataReceiver(instream);
        HttpMutableMessage message = new BasicHttpMessage();
        message.addHeader(new Header("Content-Type", "unknown"));
        EntityGenerator entitygen = new DefaultEntityGenerator();
        HttpEntity entity = entitygen.generate(datareceiver, message);
        assertNotNull(entity);
        assertEquals(-1, entity.getContentLength());
        assertFalse(entity.isChunked());
        assertTrue(entity.getInputStream() instanceof ByteArrayInputStream);
    }

}


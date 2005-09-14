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
import org.apache.http.HttpEntity;
import org.apache.http.HttpMutableMessage;
import org.apache.http.ProtocolException;
import org.apache.http.impl.entity.DefaultEntityGenerator;
import org.apache.http.impl.entity.EntityGenerator;
import org.apache.http.io.ChunkedInputStream;
import org.apache.http.io.ContentLengthInputStream;
import org.apache.http.io.HttpDataInputStream;
import org.apache.http.io.HttpDataReceiver;
import org.apache.http.mockup.InputStreamHttpDataReceiverMockup;
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

    public void testIllegalGenerateArg() throws Exception {
        EntityGenerator entitygen = new DefaultEntityGenerator();
        try {
            entitygen.generate(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            entitygen.generate(new InputStreamHttpDataReceiverMockup(new byte[] {}) , null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testEntityWithTransferEncoding() throws Exception {
        HttpDataReceiver datareceiver = new InputStreamHttpDataReceiverMockup("0\r\n", "US-ASCII");
        HttpMutableMessage message = new BasicHttpMessage();
        
        // lenient mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        message.addHeader(new Header("Content-Type", "unknown"));
        message.addHeader(new Header("Transfer-Encoding", "identity, chunked"));
        message.addHeader(new Header("Content-Length", "plain wrong"));
        EntityGenerator entitygen = new DefaultEntityGenerator();
        HttpEntity entity = entitygen.generate(datareceiver, message);
        assertNotNull(entity);
        assertEquals(-1, entity.getContentLength());
        assertTrue(entity.isChunked());
        assertTrue(entity.getContent() instanceof ChunkedInputStream);

        // strict mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, true);
        entity = entitygen.generate(datareceiver, message);
        assertNotNull(entity);
        assertEquals(-1, entity.getContentLength());
        assertTrue(entity.isChunked());
        assertTrue(entity.getContent() instanceof ChunkedInputStream);
    }

    public void testEntityWithIdentityTransferEncoding() throws Exception {
        HttpDataReceiver datareceiver = 
        	new InputStreamHttpDataReceiverMockup(new byte[] {});
        HttpMutableMessage message = new BasicHttpMessage();
        
        // lenient mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        message.addHeader(new Header("Content-Type", "unknown"));
        message.addHeader(new Header("Transfer-Encoding", "identity"));
        message.addHeader(new Header("Content-Length", "plain wrong"));
        EntityGenerator entitygen = new DefaultEntityGenerator();
        HttpEntity entity = entitygen.generate(datareceiver, message);
        assertNotNull(entity);
        assertEquals(-1, entity.getContentLength());
        assertFalse(entity.isChunked());
    }

    public void testEntityWithUnsupportedTransferEncoding() throws Exception {
        HttpDataReceiver datareceiver = new InputStreamHttpDataReceiverMockup("0\r\n", "US-ASCII");
        HttpMutableMessage message = new BasicHttpMessage();
        
        // lenient mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        message.addHeader(new Header("Content-Type", "unknown"));
        message.addHeader(new Header("Transfer-Encoding", "whatever; param=value, chunked"));
        message.addHeader(new Header("Content-Length", "plain wrong"));
        EntityGenerator entitygen = new DefaultEntityGenerator();
        HttpEntity entity = entitygen.generate(datareceiver, message);
        assertNotNull(entity);
        assertEquals(-1, entity.getContentLength());
        assertTrue(entity.isChunked());
        assertTrue(entity.getContent() instanceof ChunkedInputStream);

        // strict mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, true);
        try {
            entitygen.generate(datareceiver, message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    public void testChunkedTransferEncodingMustBeLast() throws Exception {
        HttpDataReceiver datareceiver = new InputStreamHttpDataReceiverMockup("0\r\n", "US-ASCII");
        HttpMutableMessage message = new BasicHttpMessage();
        
        // lenient mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        message.addHeader(new Header("Content-Type", "unknown"));
        message.addHeader(new Header("Transfer-Encoding", "chunked, identity"));
        message.addHeader(new Header("Content-Length", "plain wrong"));
        EntityGenerator entitygen = new DefaultEntityGenerator();
        HttpEntity entity = entitygen.generate(datareceiver, message);
        assertNotNull(entity);
        assertEquals(-1, entity.getContentLength());
        assertFalse(entity.isChunked());
        assertFalse(entity.getContent() instanceof ChunkedInputStream);

        // strict mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, true);
        try {
            entitygen.generate(datareceiver, message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    public void testEntityWithContentLength() throws Exception {
        HttpDataReceiver datareceiver = new InputStreamHttpDataReceiverMockup(new byte[] {});
        HttpMutableMessage message = new BasicHttpMessage();
        
        // lenient mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        message.addHeader(new Header("Content-Type", "unknown"));
        message.addHeader(new Header("Content-Length", "0"));
        EntityGenerator entitygen = new DefaultEntityGenerator();
        HttpEntity entity = entitygen.generate(datareceiver, message);
        assertNotNull(entity);
        assertEquals(0, entity.getContentLength());
        assertFalse(entity.isChunked());
        assertTrue(entity.getContent() instanceof ContentLengthInputStream);
    }

    public void testEntityWithMultipleContentLength() throws Exception {
        HttpDataReceiver datareceiver = new InputStreamHttpDataReceiverMockup(new byte[] {'0'});
        HttpMutableMessage message = new BasicHttpMessage();

        // lenient mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        message.addHeader(new Header("Content-Type", "unknown"));
        message.addHeader(new Header("Content-Length", "0"));
        message.addHeader(new Header("Content-Length", "0"));
        message.addHeader(new Header("Content-Length", "1"));
        EntityGenerator entitygen = new DefaultEntityGenerator();
        HttpEntity entity = entitygen.generate(datareceiver, message);
        assertNotNull(entity);
        assertEquals(1, entity.getContentLength());
        assertFalse(entity.isChunked());
        assertNotNull(entity.getContent());
        assertTrue(entity.getContent() instanceof ContentLengthInputStream);
        
        // strict mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, true);
        try {
            entitygen.generate(datareceiver, message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }
    
    public void testEntityWithMultipleContentLengthSomeWrong() throws Exception {
        HttpDataReceiver datareceiver = new InputStreamHttpDataReceiverMockup(new byte[] {'0'});
        HttpMutableMessage message = new BasicHttpMessage();

        // lenient mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        message.addHeader(new Header("Content-Type", "unknown"));
        message.addHeader(new Header("Content-Length", "1"));
        message.addHeader(new Header("Content-Length", "yyy"));
        message.addHeader(new Header("Content-Length", "xxx"));
        EntityGenerator entitygen = new DefaultEntityGenerator();
        HttpEntity entity = entitygen.generate(datareceiver, message);
        assertNotNull(entity);
        assertEquals(1, entity.getContentLength());
        assertFalse(entity.isChunked());
        assertNotNull(entity.getContent());
        assertTrue(entity.getContent() instanceof ContentLengthInputStream);
        
        // strict mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, true);
        try {
            entitygen.generate(datareceiver, message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }
    
    public void testEntityWithMultipleContentLengthAllWrong() throws Exception {
        HttpDataReceiver datareceiver = new InputStreamHttpDataReceiverMockup(new byte[] {'0'});
        HttpMutableMessage message = new BasicHttpMessage();

        // lenient mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        message.addHeader(new Header("Content-Type", "unknown"));
        message.addHeader(new Header("Content-Length", "yyy"));
        message.addHeader(new Header("Content-Length", "xxx"));
        EntityGenerator entitygen = new DefaultEntityGenerator();
        HttpEntity entity = entitygen.generate(datareceiver, message);
        assertNotNull(entity);
        assertEquals(-1, entity.getContentLength());
        assertFalse(entity.isChunked());
        assertNotNull(entity.getContent());
        assertFalse(entity.getContent() instanceof ContentLengthInputStream);
        assertTrue(entity.getContent() instanceof HttpDataInputStream);
        
        // strict mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, true);
        try {
            entitygen.generate(datareceiver, message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    public void testEntityWithInvalidContentLength() throws Exception {
        HttpDataReceiver datareceiver = new InputStreamHttpDataReceiverMockup(new byte[] {'0'});
        HttpMutableMessage message = new BasicHttpMessage();

        // lenient mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        message.addHeader(new Header("Content-Type", "unknown"));
        message.addHeader(new Header("Content-Length", "xxx"));
        EntityGenerator entitygen = new DefaultEntityGenerator();
        HttpEntity entity = entitygen.generate(datareceiver, message);
        assertNotNull(entity);
        assertEquals(-1, entity.getContentLength());
        assertFalse(entity.isChunked());
        assertNotNull(entity.getContent());
        assertFalse(entity.getContent() instanceof ContentLengthInputStream);
        assertTrue(entity.getContent() instanceof HttpDataInputStream);
        
        // strict mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, true);
        try {
            entitygen.generate(datareceiver, message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    public void testEntityNeitherContentLengthNorTransferEncoding() throws Exception {
        HttpDataReceiver datareceiver = new InputStreamHttpDataReceiverMockup(new byte[] {'0'});
        HttpMutableMessage message = new BasicHttpMessage();

        // lenient mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        EntityGenerator entitygen = new DefaultEntityGenerator();
        HttpEntity entity = entitygen.generate(datareceiver, message);
        assertNotNull(entity);
        assertEquals(-1, entity.getContentLength());
        assertFalse(entity.isChunked());
        assertNotNull(entity.getContent());
        assertFalse(entity.getContent() instanceof ContentLengthInputStream);
        assertFalse(entity.getContent() instanceof ChunkedInputStream);
        assertTrue(entity.getContent() instanceof HttpDataInputStream);
    }

}


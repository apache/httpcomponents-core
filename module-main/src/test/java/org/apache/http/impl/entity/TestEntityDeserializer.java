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

package org.apache.http.impl.entity;

import java.io.InputStream;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.HttpEntity;
import org.apache.http.HttpMessage;
import org.apache.http.ProtocolException;
import org.apache.http.io.ChunkedInputStream;
import org.apache.http.io.ContentLengthInputStream;
import org.apache.http.io.HttpDataInputStream;
import org.apache.http.io.HttpDataReceiver;
import org.apache.http.mockup.HttpDataReceiverMockup;
import org.apache.http.mockup.HttpMessageMockup;
import org.apache.http.params.HttpProtocolParams;

public class TestEntityDeserializer extends TestCase {

    public TestEntityDeserializer(String testName) {
        super(testName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestEntityDeserializer.class);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestEntityDeserializer.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public void testIllegalGenerateArg() throws Exception {
        EntityDeserializer entitygen = new EntityDeserializer(
                new LaxContentLengthStrategy());
        try {
            entitygen.deserialize(null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            entitygen.deserialize(new HttpDataReceiverMockup(new byte[] {}) , null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testEntityWithTransferEncoding() throws Exception {
        HttpDataReceiver datareceiver = new HttpDataReceiverMockup("0\r\n", "US-ASCII");
        HttpMessage message = new HttpMessageMockup();
        
        // lenient mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        message.addHeader("Content-Type", "unknown");
        message.addHeader("Transfer-Encoding", "identity, chunked");
        message.addHeader("Content-Length", "plain wrong");
        EntityDeserializer entitygen = new EntityDeserializer(
                new LaxContentLengthStrategy());
        HttpEntity entity = entitygen.deserialize(datareceiver, message);
        assertNotNull(entity);
        assertEquals(-1, entity.getContentLength());
        assertTrue(entity.isChunked());
        assertTrue(entity.getContent() instanceof ChunkedInputStream);

        // strict mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, true);
        entity = entitygen.deserialize(datareceiver, message);
        assertNotNull(entity);
        assertEquals(-1, entity.getContentLength());
        assertTrue(entity.isChunked());
        assertTrue(entity.getContent() instanceof ChunkedInputStream);
    }

    public void testEntityWithIdentityTransferEncoding() throws Exception {
        HttpDataReceiver datareceiver = 
        	new HttpDataReceiverMockup(new byte[] {});
        HttpMessage message = new HttpMessageMockup();
        
        // lenient mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        message.addHeader("Content-Type", "unknown");
        message.addHeader("Transfer-Encoding", "identity");
        message.addHeader("Content-Length", "plain wrong");
        EntityDeserializer entitygen = new EntityDeserializer(
                new LaxContentLengthStrategy());
        HttpEntity entity = entitygen.deserialize(datareceiver, message);
        assertNotNull(entity);
        assertEquals(-1, entity.getContentLength());
        assertFalse(entity.isChunked());
    }

    public void testEntityWithUnsupportedTransferEncoding() throws Exception {
        HttpDataReceiver datareceiver = new HttpDataReceiverMockup("0\r\n", "US-ASCII");
        HttpMessage message = new HttpMessageMockup();
        
        // lenient mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        message.addHeader("Content-Type", "unknown");
        message.addHeader("Transfer-Encoding", "whatever; param=value, chunked");
        message.addHeader("Content-Length", "plain wrong");
        EntityDeserializer entitygen = new EntityDeserializer(
                new LaxContentLengthStrategy());
        HttpEntity entity = entitygen.deserialize(datareceiver, message);
        assertNotNull(entity);
        assertEquals(-1, entity.getContentLength());
        assertTrue(entity.isChunked());
        assertTrue(entity.getContent() instanceof ChunkedInputStream);

        // strict mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, true);
        try {
            entitygen.deserialize(datareceiver, message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    public void testChunkedTransferEncodingMustBeLast() throws Exception {
        HttpDataReceiver datareceiver = new HttpDataReceiverMockup("0\r\n", "US-ASCII");
        HttpMessage message = new HttpMessageMockup();
        
        // lenient mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        message.addHeader("Content-Type", "unknown");
        message.addHeader("Transfer-Encoding", "chunked, identity");
        message.addHeader("Content-Length", "plain wrong");
        EntityDeserializer entitygen = new EntityDeserializer(
                new LaxContentLengthStrategy());
        HttpEntity entity = entitygen.deserialize(datareceiver, message);
        assertNotNull(entity);
        assertEquals(-1, entity.getContentLength());
        assertFalse(entity.isChunked());
        assertFalse(entity.getContent() instanceof ChunkedInputStream);

        // strict mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, true);
        try {
            entitygen.deserialize(datareceiver, message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    public void testEntityWithContentLength() throws Exception {
        HttpDataReceiver datareceiver = new HttpDataReceiverMockup(new byte[] {});
        HttpMessage message = new HttpMessageMockup();
        
        // lenient mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        message.addHeader("Content-Type", "unknown");
        message.addHeader("Content-Length", "0");
        EntityDeserializer entitygen = new EntityDeserializer(
                new LaxContentLengthStrategy());
        HttpEntity entity = entitygen.deserialize(datareceiver, message);
        assertNotNull(entity);
        assertEquals(0, entity.getContentLength());
        assertFalse(entity.isChunked());
        assertTrue(entity.getContent() instanceof ContentLengthInputStream);
    }

    public void testEntityWithMultipleContentLength() throws Exception {
        HttpDataReceiver datareceiver = new HttpDataReceiverMockup(new byte[] {'0'});
        HttpMessage message = new HttpMessageMockup();

        // lenient mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        message.addHeader("Content-Type", "unknown");
        message.addHeader("Content-Length", "0");
        message.addHeader("Content-Length", "0");
        message.addHeader("Content-Length", "1");
        EntityDeserializer entitygen = new EntityDeserializer(
                new LaxContentLengthStrategy());
        HttpEntity entity = entitygen.deserialize(datareceiver, message);
        assertNotNull(entity);
        assertEquals(1, entity.getContentLength());
        assertFalse(entity.isChunked());
        InputStream instream = entity.getContent();
        assertNotNull(instream);
        assertTrue(instream instanceof ContentLengthInputStream);
        
        // strict mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, true);
        try {
            entitygen.deserialize(datareceiver, message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }
    
    public void testEntityWithMultipleContentLengthSomeWrong() throws Exception {
        HttpDataReceiver datareceiver = new HttpDataReceiverMockup(new byte[] {'0'});
        HttpMessage message = new HttpMessageMockup();

        // lenient mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        message.addHeader("Content-Type", "unknown");
        message.addHeader("Content-Length", "1");
        message.addHeader("Content-Length", "yyy");
        message.addHeader("Content-Length", "xxx");
        EntityDeserializer entitygen = new EntityDeserializer(
                new LaxContentLengthStrategy());
        HttpEntity entity = entitygen.deserialize(datareceiver, message);
        assertNotNull(entity);
        assertEquals(1, entity.getContentLength());
        assertFalse(entity.isChunked());
        InputStream instream = entity.getContent();
        assertNotNull(instream);
        assertTrue(instream instanceof ContentLengthInputStream);
        
        // strict mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, true);
        try {
            entitygen.deserialize(datareceiver, message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }
    
    public void testEntityWithMultipleContentLengthAllWrong() throws Exception {
        HttpDataReceiver datareceiver = new HttpDataReceiverMockup(new byte[] {'0'});
        HttpMessage message = new HttpMessageMockup();

        // lenient mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        message.addHeader("Content-Type", "unknown");
        message.addHeader("Content-Length", "yyy");
        message.addHeader("Content-Length", "xxx");
        EntityDeserializer entitygen = new EntityDeserializer(
                new LaxContentLengthStrategy());
        HttpEntity entity = entitygen.deserialize(datareceiver, message);
        assertNotNull(entity);
        assertEquals(-1, entity.getContentLength());
        assertFalse(entity.isChunked());
        InputStream instream = entity.getContent();
        assertNotNull(instream);
        assertFalse(instream instanceof ContentLengthInputStream);
        assertTrue(instream instanceof HttpDataInputStream);
        
        // strict mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, true);
        try {
            entitygen.deserialize(datareceiver, message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    public void testEntityWithInvalidContentLength() throws Exception {
        HttpDataReceiver datareceiver = new HttpDataReceiverMockup(new byte[] {'0'});
        HttpMessage message = new HttpMessageMockup();

        // lenient mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        message.addHeader("Content-Type", "unknown");
        message.addHeader("Content-Length", "xxx");
        EntityDeserializer entitygen = new EntityDeserializer(
                new LaxContentLengthStrategy());
        HttpEntity entity = entitygen.deserialize(datareceiver, message);
        assertNotNull(entity);
        assertEquals(-1, entity.getContentLength());
        assertFalse(entity.isChunked());
        InputStream instream = entity.getContent();
        assertNotNull(instream);
        assertFalse(instream instanceof ContentLengthInputStream);
        assertTrue(instream instanceof HttpDataInputStream);
        
        // strict mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, true);
        try {
            entitygen.deserialize(datareceiver, message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    public void testEntityNeitherContentLengthNorTransferEncoding() throws Exception {
        HttpDataReceiver datareceiver = new HttpDataReceiverMockup(new byte[] {'0'});
        HttpMessage message = new HttpMessageMockup();

        // lenient mode 
        message.getParams().setBooleanParameter(HttpProtocolParams.STRICT_TRANSFER_ENCODING, false);
        EntityDeserializer entitygen = new EntityDeserializer(
                new LaxContentLengthStrategy());
        HttpEntity entity = entitygen.deserialize(datareceiver, message);
        assertNotNull(entity);
        assertEquals(-1, entity.getContentLength());
        assertFalse(entity.isChunked());
        InputStream instream = entity.getContent();
        assertNotNull(instream);
        assertFalse(instream instanceof ContentLengthInputStream);
        assertFalse(instream instanceof ChunkedInputStream);
        assertTrue(instream instanceof HttpDataInputStream);
    }

    public void testEntityContentType() throws Exception {
        HttpDataReceiver datareceiver = new HttpDataReceiverMockup(new byte[] {'0'});
        HttpMessage message = new HttpMessageMockup();

        message.addHeader("Content-Type", "stuff");
        EntityDeserializer entitygen = new EntityDeserializer(
                new LaxContentLengthStrategy());
        HttpEntity entity = entitygen.deserialize(datareceiver, message);
        assertNotNull(entity);
        assertNotNull(entity.getContentType());
        assertEquals("stuff", entity.getContentType().getValue());
    }

    public void testEntityContentEncoding() throws Exception {
        HttpDataReceiver datareceiver = new HttpDataReceiverMockup(new byte[] {'0'});
        HttpMessage message = new HttpMessageMockup();

        message.addHeader("Content-Encoding", "what not");
        EntityDeserializer entitygen = new EntityDeserializer(
                new LaxContentLengthStrategy());
        HttpEntity entity = entitygen.deserialize(datareceiver, message);
        assertNotNull(entity);
        assertNotNull(entity.getContentEncoding());
        assertEquals("what not", entity.getContentEncoding().getValue());
    }
    
}


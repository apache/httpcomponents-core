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

import java.io.OutputStream;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.HttpMessage;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.EntitySerializer;
import org.apache.http.io.ChunkedOutputStream;
import org.apache.http.io.ContentLengthOutputStream;
import org.apache.http.io.HttpDataTransmitter;
import org.apache.http.io.IdentityOutputStream;
import org.apache.http.mockup.HttpDataTransmitterMockup;
import org.apache.http.mockup.HttpMessageMockup;
import org.apache.http.params.HttpProtocolParams;

public class TestDefaultEntitySerializer extends TestCase {

    public TestDefaultEntitySerializer(String testName) {
        super(testName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestDefaultEntitySerializer.class);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestDefaultEntitySerializer.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    public void testIllegalGenerateArg() throws Exception {
        EntitySerializer entitywriter = new DefaultEntitySerializer(
                new StrictContentLengthStrategy());
        try {
            entitywriter.serialize(null, null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            entitywriter.serialize(new HttpDataTransmitterMockup() , null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            entitywriter.serialize(new HttpDataTransmitterMockup() , new HttpMessageMockup(), null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testEntityWithChunkTransferEncoding() throws Exception {
        HttpDataTransmitter datatransmitter = new HttpDataTransmitterMockup();
        HttpMessage message = new HttpMessageMockup();
        message.addHeader("Transfer-Encoding", "Chunked");

        DefaultEntitySerializer entitywriter = new DefaultEntitySerializer(
                new StrictContentLengthStrategy());
        OutputStream outstream = entitywriter.doSerialize(datatransmitter, message);
        assertNotNull(outstream);
        assertTrue(outstream instanceof ChunkedOutputStream);
    }

    public void testEntityWithIdentityTransferEncoding() throws Exception {
        HttpDataTransmitter datatransmitter = new HttpDataTransmitterMockup();
        HttpMessage message = new HttpMessageMockup();
        message.addHeader("Transfer-Encoding", "Identity");

        DefaultEntitySerializer entitywriter = new DefaultEntitySerializer(
                new StrictContentLengthStrategy());
        OutputStream outstream = entitywriter.doSerialize(datatransmitter, message);
        assertNotNull(outstream);
        assertTrue(outstream instanceof IdentityOutputStream);
    }
    
    public void testEntityWithInvalidTransferEncoding() throws Exception {
        HttpDataTransmitter datatransmitter = new HttpDataTransmitterMockup();
        HttpMessage message = new HttpMessageMockup();
        message.addHeader("Transfer-Encoding", "whatever");

        DefaultEntitySerializer entitywriter = new DefaultEntitySerializer(
                new StrictContentLengthStrategy());
        try {
            entitywriter.doSerialize(datatransmitter, message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }
    
    public void testEntityWithInvalidChunkEncodingAndHTTP10() throws Exception {
        HttpDataTransmitter datatransmitter = new HttpDataTransmitterMockup();
        HttpMessage message = new HttpMessageMockup();
        message.getParams().setParameter(HttpProtocolParams.PROTOCOL_VERSION, 
                HttpVersion.HTTP_1_0);
        message.addHeader("Transfer-Encoding", "chunked");

        DefaultEntitySerializer entitywriter = new DefaultEntitySerializer(
                new StrictContentLengthStrategy());
        try {
            entitywriter.doSerialize(datatransmitter, message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }
    
    public void testEntityWithContentLength() throws Exception {
        HttpDataTransmitter datatransmitter = new HttpDataTransmitterMockup();
        HttpMessage message = new HttpMessageMockup();
        message.addHeader("Content-Length", "100");
        DefaultEntitySerializer entitywriter = new DefaultEntitySerializer(
                new StrictContentLengthStrategy());
        OutputStream outstream = entitywriter.doSerialize(datatransmitter, message);
        assertNotNull(outstream);
        assertTrue(outstream instanceof ContentLengthOutputStream);
    }
    
    public void testEntityWithInvalidContentLength() throws Exception {
        HttpDataTransmitter datatransmitter = new HttpDataTransmitterMockup();
        HttpMessage message = new HttpMessageMockup();
        message.addHeader("Content-Length", "whatever");

        DefaultEntitySerializer entitywriter = new DefaultEntitySerializer(
                new StrictContentLengthStrategy());
        try {
            entitywriter.doSerialize(datatransmitter, message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    public void testEntityNoContentDelimiter() throws Exception {
        HttpDataTransmitter datatransmitter = new HttpDataTransmitterMockup();
        HttpMessage message = new HttpMessageMockup();
        DefaultEntitySerializer entitywriter = new DefaultEntitySerializer(
                new StrictContentLengthStrategy());
        OutputStream outstream = entitywriter.doSerialize(datatransmitter, message);
        assertNotNull(outstream);
        assertTrue(outstream instanceof IdentityOutputStream);
    }
        
    public void testEntitySerialization() throws Exception {
        byte[] content = new byte[] {1, 2, 3, 4, 5};
        ByteArrayEntity entity = new ByteArrayEntity(content); 
        
        HttpDataTransmitterMockup datatransmitter = new HttpDataTransmitterMockup();
        HttpMessage message = new HttpMessageMockup();
        message.addHeader("Content-Length", Integer.toString(content.length));
        
        DefaultEntitySerializer entitywriter = new DefaultEntitySerializer(
                new StrictContentLengthStrategy());
        entitywriter.serialize(datatransmitter, message, entity);
        
        byte[] data = datatransmitter.getData();
        assertNotNull(data);
        assertEquals(content.length, data.length);
    }
}


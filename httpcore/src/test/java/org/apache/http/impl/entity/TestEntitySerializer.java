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

package org.apache.http.impl.entity;

import java.io.OutputStream;

import junit.framework.TestCase;

import org.apache.http.HttpMessage;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.io.ChunkedOutputStream;
import org.apache.http.impl.io.ContentLengthOutputStream;
import org.apache.http.impl.io.IdentityOutputStream;
import org.apache.http.io.SessionOutputBuffer;
import org.apache.http.mockup.HttpMessageMockup;
import org.apache.http.mockup.SessionOutputBufferMockup;
import org.apache.http.params.CoreProtocolPNames;

public class TestEntitySerializer extends TestCase {

    public TestEntitySerializer(String testName) {
        super(testName);
    }

    public void testIllegalGenerateArg() throws Exception {
        EntitySerializer entitywriter = new EntitySerializer(
                new StrictContentLengthStrategy());
        try {
            entitywriter.serialize(null, null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            entitywriter.serialize(new SessionOutputBufferMockup() , null, null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            entitywriter.serialize(new SessionOutputBufferMockup() , new HttpMessageMockup(), null);
            fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    public void testEntityWithChunkTransferEncoding() throws Exception {
        SessionOutputBuffer datatransmitter = new SessionOutputBufferMockup();
        HttpMessage message = new HttpMessageMockup();
        message.addHeader("Transfer-Encoding", "Chunked");

        EntitySerializer entitywriter = new EntitySerializer(
                new StrictContentLengthStrategy());
        OutputStream outstream = entitywriter.doSerialize(datatransmitter, message);
        assertNotNull(outstream);
        assertTrue(outstream instanceof ChunkedOutputStream);
    }

    public void testEntityWithIdentityTransferEncoding() throws Exception {
        SessionOutputBuffer datatransmitter = new SessionOutputBufferMockup();
        HttpMessage message = new HttpMessageMockup();
        message.addHeader("Transfer-Encoding", "Identity");

        EntitySerializer entitywriter = new EntitySerializer(
                new StrictContentLengthStrategy());
        OutputStream outstream = entitywriter.doSerialize(datatransmitter, message);
        assertNotNull(outstream);
        assertTrue(outstream instanceof IdentityOutputStream);
    }

    public void testEntityWithInvalidTransferEncoding() throws Exception {
        SessionOutputBuffer datatransmitter = new SessionOutputBufferMockup();
        HttpMessage message = new HttpMessageMockup();
        message.addHeader("Transfer-Encoding", "whatever");

        EntitySerializer entitywriter = new EntitySerializer(
                new StrictContentLengthStrategy());
        try {
            entitywriter.doSerialize(datatransmitter, message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    public void testEntityWithInvalidChunkEncodingAndHTTP10() throws Exception {
        SessionOutputBuffer datatransmitter = new SessionOutputBufferMockup();
        HttpMessage message = new HttpMessageMockup();
        message.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION,
                HttpVersion.HTTP_1_0);
        message.addHeader("Transfer-Encoding", "chunked");

        EntitySerializer entitywriter = new EntitySerializer(
                new StrictContentLengthStrategy());
        try {
            entitywriter.doSerialize(datatransmitter, message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    public void testEntityWithContentLength() throws Exception {
        SessionOutputBuffer datatransmitter = new SessionOutputBufferMockup();
        HttpMessage message = new HttpMessageMockup();
        message.addHeader("Content-Length", "100");
        EntitySerializer entitywriter = new EntitySerializer(
                new StrictContentLengthStrategy());
        OutputStream outstream = entitywriter.doSerialize(datatransmitter, message);
        assertNotNull(outstream);
        assertTrue(outstream instanceof ContentLengthOutputStream);
    }

    public void testEntityWithInvalidContentLength() throws Exception {
        SessionOutputBuffer datatransmitter = new SessionOutputBufferMockup();
        HttpMessage message = new HttpMessageMockup();
        message.addHeader("Content-Length", "whatever");

        EntitySerializer entitywriter = new EntitySerializer(
                new StrictContentLengthStrategy());
        try {
            entitywriter.doSerialize(datatransmitter, message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    public void testEntityNoContentDelimiter() throws Exception {
        SessionOutputBuffer datatransmitter = new SessionOutputBufferMockup();
        HttpMessage message = new HttpMessageMockup();
        EntitySerializer entitywriter = new EntitySerializer(
                new StrictContentLengthStrategy());
        OutputStream outstream = entitywriter.doSerialize(datatransmitter, message);
        assertNotNull(outstream);
        assertTrue(outstream instanceof IdentityOutputStream);
    }

    public void testEntitySerialization() throws Exception {
        byte[] content = new byte[] {1, 2, 3, 4, 5};
        ByteArrayEntity entity = new ByteArrayEntity(content);

        SessionOutputBufferMockup datatransmitter = new SessionOutputBufferMockup();
        HttpMessage message = new HttpMessageMockup();
        message.addHeader("Content-Length", Integer.toString(content.length));

        EntitySerializer entitywriter = new EntitySerializer(
                new StrictContentLengthStrategy());
        entitywriter.serialize(datatransmitter, message, entity);

        byte[] data = datatransmitter.getData();
        assertNotNull(data);
        assertEquals(content.length, data.length);
    }
}


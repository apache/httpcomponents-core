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

import org.apache.http.HttpMessage;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.ProtocolVersion;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.SessionOutputBufferMock;
import org.apache.http.impl.io.ChunkedOutputStream;
import org.apache.http.impl.io.ContentLengthOutputStream;
import org.apache.http.impl.io.IdentityOutputStream;
import org.apache.http.io.SessionOutputBuffer;
import org.apache.http.message.AbstractHttpMessage;
import org.junit.Assert;
import org.junit.Test;

@Deprecated
public class TestEntitySerializer {

    static class TestHttpMessage extends AbstractHttpMessage {

        private final ProtocolVersion ver;

        public TestHttpMessage(final ProtocolVersion ver) {
            super();
            this.ver = ver != null ? ver : HttpVersion.HTTP_1_1;
        }

        public TestHttpMessage() {
            this(HttpVersion.HTTP_1_1);
        }

        public ProtocolVersion getProtocolVersion() {
            return ver;
        }

    }

    @Test
    public void testIllegalGenerateArg() throws Exception {
        final EntitySerializer entitywriter = new EntitySerializer(
                new StrictContentLengthStrategy());
        try {
            entitywriter.serialize(null, null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        try {
            entitywriter.serialize(new SessionOutputBufferMock() , null, null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        try {
            entitywriter.serialize(new SessionOutputBufferMock() , new DummyHttpMessage(), null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testEntityWithChunkTransferEncoding() throws Exception {
        final SessionOutputBuffer outbuffer = new SessionOutputBufferMock();
        final HttpMessage message = new DummyHttpMessage();
        message.addHeader("Transfer-Encoding", "Chunked");

        final EntitySerializer entitywriter = new EntitySerializer(
                new StrictContentLengthStrategy());
        final OutputStream outstream = entitywriter.doSerialize(outbuffer, message);
        Assert.assertNotNull(outstream);
        Assert.assertTrue(outstream instanceof ChunkedOutputStream);
    }

    @Test
    public void testEntityWithIdentityTransferEncoding() throws Exception {
        final SessionOutputBuffer outbuffer = new SessionOutputBufferMock();
        final HttpMessage message = new DummyHttpMessage();
        message.addHeader("Transfer-Encoding", "Identity");

        final EntitySerializer entitywriter = new EntitySerializer(
                new StrictContentLengthStrategy());
        final OutputStream outstream = entitywriter.doSerialize(outbuffer, message);
        Assert.assertNotNull(outstream);
        Assert.assertTrue(outstream instanceof IdentityOutputStream);
    }

    @Test
    public void testEntityWithInvalidTransferEncoding() throws Exception {
        final SessionOutputBuffer outbuffer = new SessionOutputBufferMock();
        final HttpMessage message = new DummyHttpMessage();
        message.addHeader("Transfer-Encoding", "whatever");

        final EntitySerializer entitywriter = new EntitySerializer(
                new StrictContentLengthStrategy());
        try {
            entitywriter.doSerialize(outbuffer, message);
            Assert.fail("ProtocolException should have been thrown");
        } catch (final ProtocolException ex) {
            // expected
        }
    }

    @Test
    public void testEntityWithInvalidChunkEncodingAndHTTP10() throws Exception {
        final SessionOutputBuffer outbuffer = new SessionOutputBufferMock();
        final HttpMessage message = new DummyHttpMessage(HttpVersion.HTTP_1_0);
        message.addHeader("Transfer-Encoding", "chunked");

        final EntitySerializer entitywriter = new EntitySerializer(
                new StrictContentLengthStrategy());
        try {
            entitywriter.doSerialize(outbuffer, message);
            Assert.fail("ProtocolException should have been thrown");
        } catch (final ProtocolException ex) {
            // expected
        }
    }

    @Test
    public void testEntityWithContentLength() throws Exception {
        final SessionOutputBuffer outbuffer = new SessionOutputBufferMock();
        final HttpMessage message = new DummyHttpMessage();
        message.addHeader("Content-Length", "100");
        final EntitySerializer entitywriter = new EntitySerializer(
                new StrictContentLengthStrategy());
        final OutputStream outstream = entitywriter.doSerialize(outbuffer, message);
        Assert.assertNotNull(outstream);
        Assert.assertTrue(outstream instanceof ContentLengthOutputStream);
    }

    @Test
    public void testEntityWithInvalidContentLength() throws Exception {
        final SessionOutputBuffer outbuffer = new SessionOutputBufferMock();
        final HttpMessage message = new DummyHttpMessage();
        message.addHeader("Content-Length", "whatever");

        final EntitySerializer entitywriter = new EntitySerializer(
                new StrictContentLengthStrategy());
        try {
            entitywriter.doSerialize(outbuffer, message);
            Assert.fail("ProtocolException should have been thrown");
        } catch (final ProtocolException ex) {
            // expected
        }
    }

    @Test
    public void testEntityNoContentDelimiter() throws Exception {
        final SessionOutputBuffer outbuffer = new SessionOutputBufferMock();
        final HttpMessage message = new DummyHttpMessage();
        final EntitySerializer entitywriter = new EntitySerializer(
                new StrictContentLengthStrategy());
        final OutputStream outstream = entitywriter.doSerialize(outbuffer, message);
        Assert.assertNotNull(outstream);
        Assert.assertTrue(outstream instanceof IdentityOutputStream);
    }

    @Test
    public void testEntitySerialization() throws Exception {
        final byte[] content = new byte[] {1, 2, 3, 4, 5};
        final ByteArrayEntity entity = new ByteArrayEntity(content);

        final SessionOutputBufferMock outbuffer = new SessionOutputBufferMock();
        final HttpMessage message = new DummyHttpMessage();
        message.addHeader("Content-Length", Integer.toString(content.length));

        final EntitySerializer entitywriter = new EntitySerializer(
                new StrictContentLengthStrategy());
        entitywriter.serialize(outbuffer, message, entity);

        final byte[] data = outbuffer.getData();
        Assert.assertNotNull(data);
        Assert.assertEquals(content.length, data.length);
    }
}


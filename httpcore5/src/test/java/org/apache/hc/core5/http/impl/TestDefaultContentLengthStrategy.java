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

package org.apache.hc.core5.http.impl;

import org.apache.hc.core5.http.ContentLengthStrategy;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.NotImplementedException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestDefaultContentLengthStrategy {

    static class TestHttpMessage extends HeaderGroup implements HttpMessage {

        private static final long serialVersionUID = 1L;

        @Override
        public ProtocolVersion getVersion() {
            return null;
        }

        @Override
        public void addHeader(final String name, final Object value) {
            addHeader(new BasicHeader(name, value));
        }

        @Override
        public void setHeader(final String name, final Object value) {
            setHeader(new BasicHeader(name, value));
        }

        @Override
        public void setVersion(final ProtocolVersion version) {
        }

    }

    @Test
    void testEntityWithChunkTransferEncoding() throws Exception {
        final ContentLengthStrategy lenStrategy = new DefaultContentLengthStrategy();
        final HttpMessage message = new TestHttpMessage();
        message.addHeader("Transfer-Encoding", "Chunked");

        Assertions.assertEquals(ContentLengthStrategy.CHUNKED, lenStrategy.determineLength(message));
    }

    @Test
    void testEntityWithChunkTransferEncodingAndEmptyTokens() throws Exception {
        final ContentLengthStrategy lenStrategy = new DefaultContentLengthStrategy();
        final HttpMessage message = new TestHttpMessage();
        message.addHeader("Transfer-Encoding", ",,Chunked,,");

        Assertions.assertEquals(ContentLengthStrategy.CHUNKED, lenStrategy.determineLength(message));
    }

    @Test
    void testEntityWithChunkTransferEncodingDoubleChunk() {
        final ContentLengthStrategy lenStrategy = new DefaultContentLengthStrategy();
        final HttpMessage message = new TestHttpMessage();
        message.addHeader("Transfer-Encoding", "Chunked,Chunked");
        Assertions.assertThrows(NotImplementedException.class, () ->
                lenStrategy.determineLength(message));
    }

    @Test
    void testEntityWithChunkTransferEncodingUnknown1() {
        final ContentLengthStrategy lenStrategy = new DefaultContentLengthStrategy();
        final HttpMessage message = new TestHttpMessage();
        message.addHeader("Transfer-Encoding", "blah");
        Assertions.assertThrows(NotImplementedException.class, () ->
                lenStrategy.determineLength(message));
    }

    @Test
    void testEntityWithChunkTransferEncodingUnknown2() {
        final ContentLengthStrategy lenStrategy = new DefaultContentLengthStrategy();
        final HttpMessage message = new TestHttpMessage();
        message.addHeader("Transfer-Encoding", "blah, chunked");
        Assertions.assertThrows(NotImplementedException.class, () ->
                lenStrategy.determineLength(message));
    }

    @Test
    void testEntityWithChunkTransferEncodingUnknown3() {
        final ContentLengthStrategy lenStrategy = new DefaultContentLengthStrategy();
        final HttpMessage message = new TestHttpMessage();
        message.addHeader("Transfer-Encoding", "chunked,blah");
        Assertions.assertThrows(NotImplementedException.class, () ->
                lenStrategy.determineLength(message));
    }

    @Test
    void testEntityWithIdentityTransferEncoding() {
        final ContentLengthStrategy lenStrategy = new DefaultContentLengthStrategy();
        final HttpMessage message = new TestHttpMessage();
        message.addHeader("Transfer-Encoding", "Identity");
        Assertions.assertThrows(NotImplementedException.class, () ->
                lenStrategy.determineLength(message));
    }

    @Test
    void testEntityWithContentLength() throws Exception {
        final ContentLengthStrategy lenStrategy = new DefaultContentLengthStrategy();
        final HttpMessage message = new TestHttpMessage();
        message.addHeader("Content-Length", "100");
        Assertions.assertEquals(100, lenStrategy.determineLength(message));
    }

    @Test
    void testEntityWithInvalidContentLength() {
        final ContentLengthStrategy lenStrategy = new DefaultContentLengthStrategy();
        final HttpMessage message = new TestHttpMessage();
        message.addHeader("Content-Length", "whatever");
        Assertions.assertThrows(ProtocolException.class, () ->
                lenStrategy.determineLength(message));
    }

    @Test
    void testEntityWithNegativeContentLength() {
        final ContentLengthStrategy lenStrategy = new DefaultContentLengthStrategy();
        final HttpMessage message = new TestHttpMessage();
        message.addHeader("Content-Length", "-10");
        Assertions.assertThrows(ProtocolException.class, () ->
                lenStrategy.determineLength(message));
    }

    @Test
    void testEntityNoContentDelimiter() throws Exception {
        final ContentLengthStrategy lenStrategy = new DefaultContentLengthStrategy();
        final HttpMessage message = new TestHttpMessage();
        Assertions.assertEquals(ContentLengthStrategy.UNDEFINED, lenStrategy.determineLength(message));
    }

}


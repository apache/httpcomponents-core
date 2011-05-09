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

import org.apache.http.HttpMessage;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.entity.ContentLengthStrategy;
import org.apache.http.mockup.HttpMessageMockup;
import org.apache.http.params.CoreProtocolPNames;
import org.junit.Assert;
import org.junit.Test;

public class TestStrictContentLengthStrategy {

    @Test
    public void testEntityWithChunkTransferEncoding() throws Exception {
        ContentLengthStrategy lenStrategy = new StrictContentLengthStrategy();
        HttpMessage message = new HttpMessageMockup();
        message.addHeader("Transfer-Encoding", "Chunked");

        Assert.assertEquals(ContentLengthStrategy.CHUNKED, lenStrategy.determineLength(message));
    }

    @Test
    public void testEntityWithIdentityTransferEncoding() throws Exception {
        ContentLengthStrategy lenStrategy = new StrictContentLengthStrategy();
        HttpMessage message = new HttpMessageMockup();
        message.addHeader("Transfer-Encoding", "Identity");

        Assert.assertEquals(ContentLengthStrategy.IDENTITY, lenStrategy.determineLength(message));
    }

    @Test
    public void testEntityWithInvalidTransferEncoding() throws Exception {
        ContentLengthStrategy lenStrategy = new StrictContentLengthStrategy();
        HttpMessage message = new HttpMessageMockup();
        message.addHeader("Transfer-Encoding", "whatever");

        try {
            lenStrategy.determineLength(message);
            Assert.fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    @Test
    public void testEntityWithInvalidChunkEncodingAndHTTP10() throws Exception {
        ContentLengthStrategy lenStrategy = new StrictContentLengthStrategy();
        HttpMessage message = new HttpMessageMockup();
        message.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION,
                HttpVersion.HTTP_1_0);
        message.addHeader("Transfer-Encoding", "chunked");

        try {
            lenStrategy.determineLength(message);
            Assert.fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    @Test
    public void testEntityWithContentLength() throws Exception {
        ContentLengthStrategy lenStrategy = new StrictContentLengthStrategy();
        HttpMessage message = new HttpMessageMockup();
        message.addHeader("Content-Length", "100");
        Assert.assertEquals(100, lenStrategy.determineLength(message));
    }

    @Test
    public void testEntityWithInvalidContentLength() throws Exception {
        ContentLengthStrategy lenStrategy = new StrictContentLengthStrategy();
        HttpMessage message = new HttpMessageMockup();
        message.addHeader("Content-Length", "whatever");

        try {
            lenStrategy.determineLength(message);
            Assert.fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    @Test
    public void testEntityNoContentDelimiter() throws Exception {
        ContentLengthStrategy lenStrategy = new StrictContentLengthStrategy();
        HttpMessage message = new HttpMessageMockup();
        Assert.assertEquals(ContentLengthStrategy.IDENTITY, lenStrategy.determineLength(message));
    }

}


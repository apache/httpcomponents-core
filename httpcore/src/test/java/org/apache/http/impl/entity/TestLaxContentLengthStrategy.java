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

import junit.framework.TestCase;

import org.apache.http.HttpMessage;
import org.apache.http.ProtocolException;
import org.apache.http.entity.ContentLengthStrategy;
import org.apache.http.mockup.HttpMessageMockup;
import org.apache.http.params.CoreProtocolPNames;

public class TestLaxContentLengthStrategy extends TestCase {

    public TestLaxContentLengthStrategy(String testName) {
        super(testName);
    }

    public void testEntityWithTransferEncoding() throws Exception {
        ContentLengthStrategy lenStrategy = new LaxContentLengthStrategy();
        HttpMessage message = new HttpMessageMockup();

        // lenient mode
        message.getParams().setBooleanParameter(CoreProtocolPNames.STRICT_TRANSFER_ENCODING, false);
        message.addHeader("Content-Type", "unknown");
        message.addHeader("Transfer-Encoding", "identity, chunked");
        message.addHeader("Content-Length", "plain wrong");
        assertEquals(ContentLengthStrategy.CHUNKED, lenStrategy.determineLength(message));

        // strict mode
        message.getParams().setBooleanParameter(CoreProtocolPNames.STRICT_TRANSFER_ENCODING, true);
        assertEquals(ContentLengthStrategy.CHUNKED, lenStrategy.determineLength(message));
    }

    public void testEntityWithIdentityTransferEncoding() throws Exception {
        ContentLengthStrategy lenStrategy = new LaxContentLengthStrategy();
        HttpMessage message = new HttpMessageMockup();

        // lenient mode
        message.getParams().setBooleanParameter(CoreProtocolPNames.STRICT_TRANSFER_ENCODING, false);
        message.addHeader("Content-Type", "unknown");
        message.addHeader("Transfer-Encoding", "identity");
        message.addHeader("Content-Length", "plain wrong");
        assertEquals(ContentLengthStrategy.IDENTITY, lenStrategy.determineLength(message));
    }

    public void testEntityWithUnsupportedTransferEncoding() throws Exception {
        ContentLengthStrategy lenStrategy = new LaxContentLengthStrategy();
        HttpMessage message = new HttpMessageMockup();

        // lenient mode
        message.getParams().setBooleanParameter(CoreProtocolPNames.STRICT_TRANSFER_ENCODING, false);
        message.addHeader("Content-Type", "unknown");
        message.addHeader("Transfer-Encoding", "whatever; param=value, chunked");
        message.addHeader("Content-Length", "plain wrong");
        assertEquals(ContentLengthStrategy.CHUNKED, lenStrategy.determineLength(message));

        // strict mode
        message.getParams().setBooleanParameter(CoreProtocolPNames.STRICT_TRANSFER_ENCODING, true);
        try {
            lenStrategy.determineLength(message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    public void testChunkedTransferEncodingMustBeLast() throws Exception {
        ContentLengthStrategy lenStrategy = new LaxContentLengthStrategy();
        HttpMessage message = new HttpMessageMockup();

        // lenient mode
        message.getParams().setBooleanParameter(CoreProtocolPNames.STRICT_TRANSFER_ENCODING, false);
        message.addHeader("Content-Type", "unknown");
        message.addHeader("Transfer-Encoding", "chunked, identity");
        message.addHeader("Content-Length", "plain wrong");
        assertEquals(ContentLengthStrategy.IDENTITY, lenStrategy.determineLength(message));

        // strict mode
        message.getParams().setBooleanParameter(CoreProtocolPNames.STRICT_TRANSFER_ENCODING, true);
        try {
            lenStrategy.determineLength(message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    public void testEntityWithContentLength() throws Exception {
        ContentLengthStrategy lenStrategy = new LaxContentLengthStrategy();
        HttpMessage message = new HttpMessageMockup();

        // lenient mode
        message.getParams().setBooleanParameter(CoreProtocolPNames.STRICT_TRANSFER_ENCODING, false);
        message.addHeader("Content-Type", "unknown");
        message.addHeader("Content-Length", "0");
        assertEquals(0, lenStrategy.determineLength(message));
    }

    public void testEntityWithMultipleContentLength() throws Exception {
        ContentLengthStrategy lenStrategy = new LaxContentLengthStrategy();
        HttpMessage message = new HttpMessageMockup();

        // lenient mode
        message.getParams().setBooleanParameter(CoreProtocolPNames.STRICT_TRANSFER_ENCODING, false);
        message.addHeader("Content-Type", "unknown");
        message.addHeader("Content-Length", "0");
        message.addHeader("Content-Length", "0");
        message.addHeader("Content-Length", "1");
        assertEquals(1, lenStrategy.determineLength(message));

        // strict mode
        message.getParams().setBooleanParameter(CoreProtocolPNames.STRICT_TRANSFER_ENCODING, true);
        try {
            lenStrategy.determineLength(message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    public void testEntityWithMultipleContentLengthSomeWrong() throws Exception {
        ContentLengthStrategy lenStrategy = new LaxContentLengthStrategy();
        HttpMessage message = new HttpMessageMockup();

        // lenient mode
        message.getParams().setBooleanParameter(CoreProtocolPNames.STRICT_TRANSFER_ENCODING, false);
        message.addHeader("Content-Type", "unknown");
        message.addHeader("Content-Length", "1");
        message.addHeader("Content-Length", "yyy");
        message.addHeader("Content-Length", "xxx");
        assertEquals(1, lenStrategy.determineLength(message));

        // strict mode
        message.getParams().setBooleanParameter(CoreProtocolPNames.STRICT_TRANSFER_ENCODING, true);
        try {
            lenStrategy.determineLength(message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    public void testEntityWithMultipleContentLengthAllWrong() throws Exception {
        ContentLengthStrategy lenStrategy = new LaxContentLengthStrategy();
        HttpMessage message = new HttpMessageMockup();

        // lenient mode
        message.getParams().setBooleanParameter(CoreProtocolPNames.STRICT_TRANSFER_ENCODING, false);
        message.addHeader("Content-Type", "unknown");
        message.addHeader("Content-Length", "yyy");
        message.addHeader("Content-Length", "xxx");
        assertEquals(ContentLengthStrategy.IDENTITY, lenStrategy.determineLength(message));

        // strict mode
        message.getParams().setBooleanParameter(CoreProtocolPNames.STRICT_TRANSFER_ENCODING, true);
        try {
            lenStrategy.determineLength(message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    public void testEntityWithInvalidContentLength() throws Exception {
        ContentLengthStrategy lenStrategy = new LaxContentLengthStrategy();
        HttpMessage message = new HttpMessageMockup();

        // lenient mode
        message.getParams().setBooleanParameter(CoreProtocolPNames.STRICT_TRANSFER_ENCODING, false);
        message.addHeader("Content-Type", "unknown");
        message.addHeader("Content-Length", "xxx");
        assertEquals(ContentLengthStrategy.IDENTITY, lenStrategy.determineLength(message));

        // strict mode
        message.getParams().setBooleanParameter(CoreProtocolPNames.STRICT_TRANSFER_ENCODING, true);
        try {
            lenStrategy.determineLength(message);
            fail("ProtocolException should have been thrown");
        } catch (ProtocolException ex) {
            // expected
        }
    }

    public void testEntityNeitherContentLengthNorTransferEncoding() throws Exception {
        ContentLengthStrategy lenStrategy = new LaxContentLengthStrategy();
        HttpMessage message = new HttpMessageMockup();

        // lenient mode
        assertEquals(ContentLengthStrategy.IDENTITY, lenStrategy.determineLength(message));
    }

}


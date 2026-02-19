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

package org.apache.hc.core5.http.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.junit.jupiter.api.Test;

class TestResponseContent {

    @Test
    void testNoContentLengthForInformationalResponses() throws Exception {
        final ResponseContent interceptor = new ResponseContent();
        final HttpCoreContext context = HttpCoreContext.create();
        context.setProtocolVersion(HttpVersion.HTTP_1_1);

        for (int status = 100; status < 200; status++) {
            final BasicHttpResponse response = new BasicHttpResponse(status);
            interceptor.process(response, null, context);

            assertFalse(response.containsHeader(HttpHeaders.CONTENT_LENGTH),
                    "Unexpected Content-Length for status " + status);
        }
    }

    @Test
    void testContentLengthZeroForOkResponseWithoutEntity() throws Exception {
        final ResponseContent interceptor = new ResponseContent();
        final HttpCoreContext context = HttpCoreContext.create();
        context.setProtocolVersion(HttpVersion.HTTP_1_1);

        final BasicHttpResponse response = new BasicHttpResponse(HttpStatus.SC_OK);
        interceptor.process(response, null, context);

        assertNotNull(response.getFirstHeader(HttpHeaders.CONTENT_LENGTH));
        assertEquals("0", response.getFirstHeader(HttpHeaders.CONTENT_LENGTH).getValue());
    }

    @Test
    void testNoContentLengthForNoContentAndNotModified() throws Exception {
        final ResponseContent interceptor = new ResponseContent();
        final HttpCoreContext context = HttpCoreContext.create();
        context.setProtocolVersion(HttpVersion.HTTP_1_1);

        final BasicHttpResponse r204 = new BasicHttpResponse(HttpStatus.SC_NO_CONTENT);
        interceptor.process(r204, null, context);
        assertFalse(r204.containsHeader(HttpHeaders.CONTENT_LENGTH));

        final BasicHttpResponse r304 = new BasicHttpResponse(HttpStatus.SC_NOT_MODIFIED);
        interceptor.process(r304, null, context);
        assertFalse(r304.containsHeader(HttpHeaders.CONTENT_LENGTH));
    }
}

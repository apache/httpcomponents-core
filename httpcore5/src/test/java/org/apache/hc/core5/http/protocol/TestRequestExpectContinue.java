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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestRequestExpectContinue {

    private HttpContext context;
    private BasicHttpRequest request;
    private EntityDetails entity;

    @BeforeEach
    public void setup() {
        context = new BasicHttpContext();
        request = new BasicHttpRequest("GET", "/");
        entity = mock(EntityDetails.class);
    }

    @Test
    public void testDefaultExpectHeaderAdded() throws Exception {
        when(entity.getContentLength()).thenReturn(10L);

        final RequestExpectContinue interceptor = new RequestExpectContinue(false);
        interceptor.process(request, entity, context);

        final Header expectHeader = request.getFirstHeader(HttpHeaders.EXPECT);
        assert (expectHeader != null);
        assert (expectHeader.getValue().equalsIgnoreCase(HeaderElements.CONTINUE));
    }

    @Test
    public void testShouldRespond417() throws Exception {
        request.addHeader(HttpHeaders.EXPECT, "random-value");
        final RequestExpectContinue interceptor = new RequestExpectContinue(true);

        try {
            interceptor.process(request, entity, context);
        } catch (final ProtocolException e) {
            assert (e.getMessage().equals("417 Expectation Failed"));
        }
    }

    @Test
    public void testShouldNotRespond417() throws Exception {
        request.addHeader(HttpHeaders.EXPECT, "random-value");
        final RequestExpectContinue interceptor = new RequestExpectContinue(false);

        try {
            interceptor.process(request, entity, context);
        } catch (final ProtocolException e) {
            // Should not throw this exception
            assert (false);
        }
    }

    @Test
    public void testExpectContinueHeaderAlreadyPresent() throws Exception {
        request.addHeader(HttpHeaders.EXPECT, HeaderElements.CONTINUE);
        when(entity.getContentLength()).thenReturn(10L);

        final RequestExpectContinue interceptor = new RequestExpectContinue(false);
        interceptor.process(request, entity, context);

        final Header[] headers = request.getHeaders(HttpHeaders.EXPECT);
        assert (headers.length == 1);
        assert (headers[0].getValue().equalsIgnoreCase(HeaderElements.CONTINUE));
    }

    @Test
    public void testContentLengthZero() throws Exception {
        when(entity.getContentLength()).thenReturn(0L);

        final RequestExpectContinue interceptor = new RequestExpectContinue(false);
        interceptor.process(request, entity, context);

        final Header expectHeader = request.getFirstHeader(HttpHeaders.EXPECT);
        assert (expectHeader == null);
    }
}

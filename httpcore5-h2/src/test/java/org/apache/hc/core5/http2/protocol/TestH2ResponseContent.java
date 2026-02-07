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
package org.apache.hc.core5.http2.protocol;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestH2ResponseContent {

    @Test
    void addsContentHeadersForHttp2Entity() throws Exception {
        final BasicHttpResponse response = new BasicHttpResponse(200);
        final HttpCoreContext context = HttpCoreContext.create();
        context.setProtocolVersion(HttpVersion.HTTP_2);

        final BasicEntityDetails entity = new BasicEntityDetails(10, ContentType.TEXT_PLAIN);
        H2ResponseContent.INSTANCE.process(response, entity, context);

        Assertions.assertTrue(response.containsHeader(HttpHeaders.CONTENT_TYPE));
        Assertions.assertFalse(response.containsHeader(HttpHeaders.CONTENT_LENGTH));
    }

    @Test
    void addsContentLengthForHttp1() throws Exception {
        final BasicHttpResponse response = new BasicHttpResponse(200);
        final HttpCoreContext context = HttpCoreContext.create();
        context.setProtocolVersion(HttpVersion.HTTP_1_1);

        final BasicEntityDetails entity = new BasicEntityDetails(5, ContentType.TEXT_PLAIN);
        H2ResponseContent.INSTANCE.process(response, entity, context);

        Assertions.assertEquals("5", response.getFirstHeader(HttpHeaders.CONTENT_LENGTH).getValue());
    }

}

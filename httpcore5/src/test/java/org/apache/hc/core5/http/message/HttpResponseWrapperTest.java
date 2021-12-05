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

package org.apache.hc.core5.http.message;

import java.util.Locale;

import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HttpResponseWrapperTest {

    @Test
    void testDefaultResponseConstructors() {
        final HttpResponse response1 = new BasicHttpResponse(HttpStatus.SC_BAD_REQUEST, "Bad Request");
        final HttpResponseWrapper httpResponseWrapper1 = new HttpResponseWrapper(response1);

        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, httpResponseWrapper1.getCode());

        final HttpResponse response2 = new BasicHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "whatever");
        final HttpResponseWrapper httpResponseWrapper2 = new HttpResponseWrapper(response2);
        Assertions.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, httpResponseWrapper2.getCode());
        Assertions.assertEquals("whatever", httpResponseWrapper2.getReasonPhrase());

        httpResponseWrapper2.setReasonPhrase("another-whatever");
        Assertions.assertEquals("another-whatever", httpResponseWrapper2.getReasonPhrase());
    }

    @Test
    void testSetResponseStatus() {
        final HttpResponse response1 = new BasicHttpResponse(200, "OK");
        final HttpResponseWrapper httpResponseWrapper1 = new HttpResponseWrapper(response1);

        Assertions.assertNotNull(httpResponseWrapper1.getCode());
        Assertions.assertEquals(200, httpResponseWrapper1.getCode());

        final HttpResponse response2 = new BasicHttpResponse(HttpStatus.SC_BAD_REQUEST, "Bad Request");
        final HttpResponseWrapper httpResponseWrapper2 = new HttpResponseWrapper(response2);
        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, httpResponseWrapper2.getCode());

        final HttpResponse response3 = new BasicHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, "whatever");
        final HttpResponseWrapper httpResponseWrapper3 = new HttpResponseWrapper(response3);
        Assertions.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, httpResponseWrapper3.getCode());
        Assertions.assertEquals("whatever", httpResponseWrapper3.getReasonPhrase());

        final HttpResponse response4 = new BasicHttpResponse(HttpStatus.SC_OK, "OK");
        final HttpResponseWrapper httpResponseWrapper4 = new HttpResponseWrapper(response4);
        Assertions.assertThrows(IllegalArgumentException.class, () -> httpResponseWrapper4.setCode(-23));
    }

    @Test
    void testLocale() {
        final HttpResponse response = new BasicHttpResponse(200, "OK");
        final HttpResponseWrapper httpResponseWrapper = new HttpResponseWrapper(response);
        httpResponseWrapper.setLocale(Locale.US);
        Assertions.assertEquals("US", httpResponseWrapper.getLocale().getCountry());
    }

}
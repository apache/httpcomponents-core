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
package org.apache.hc.core5.http2.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http2.H2PseudoRequestHeaders;
import org.apache.hc.core5.net.URIAuthority;
import org.junit.jupiter.api.Test;

class TestExtendedConnectRequestConverter {

    @Test
    void parsesExtendedConnect() throws Exception {
        final List<Header> headers = new ArrayList<>();
        headers.add(new BasicHeader(H2PseudoRequestHeaders.METHOD, Method.CONNECT.name(), false));
        headers.add(new BasicHeader(H2PseudoRequestHeaders.PROTOCOL, "websocket", false));
        headers.add(new BasicHeader(H2PseudoRequestHeaders.SCHEME, "https", false));
        headers.add(new BasicHeader(H2PseudoRequestHeaders.AUTHORITY, "example.com", false));
        headers.add(new BasicHeader(H2PseudoRequestHeaders.PATH, "/echo", false));

        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        final HttpRequest request = converter.convert(headers);
        assertNotNull(request);
        assertEquals(Method.CONNECT.name(), request.getMethod());
        assertEquals("/echo", request.getPath());
        assertEquals("websocket", request.getFirstHeader(H2PseudoRequestHeaders.PROTOCOL).getValue());
    }

    @Test
    void emitsProtocolPseudoHeader() throws Exception {
        final DefaultH2RequestConverter converter = new DefaultH2RequestConverter();
        final HttpRequest request = new org.apache.hc.core5.http.message.BasicHttpRequest(Method.CONNECT.name(), "/echo");
        request.setScheme("https");
        request.setAuthority(new URIAuthority("example.com"));
        request.setPath("/echo");
        request.addHeader(H2PseudoRequestHeaders.PROTOCOL, "websocket");
        final List<Header> headers = converter.convert(request);
        boolean found = false;
        for (final Header header : headers) {
            if (H2PseudoRequestHeaders.PROTOCOL.equals(header.getName())) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }
}

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.net.URIAuthority;
import org.junit.jupiter.api.Test;

public class TesForwardedRequest {
    private final HttpRequest request = new BasicHttpRequest("GET", "/");
    private final HttpContext context = mock(HttpContext.class);

    private static final String FORWARDED_HEADER_NAME = "Forwarded";

    @Test
    public void testProcess() throws IOException, HttpException {
        final HttpRequestInterceptor processor = new ForwardedRequest();

        // Create a mock endpoint with a remote address
        final InetSocketAddress remoteAddress = new InetSocketAddress("192.168.1.100", 12345);
        final InetSocketAddress localAddress = new InetSocketAddress("127.0.0.1", 2263);

        final EndpointDetails endpointDetails = mock(EndpointDetails.class);
        when(endpointDetails.getRemoteAddress()).thenReturn(remoteAddress);
        when(endpointDetails.getLocalAddress()).thenReturn(localAddress);

        // Create a mock HTTP request with a host and port
        final String host = "somehost";
        final int port = 8888;

        request.setAuthority(new URIAuthority(host, port));

        // Create a mock HTTP context with the endpoint and protocol version
        final ProtocolVersion version = HttpVersion.HTTP_1_1;
        final HttpContext context = new BasicHttpContext();
        context.setAttribute(HttpCoreContext.CONNECTION_ENDPOINT, endpointDetails);

        // Process the HTTP request
        processor.process(request, new BasicEntityDetails(1, ContentType.APPLICATION_JSON), context);

        // Check the value of the Forwarded header
        final String expectedValue = "by=" + remoteAddress.getAddress().getHostAddress() + ":" + remoteAddress.getPort() + ";" +
                "for=" + localAddress.getAddress().getHostAddress() + ":" + localAddress.getPort() + ";" +
                "host=\"" + host + "\";port=" + port + ";proto=" + version.getProtocol();

        assertEquals(expectedValue, request.getFirstHeader(FORWARDED_HEADER_NAME).getValue());
    }


    @Test
    public void testProcessWithIPv6() throws IOException, HttpException {
        final HttpRequestInterceptor processor = new ForwardedRequest();

        // Create a mock endpoint with a remote IPv6 address
        final InetSocketAddress remoteAddress = InetSocketAddress.createUnresolved("[2001:0db8:85a3:0000:0000:8a2e:0370:7334]", 12345);
        final InetSocketAddress localAddress = InetSocketAddress.createUnresolved("[0:0:0:0:0:0:0:1]", 2263);

        final EndpointDetails endpointDetails = mock(EndpointDetails.class);
        when(endpointDetails.getRemoteAddress()).thenReturn(remoteAddress);
        when(endpointDetails.getLocalAddress()).thenReturn(localAddress);

        // Create a mock HTTP request with a host and port
        final String host = "somehost";
        final int port = 8888;

        request.setAuthority(new URIAuthority(host, port));

        // Create a mock HTTP context with the endpoint and protocol version
        final ProtocolVersion version = HttpVersion.HTTP_1_1;
        final HttpContext context = new BasicHttpContext();
        context.setAttribute(HttpCoreContext.CONNECTION_ENDPOINT, endpointDetails);

        // Process the HTTP request
        processor.process(request, new BasicEntityDetails(1, ContentType.APPLICATION_JSON), context);

        // Check the value of the Forwarded header
        final String expectedValue = "by=" + remoteAddress.getHostName() + ":" + remoteAddress.getPort() +
                ";for=" + localAddress.getHostName()  + ":" + localAddress.getPort() +
                ";host=\"" + host + "\";port=" + port + ";proto=" + version.getProtocol();


        assertEquals(expectedValue, request.getFirstHeader(FORWARDED_HEADER_NAME).getValue());
    }

    @Test
    void testProcess_withNullEndpointDetails_shouldAddValidHeader() throws Exception {
        final HttpRequestInterceptor processor = new ForwardedRequest();

        // Create a mock HTTP request with a host and port
        final String host = "somehost";
        final int port = 8888;

        request.setAuthority(new URIAuthority(host, port));

        // Create a mock HTTP context with the endpoint and protocol version
        final ProtocolVersion version = HttpVersion.HTTP_1_1;
        final HttpContext context = new BasicHttpContext();
        context.setAttribute(HttpCoreContext.CONNECTION_ENDPOINT, null);

        // Process the HTTP request
        processor.process(request, new BasicEntityDetails(1, ContentType.APPLICATION_JSON), context);

        // Check the value of the Forwarded header
        final String expectedValue = "host=\"" + host + "\";port=" + port + ";proto=" + version.getProtocol();

        assertEquals(expectedValue, request.getFirstHeader(FORWARDED_HEADER_NAME).getValue());
    }

    @Test
    public void testWithForwardedHeader() throws Exception {
        final HttpRequestInterceptor processor = new ForwardedRequest();

        // Create a mock HTTP request with a host and port
        final String host = "newhost";
        final int port = 8888;

        request.setAuthority(new URIAuthority(host, port));

        // Create a mock HTTP context with the endpoint and protocol version
        final ProtocolVersion version = HttpVersion.HTTP_1_1;
        final HttpContext context = new BasicHttpContext();
        context.setAttribute(HttpCoreContext.CONNECTION_ENDPOINT, null);
        final String forwaredHeaderValue = "host=oldhost;port=8855;proto=HTTP";

        request.setHeader(new BasicHeader(FORWARDED_HEADER_NAME, forwaredHeaderValue));

         // Process the HTTP request
        processor.process(request, new BasicEntityDetails(1, ContentType.APPLICATION_JSON), context);

        // Check the value of the Forwarded header
        final String expectedValue = forwaredHeaderValue + ", " + "host=\"" + host + "\";port=" + port + ";proto=" + version.getProtocol();

        assertEquals(expectedValue, request.getFirstHeader(FORWARDED_HEADER_NAME).getValue());
    }

    @Test
    public void testProcessWithNullHttpRequest() {
        final ForwardedRequest httpRequestModifier = new ForwardedRequest();
        assertThrows(NullPointerException.class, () -> httpRequestModifier.process(null, null, context));
    }

    @Test
    public void testProcessWithNullHttpContext() {
        final ForwardedRequest httpRequestModifier = new ForwardedRequest();
        assertThrows(NullPointerException.class, () -> httpRequestModifier.process(request, null, null));
    }

    @Test
    public void testProcessWithNullAuthority() {
        final ForwardedRequest httpRequestModifier = new ForwardedRequest();
        assertThrows(ProtocolException.class, () -> httpRequestModifier.process(request, null, context));
    }
}

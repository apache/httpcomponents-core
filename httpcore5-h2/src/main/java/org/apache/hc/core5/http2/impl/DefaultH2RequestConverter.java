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

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http2.H2MessageConverter;
import org.apache.hc.core5.http2.H2PseudoRequestHeaders;
import org.apache.hc.core5.net.URIAuthority;
import org.apache.hc.core5.util.TextUtils;

/**
 * HTTP/2 request converter.
 *
 * @since 5.0
 */
public final class DefaultH2RequestConverter implements H2MessageConverter<HttpRequest> {

    public final static DefaultH2RequestConverter INSTANCE = new DefaultH2RequestConverter();

    @Override
    public HttpRequest convert(final List<Header> headers) throws HttpException {
        String method = null;
        String scheme = null;
        String authority = null;
        String path = null;
        final List<Header> messageHeaders = new ArrayList<>();

        for (int i = 0; i < headers.size(); i++) {
            final Header header = headers.get(i);
            final String name = header.getName();
            final String value = header.getValue();

            for (int n = 0; n < name.length(); n++) {
                final char ch = name.charAt(n);
                if (Character.isAlphabetic(ch) && !Character.isLowerCase(ch)) {
                    throw new ProtocolException("Header name '%s' is invalid (header name contains uppercase characters)", name);
                }
            }

            if (name.startsWith(":")) {
                if (!messageHeaders.isEmpty()) {
                    throw new ProtocolException("Invalid sequence of headers (pseudo-headers must precede message headers)");
                }

                switch (name) {
                    case H2PseudoRequestHeaders.METHOD:
                        if (method != null) {
                            throw new ProtocolException("Multiple '%s' request headers are illegal", name);
                        }
                        method = value;
                        break;
                    case H2PseudoRequestHeaders.SCHEME:
                        if (scheme != null) {
                            throw new ProtocolException("Multiple '%s' request headers are illegal", name);
                        }
                        scheme = value;
                        break;
                    case H2PseudoRequestHeaders.PATH:
                        if (path != null) {
                            throw new ProtocolException("Multiple '%s' request headers are illegal", name);
                        }
                        path = value;
                        break;
                    case H2PseudoRequestHeaders.AUTHORITY:
                        authority = value;
                        break;
                    default:
                        throw new ProtocolException("Unsupported request header '%s'", name);
                }
            } else {
                if (name.equalsIgnoreCase(HttpHeaders.CONNECTION) || name.equalsIgnoreCase(HttpHeaders.KEEP_ALIVE)
                    || name.equalsIgnoreCase(HttpHeaders.PROXY_CONNECTION) || name.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING)
                    || name.equalsIgnoreCase(HttpHeaders.HOST) || name.equalsIgnoreCase(HttpHeaders.UPGRADE)) {
                    throw new ProtocolException("Header '%s: %s' is illegal for HTTP/2 messages", header.getName(), header.getValue());
                }
                if (name.equalsIgnoreCase(HttpHeaders.TE) && !value.equalsIgnoreCase("trailers")) {
                    throw new ProtocolException("Header '%s: %s' is illegal for HTTP/2 messages", header.getName(), header.getValue());
                }
                messageHeaders.add(header);
            }
        }
        if (method == null) {
            throw new ProtocolException("Mandatory request header '%s' not found", H2PseudoRequestHeaders.METHOD);
        }
        if (Method.CONNECT.isSame(method)) {
            if (authority == null) {
                throw new ProtocolException("Header '%s' is mandatory for CONNECT request", H2PseudoRequestHeaders.AUTHORITY);
            }
            if (scheme != null) {
                throw new ProtocolException("Header '%s' must not be set for CONNECT request", H2PseudoRequestHeaders.SCHEME);
            }
            if (path != null) {
                throw new ProtocolException("Header '%s' must not be set for CONNECT request", H2PseudoRequestHeaders.PATH);
            }
        } else {
            if (scheme == null) {
                throw new ProtocolException("Mandatory request header '%s' not found", H2PseudoRequestHeaders.SCHEME);
            }
            if (path == null) {
                throw new ProtocolException("Mandatory request header '%s' not found", H2PseudoRequestHeaders.PATH);
            }
            validatePathPseudoHeader(method, scheme, path);
        }

        final HttpRequest httpRequest = new BasicHttpRequest(method, path);
        httpRequest.setVersion(HttpVersion.HTTP_2);
        httpRequest.setScheme(scheme);
        try {
            httpRequest.setAuthority(URIAuthority.create(authority));
        } catch (final URISyntaxException ex) {
            throw new ProtocolException(ex.getMessage(), ex);
        }
        httpRequest.setPath(path);
        for (int i = 0; i < messageHeaders.size(); i++) {
            httpRequest.addHeader(messageHeaders.get(i));
        }
        return httpRequest;
    }

    @Override
    public List<Header> convert(final HttpRequest message) throws HttpException {
        if (TextUtils.isBlank(message.getMethod())) {
            throw new ProtocolException("Request method is empty");
        }
        final boolean optionMethod = Method.CONNECT.name().equalsIgnoreCase(message.getMethod());
        if (optionMethod) {
            if (message.getAuthority() == null) {
                throw new ProtocolException("CONNECT request authority is not set");
            }
            if (message.getPath() != null) {
                throw new ProtocolException("CONNECT request path must be null");
            }
        } else {
            if (TextUtils.isBlank(message.getScheme())) {
                throw new ProtocolException("Request scheme is not set");
            }
            if (TextUtils.isBlank(message.getPath())) {
                throw new ProtocolException("Request path is not set");
            }
        }
        final List<Header> headers = new ArrayList<>();
        headers.add(new BasicHeader(H2PseudoRequestHeaders.METHOD, message.getMethod(), false));
        if (optionMethod) {
            headers.add(new BasicHeader(H2PseudoRequestHeaders.AUTHORITY, message.getAuthority(), false));
        }  else {
            headers.add(new BasicHeader(H2PseudoRequestHeaders.SCHEME, message.getScheme(), false));
            if (message.getAuthority() != null) {
                headers.add(new BasicHeader(H2PseudoRequestHeaders.AUTHORITY, message.getAuthority(), false));
            }
            headers.add(new BasicHeader(H2PseudoRequestHeaders.PATH, message.getPath(), false));
        }

        for (final Iterator<Header> it = message.headerIterator(); it.hasNext(); ) {
            final Header header = it.next();
            final String name = header.getName();
            final String value = header.getValue();
            if (name.startsWith(":")) {
                throw new ProtocolException("Header name '%s' is invalid", name);
            }
            if (name.equalsIgnoreCase(HttpHeaders.CONNECTION) || name.equalsIgnoreCase(HttpHeaders.KEEP_ALIVE)
                || name.equalsIgnoreCase(HttpHeaders.PROXY_CONNECTION) || name.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING)
                || name.equalsIgnoreCase(HttpHeaders.HOST) || name.equalsIgnoreCase(HttpHeaders.UPGRADE)) {
                throw new ProtocolException("Header '%s: %s' is illegal for HTTP/2 messages", header.getName(), header.getValue());
            }
            if (name.equalsIgnoreCase(HttpHeaders.TE) && !value.equalsIgnoreCase("trailers")) {
                throw new ProtocolException("Header '%s: %s' is illegal for HTTP/2 messages", header.getName(), header.getValue());
            }
            headers.add(new BasicHeader(TextUtils.toLowerCase(name), value));
        }

        return headers;
    }

    /**
     * Validates the {@code :path} pseudo-header field based on the provided HTTP method and scheme.
     * <p>
     * This method performs the following validations:
     * </p>
     * <ul>
     *     <li><strong>Non-Empty Path:</strong> For 'http' or 'https' URIs, the {@code :path} pseudo-header field must not be empty.</li>
     *     <li><strong>OPTIONS Method:</strong> If the HTTP method is OPTIONS and the URI does not contain a path component,
     *         the {@code :path} pseudo-header field must have a value of '*'. </li>
     *     <li><strong>Path Starting with '/':</strong> For 'http' or 'https' URIs, the {@code :path} pseudo-header field must either start with '/' or be '*'. </li>
     * </ul>
     *
     * @param method The HTTP method of the request, e.g., GET, POST, OPTIONS, etc.
     * @param scheme The scheme of the request, e.g., http or https.
     * @param path The value of the {@code :path} pseudo-header field.
     * @throws ProtocolException if any of the validations fail.
     */
    private void validatePathPseudoHeader(final String method, final String scheme, final String path) throws ProtocolException {
        if (URIScheme.HTTP.name().equalsIgnoreCase(scheme) || URIScheme.HTTPS.name().equalsIgnoreCase(scheme)) {
            if (TextUtils.isBlank(path)) {
                throw new ProtocolException("':path' pseudo-header field must not be empty for 'http' or 'https' URIs");
            } else {
                final boolean isRoot = path.startsWith("/");
                if (Method.OPTIONS.isSame(method)) {
                    if (!"*".equals(path) && !isRoot) {
                        throw new ProtocolException("OPTIONS request for an 'http' or 'https' URI must have a ':path' pseudo-header field with a value of '*' or '/'");
                    }
                } else {
                    if (!isRoot) {
                        throw new ProtocolException("':path' pseudo-header field for 'http' or 'https' URIs must start with '/'");
                    }
                }
            }
        }
    }
}

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

package org.apache.hc.core5.testing.framework;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.testing.classic.ClassicTestClient;
import org.apache.hc.core5.util.Timeout;

public class ClassicTestClientAdapter extends ClientPOJOAdapter {

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> execute(final String defaultURI, final Map<String, Object> request) throws Exception {
        // check the request for missing items.
        if (defaultURI == null) {
            throw new HttpException("defaultURL cannot be null");
        }
        if (request == null) {
            throw new HttpException("request cannot be null");
        }
        if (! request.containsKey(PATH)) {
            throw new HttpException("Request path should be set.");
        }
        if (! request.containsKey(METHOD)) {
            throw new HttpException("Request method should be set.");
        }

        final Timeout timeout;
        if (request.containsKey(TIMEOUT)) {
            timeout = Timeout.ofMilliseconds((long) request.get(TIMEOUT));
        } else {
            timeout = null;
        }
        final ClassicTestClient client = new ClassicTestClient(SocketConfig.custom()
                .setSoTimeout(timeout)
                .build());

        // Append the path to the defaultURI.
        String tempDefaultURI = defaultURI;
        if (! defaultURI.endsWith("/")) {
            tempDefaultURI += "/";
        }
        final URI startingURI = new URI(tempDefaultURI + request.get(PATH));
        final URI uri;

        // append each parameter in the query to the uri.
        @SuppressWarnings("unchecked")
        final Map<String, String> queryMap = (Map<String, String>) request.get(QUERY);
        if (queryMap != null) {
            final String existingQuery = startingURI.getRawQuery();
            final StringBuilder newQuery = new StringBuilder(existingQuery == null ? "" : existingQuery);

            // append each parm to the query
            for (final Entry<String, String> parm : queryMap.entrySet()) {
                newQuery.append("&").append(parm.getKey()).append("=").append(parm.getValue());
            }
            // create a uri with the new query.
            uri = new URI(
                    startingURI.getRawSchemeSpecificPart(),
                    startingURI.getRawUserInfo(),
                    startingURI.getHost(),
                    startingURI.getPort(),
                    startingURI.getRawPath(),
                    newQuery.toString(),
                    startingURI.getRawFragment());
        } else {
            uri = startingURI;
        }

        final BasicClassicHttpRequest httpRequest = new BasicClassicHttpRequest(request.get(METHOD).toString(), uri);

        if (request.containsKey(PROTOCOL_VERSION)) {
            httpRequest.setVersion((ProtocolVersion) request.get(PROTOCOL_VERSION));
        }

        // call addHeader for each header in headers.
        @SuppressWarnings("unchecked")
        final Map<String, String> headersMap = (Map<String, String>) request.get(HEADERS);
        if (headersMap != null) {
            for (final Entry<String, String> header : headersMap.entrySet()) {
                httpRequest.addHeader(header.getKey(), header.getValue());
            }
        }

        // call setEntity if a body is specified.
        final String requestBody = (String) request.get(BODY);
        if (requestBody != null) {
            final String requestContentType = (String) request.get(CONTENT_TYPE);
            final StringEntity entity = requestContentType != null ?
                                          new StringEntity(requestBody, ContentType.parse(requestContentType)) :
                                          new StringEntity(requestBody);
            httpRequest.setEntity(entity);
        }

        client.start(null);

        // Now start the request.
        final HttpHost host = new HttpHost(uri.getHost(), uri.getPort());
        final HttpCoreContext context = HttpCoreContext.create();
        try (final ClassicHttpResponse response = client.execute(host, httpRequest, context)) {
            // Prepare the response.  It will contain status, body, headers, and contentType.
            final HttpEntity entity = response.getEntity();
            final String body = entity == null ? null : EntityUtils.toString(entity);
            final String contentType = entity == null ? null : entity.getContentType();

            // prepare the returned information
            final Map<String, Object> ret = new HashMap<>();
            ret.put(STATUS, response.getCode());

            // convert the headers to a Map
            final Map<String, Object> headerMap = new HashMap<>();
            for (final Header header : response.getHeaders()) {
                headerMap.put(header.getName(), header.getValue());
            }
            ret.put(HEADERS, headerMap);
            ret.put(BODY, body);
            ret.put(CONTENT_TYPE, contentType);

            return ret ;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getClientName() {
        return "ClassicTestClient";
    }
}

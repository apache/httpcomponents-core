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

import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.BODY;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.CONTENT_TYPE;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.HEADERS;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.METHOD;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.PROTOCOL_VERSION;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.QUERY;
import static org.apache.hc.core5.testing.framework.ClientPOJOAdapter.STATUS;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIBuilder;

public class TestingFrameworkRequestHandler implements HttpRequestHandler {
    protected Throwable thrown;
    protected Map<String, Object> requestExpectations;
    protected Map<String, Object> desiredResponse;

    /**
     * Sets the request expectations.
     *
     * @param requestExpectations the expected values of the request.
     * @throws TestingFrameworkException
     */
    @SuppressWarnings("unchecked")
    public void setRequestExpectations(final Map<String, Object> requestExpectations) throws TestingFrameworkException {
        this.requestExpectations = (Map<String, Object>) TestingFramework.deepcopy(requestExpectations);
    }

    /**
     * Sets the desired response.  The handler will return a response that matches this.
     *
     * @param desiredResponse the desired response.
     * @throws TestingFrameworkException
     */
    @SuppressWarnings("unchecked")
    public void setDesiredResponse(final Map<String, Object> desiredResponse) throws TestingFrameworkException {
        this.desiredResponse = (Map<String, Object>) TestingFramework.deepcopy(desiredResponse);
    }

    /**
     * After the handler returns the response, any exception or failed assertion will be
     * in the member called "thrown".  A testing framework can later call this method
     * which will rethrow the exception that was thrown before.
     *
     * @throws TestingFrameworkException
     */
    public void assertNothingThrown() throws TestingFrameworkException {
        if (thrown != null) {
            final TestingFrameworkException e = (thrown instanceof TestingFrameworkException ?
                                                          (TestingFrameworkException) thrown :
                                                          new TestingFrameworkException(thrown));
            thrown = null;
            throw e;
        }
    }

    /**
     * <p>Checks the HTTP request against the requestExpectations that it was previously given.
     * If there is a mismatch, an exception will be saved in the "thrown" member.</p>
     *
     * <p>Also, a response will be returned that matches the desiredResponse.</p>
     */
    @Override
    public void handle(final ClassicHttpRequest request, final ClassicHttpResponse response, final HttpContext context)
            throws HttpException, IOException {

        try {
            /*
             * Check the method against the method in the requestExpectations.
             */
            final String actualMethod = request.getMethod();
            final String expectedMethod = (String) requestExpectations.get(METHOD);
            if (! actualMethod.equals(expectedMethod)) {
                throw new TestingFrameworkException("Method not expected. " +
                    " expected=" + expectedMethod + "; actual=" + actualMethod);
            }

            /*
             * Set the status to the status that is in the desiredResponse.
             */
            final Object desiredStatus = desiredResponse.get(STATUS);
            if (desiredStatus != null) {
                response.setCode((int) desiredStatus);
            }

            /*
             * Check the query parameters against the parameters in requestExpectations.
             */
            @SuppressWarnings("unchecked")
            final Map<String, String> expectedQuery = (Map<String, String>) requestExpectations.get(QUERY);
            if (expectedQuery != null) {
                final URI uri = request.getUri();
                final URIBuilder uriBuilder = new URIBuilder(uri, StandardCharsets.UTF_8);
                final List<NameValuePair> actualParams = uriBuilder.getQueryParams();
                final Map<String, String> actualParamsMap = new HashMap<>();
                for (final NameValuePair actualParam : actualParams) {
                    actualParamsMap.put(actualParam.getName(), actualParam.getValue());
                }
                for (final Map.Entry<String, String> expectedParam : expectedQuery.entrySet()) {
                    final String key = expectedParam.getKey();
                    if (! actualParamsMap.containsKey(key)) {
                        throw new TestingFrameworkException("Expected parameter not found: " + key);
                    }
                    final String actualParamValue = actualParamsMap.get(key);
                    final String expectedParamValue = expectedParam.getValue();
                    if (! actualParamValue.equals(expectedParamValue)) {
                        throw new TestingFrameworkException("Expected parameter value not found. " +
                            " Parameter=" + key + "; expected=" + expectedParamValue + "; actual=" + actualParamValue);
                    }
                }
            }

            /*
             * Check the headers against the headers in requestExpectations.
             */
            @SuppressWarnings("unchecked")
            final Map<String, String> expectedHeaders = (Map<String, String>) requestExpectations.get(HEADERS);
            if (expectedHeaders != null) {
                final Map<String, String> actualHeadersMap = new HashMap<>();
                final Header[] actualHeaders = request.getHeaders();
                for (final Header header : actualHeaders) {
                    actualHeadersMap.put(header.getName(), header.getValue());
                }
                for (final Entry<String, String> expectedHeader : expectedHeaders.entrySet()) {
                    final String key = expectedHeader.getKey();
                    if (! actualHeadersMap.containsKey(key)) {
                        throw new TestingFrameworkException("Expected header not found: " + key);
                    }
                    final String actualHeaderValue = actualHeadersMap.get(key);
                    final String expectedHeaderValue = expectedHeader.getValue();
                    if (! actualHeaderValue.equals(expectedHeaderValue)) {
                        throw new TestingFrameworkException("Expected header value not found. " +
                                " Name=" + key + "; expected=" + expectedHeaderValue + "; actual=" + actualHeaderValue);
                    }
                }
            }

            /*
             * Check the body.
             */
            final String expectedBody = (String) requestExpectations.get(BODY);
            if (expectedBody != null) {
                final HttpEntity entity = request.getEntity();
                final String data = EntityUtils.toString(entity);
                if (! data.equals(expectedBody)) {
                    throw new TestingFrameworkException("Expected body not found. " +
                            " Body=" + data + "; expected=" + expectedBody);
                }
            }

            /*
             * Check the contentType of the request.
             */
            final String requestContentType = (String) requestExpectations.get(CONTENT_TYPE);
            if (requestContentType != null) {
                final HttpEntity entity = request.getEntity();
                final String contentType = entity.getContentType();
                final String expectedContentType = (String) requestExpectations.get(CONTENT_TYPE);
                if (! contentType.equals(expectedContentType)) {
                    throw new TestingFrameworkException("Expected request content type not found. " +
                            " Content Type=" + contentType + "; expected=" + expectedContentType);
                }
            }

            /*
             * Check the protocolVersion.
             */
            if (requestExpectations.containsKey(PROTOCOL_VERSION)) {
                final ProtocolVersion protocolVersion = request.getVersion();
                final ProtocolVersion expectedProtocolVersion = (ProtocolVersion) requestExpectations.get(PROTOCOL_VERSION);
                if (! protocolVersion.equals(expectedProtocolVersion)) {
                    throw new TestingFrameworkException("Expected request protocol version not found. " +
                            " Protocol Version=" + protocolVersion + "; expected=" + expectedProtocolVersion);
                }
            }

            /*
             * Return the body in desiredResponse using the contentType in desiredResponse.
             */
            final String desiredBody = (String) desiredResponse.get(BODY);
            if (desiredBody != null) {
                final String desiredContentType = (String) desiredResponse.get(CONTENT_TYPE);
                final StringEntity entity = desiredContentType != null ?
                                new StringEntity(desiredBody, ContentType.parse(desiredContentType)) :
                                new StringEntity(desiredBody);
                response.setEntity(entity);
            }

            /*
             * Return the headers in desiredResponse.
             */
            @SuppressWarnings("unchecked")
            final Map<String, String> desiredHeaders = (Map<String, String>) desiredResponse.get(HEADERS);
            if (desiredHeaders != null) {
                for (final Entry<String, String> entry : desiredHeaders.entrySet()) {
                    response.setHeader(entry.getKey(), entry.getValue());
                }
            }

        } catch (final Throwable t) {
            /*
             * Save the throwable to be later retrieved by a call to assertNothingThrown().
             */
            thrown = t;
        }
    }
}

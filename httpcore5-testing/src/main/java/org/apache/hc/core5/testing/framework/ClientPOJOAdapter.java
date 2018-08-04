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

import java.util.Map;

/**
 *
 * <p>This adapter expects a request to be made up of POJOs such as Maps and Lists.  In Groovy
 * the request could be expressed like this:</p>
 *
 * <pre>
 *
 * def request = [
 *                   path    : "a/path",
 *                   method  : "GET",
 *                   query   : [
 *                                parm1 : "1",
 *                                parm2 : "2",
 *                             ]
 *                   headers : [
 *                                header1 : "stuff",
 *                                header2 : "more_stuff",
 *                             ]
 *                   contentType : "application/json",
 *                   body        : '{"location" : "home" }',
 *                ]
 * </pre>
 *
 * <p>The adapter will translate this request into calls specific to a particular HTTP client.</p>
 *
 * <p>The response is then returned with POJOs with this structure:</p>
 *
 * <pre>
 *
 * def response = [
 *                    status      : 200,
 *                    headers     : [
 *                                      header1 : "response_stuff",
 *                                  ]
 *                    contentType : "application/json",
 *                    body        : '{"location" : "work" }',
 *                ]
 * </pre>
 * @since 5.0
 */
public abstract class ClientPOJOAdapter {
    public static final String BODY = "body";
    public static final String CONTENT_TYPE = "contentType";
    public static final String HEADERS = "headers";
    public static final String METHOD = "method";
    public static final String NAME = "name";
    public static final String PATH = "path";
    public static final String PROTOCOL_VERSION = "protocolVersion";
    public static final String QUERY = "query";
    public static final String REQUEST = "request";
    public static final String RESPONSE = "response";
    public static final String STATUS = "status";
    public static final String TIMEOUT = "timeout";

    /**
     * Name of the HTTP Client that this adapter uses.
     *
     * @return name of the HTTP Client.
     */
    public abstract String getClientName();

    /**
     * Execute an HTTP request.
     *
     * @param defaultURI   the URI used by default.  The path in the request is
     *                     usually appended to it.
     * @param request      the request as specified above.
     *
     * @return the response to the request as specified above.
     *
     * @throws Exception in case of a problem
     */
    public abstract Map<String, Object> execute(String defaultURI, Map<String, Object> request) throws Exception;

    /**
     * <p>Check if a request is supported.</p>
     *
     * <p>Usually called directly by a testing framework.  If an HTTP client does not support
     * a particular request, a non-null reason should be returned.  Otherwise, if
     * the request is supported, return null.</p>
     *
     * <p>If this method is overridden, then the execute method should probably call
     * assertRequestSupported() at the beginning.</p>
     *
     * @param request the request as specified above.
     *
     * @return null if the request is supported;  Otherwise, return a reason.
     */
    public String checkRequestSupport(final Map<String, Object> request) {
        return null;
    }

    /**
     * <p>Assert that the request is supported</p>
     *
     * <p>Usually called by the execute method.  Throws an exception if the request
     * is not supported.</p>
     *
     * @param request the request as specified above.
     * @throws Exception if the request is not supported.
     */
    public void assertRequestSupported(final Map<String, Object> request) throws Exception {
        final String reason = checkRequestSupport(request);
        if (reason != null) {
            throw new Exception(reason);
        }
    }

    /**
     * <p>Modify the request.</p>
     *
     * <p>In a testing context, a testing framework can call this method to allow
     * the adapter to change the request.  The request is then given to a
     * special request handler of the in-process HttpServer which will later check
     * an actual HTTP request against what is expected.</p>
     *
     * <p>In a production context, this is called by the execute method (if at all).</p>
     *
     * @param request the request as specified above.
     * @return the same request or a modification of it.
     */
    public Map<String, Object> modifyRequest(final Map<String, Object> request) {
        return request;
    }
}

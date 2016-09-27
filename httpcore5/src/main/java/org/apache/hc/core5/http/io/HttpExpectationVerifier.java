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

package org.apache.hc.core5.http.io;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Defines an interface to verify whether an incoming HTTP request meets
 * the target server's expectations.
 *
 * @since 4.0
 */
public interface HttpExpectationVerifier {

    /**
     * Verifies whether the given request meets the server's expectations.
     * <p>
     * If the request fails to meet particular criteria, this method can
     * trigger a terminal response back to the client by setting the status
     * code of the response object to a value greater or equal to
     * {@code 200}. In this case the client will not have to transmit
     * the request body. If the request meets expectations this method can
     * terminate without modifying the response object. Per default the status
     * code of the response object will be set to {@code 100}.
     *
     * @param request the HTTP request.
     * @param response the HTTP response.
     * @param context the HTTP context.
     * @throws HttpException in case of an HTTP protocol violation.
     */
    void verify(ClassicHttpRequest request, ClassicHttpResponse response, HttpContext context)
            throws HttpException;

}

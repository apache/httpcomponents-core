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
package org.apache.hc.core5.http2.nio.support;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.util.Args;

/**
 * Exception indicating CONNECT tunnel refusal by the proxy.
 *
 * @since 5.5
 */
public final class TunnelRefusedException extends HttpException {

    private static final long serialVersionUID = 1L;

    private final HttpResponse response;

    public TunnelRefusedException(final HttpResponse response) {
        super("Tunnel refused: " + new StatusLine(Args.notNull(response, "Response")));
        this.response = copy(response);
    }

    public HttpResponse getResponse() {
        return response;
    }

    public int getStatusCode() {
        return response.getCode();
    }

    private static HttpResponse copy(final HttpResponse response) {
        final BasicHttpResponse copy = new BasicHttpResponse(response.getCode(), response.getReasonPhrase());
        copy.setVersion(response.getVersion());
        for (final Header header : response.getHeaders()) {
            copy.addHeader(header);
        }
        return copy;
    }
}

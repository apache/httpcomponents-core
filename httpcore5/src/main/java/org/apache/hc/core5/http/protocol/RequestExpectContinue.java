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

import java.io.IOException;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.util.Args;
/**
 * This request interceptor is responsible for activation of the 'expect-continue'
 * handshake by adding a {@code Expect} header describing client expectations.
 * Additionally, it provides an option to respond with a 417 (Expectation Failed)
 * status for unexpected {@code Expect} headers.
 * <p>
 * This interceptor is recommended for HTTP protocol conformance and
 * the correct operation of the client-side message processing pipeline.
 * </p>
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class RequestExpectContinue implements HttpRequestInterceptor {


    /**
     * Flag to control if a 417 (Expectation Failed) should be returned for unexpected Expect header.
     *
     * @since 5.3
     */
    private final boolean shouldRespond417;

    /**
     * Creates an instance with custom behavior for "Expect" header.
     *
     * @param shouldRespond417 if true, will respond with 417 for unexpected "Expect" headers.
     * @since 5.3
     */
    public RequestExpectContinue(final boolean shouldRespond417) {
        this.shouldRespond417 = shouldRespond417;
    }

    /**
     * Singleton instance.
     *
     * @since 5.2
     */
    public static final RequestExpectContinue INSTANCE = new RequestExpectContinue();

    /**
     * Creates a default instance which will not respond with a 417 status for unexpected "Expect" headers.
     * <p>
     * The flag {@code shouldRespond417} is set to false, meaning that this instance will not throw a ProtocolException
     * resulting in a 417 (Expectation Failed) status code when encountering an "Expect" header value other than "100-continue".
     * </p>
     *
     */
    public RequestExpectContinue() {
        this(false);
    }

    @Override
    public void process(final HttpRequest request, final EntityDetails entity, final HttpContext context)
            throws HttpException, IOException {
        Args.notNull(request, "HTTP request");

        final Header expectHeader = request.getFirstHeader(HttpHeaders.EXPECT);

        if (expectHeader != null) {
            final String expectValue = expectHeader.getValue();
            if (!HeaderElements.CONTINUE.equalsIgnoreCase(expectValue) && shouldRespond417) {
                throw new ProtocolException("417 Expectation Failed");
            }
        } else {
            if (entity != null) {
                final ProtocolVersion ver = context.getProtocolVersion();
                if (entity.getContentLength() != 0 && !ver.lessEquals(HttpVersion.HTTP_1_0)) {
                    request.addHeader(HttpHeaders.EXPECT, HeaderElements.CONTINUE);
                }
            }
        }
    }
}

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
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.util.Args;

/**
 * This response interceptor is responsible for adding {@code Date} header
 * to outgoing response messages.
 * <p>
 * This interceptor is recommended for the HTTP protocol conformance and
 * the correct operation of the server-side message processing pipeline.
 * </p>
 * <p>
 * If the {@code Date} header is missing or considered invalid, and the
 * {@code alwaysReplace} flag is set to {@code true}, the interceptor will replace it
 * with the current system date and time.
 * </p>
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
public class ResponseDate implements HttpResponseInterceptor {

    /**
     * Indicates whether to always replace an invalid or missing {@code Date} header.
     *
     * @since 5.4
     */
    private final boolean alwaysReplace;

    public static final ResponseDate INSTANCE = new ResponseDate();

    public ResponseDate() {
        this(false);
    }

    /**
     * Constructs a ResponseDate interceptor.
     *
     * @param alwaysReplace Whether to replace an invalid {@code Date} header.
     *                           If {@code true}, the interceptor will replace any
     *                           detected invalid {@code Date} header with a valid value.
     * @since 5.4
     */
    public ResponseDate(final boolean alwaysReplace) {
        super();
        this.alwaysReplace = alwaysReplace;
    }

    @Override
    public void process(final HttpResponse response, final EntityDetails entity, final HttpContext context)
            throws HttpException, IOException {
        Args.notNull(response, "HTTP response");
        if (alwaysReplace || response.getFirstHeader(HttpHeaders.DATE) == null) {
            response.setHeader(HttpHeaders.DATE, HttpDateGenerator.INSTANCE.getCurrentDate());
        }
    }
}

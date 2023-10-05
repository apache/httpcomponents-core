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
package org.apache.hc.core5.http.support;

import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.util.TextUtils;

/**
 * Server side Expect header handling support.
 *
 * @since 5.3
 */
@Internal
public class ExpectSupport {

    public static Expectation parse(
            final HttpRequest request,
            final EntityDetails entityDetails) throws ProtocolException {
        // Ignore Expect header in messages older than HTTP/1.1
        if (request.getVersion() != null && request.getVersion().lessEquals(HttpVersion.HTTP_1_0)) {
            return null;
        }
        final AtomicReference<Expectation> expectationRef = new AtomicReference<>();
        MessageSupport.parseTokens(request, HttpHeaders.EXPECT, t -> {
            if (t.equalsIgnoreCase(HeaderElements.CONTINUE)) {
                expectationRef.compareAndSet(null, Expectation.CONTINUE);
            } else if (!TextUtils.isBlank(t)) {
                expectationRef.set(Expectation.UNKNOWN);
            }
        });
        final Expectation expectation = expectationRef.get();
        if (expectation == Expectation.CONTINUE && entityDetails == null) {
            throw new ProtocolException("Expect-Continue request without an enclosed entity");
        }
        return expectation;
    }
}


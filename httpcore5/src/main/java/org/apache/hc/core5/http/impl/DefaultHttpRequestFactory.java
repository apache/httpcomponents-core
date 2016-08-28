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

package org.apache.hc.core5.http.impl;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpRequestFactory;
import org.apache.hc.core5.http.MethodNotSupportedException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;

/**
 * Default factory for creating {@link ClassicHttpRequest} objects.
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class DefaultHttpRequestFactory implements HttpRequestFactory<ClassicHttpRequest> {

    public static final DefaultHttpRequestFactory INSTANCE = new DefaultHttpRequestFactory();

    private static final String[] SUPPORTED_METHODS = {
            "GET",
            "POST",
            "PUT",
            "HEAD",
            "OPTIONS",
            "DELETE",
            "TRACE",
            "PATCH",
            "CONNECT"
    };

    private static boolean isOneOf(final String[] methods, final String method) {
        for (final String method2 : methods) {
            if (method2.equalsIgnoreCase(method)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ClassicHttpRequest newHttpRequest(final ProtocolVersion transportVersion, final String method, final String uri) throws MethodNotSupportedException {
        if (isOneOf(SUPPORTED_METHODS, method)) {
            final ClassicHttpRequest request = new BasicClassicHttpRequest(method, uri);
            request.setVersion(transportVersion);
            return request;
        }
        throw new MethodNotSupportedException(method + " method not supported");
    }

    @Override
    public ClassicHttpRequest newHttpRequest(final String method, final String uri) throws MethodNotSupportedException {
        return new BasicClassicHttpRequest(method, uri);
    }

}

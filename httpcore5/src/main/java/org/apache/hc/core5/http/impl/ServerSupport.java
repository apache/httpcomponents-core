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

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.MethodNotSupportedException;
import org.apache.hc.core5.http.MisdirectedRequestException;
import org.apache.hc.core5.http.NotImplementedException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.RequestHeaderFieldsTooLargeException;
import org.apache.hc.core5.http.UnsupportedHttpVersionException;

/**
 * HTTP Server support methods.
 *
 * @since 5.0
 */
@Internal
public class ServerSupport {

    public static void validateResponse(
            final HttpResponse response,
            final EntityDetails responseEntityDetails) throws HttpException {
        final int status = response.getCode();
        switch (status) {
            case HttpStatus.SC_NO_CONTENT:
            case HttpStatus.SC_NOT_MODIFIED:
                if (responseEntityDetails != null) {
                    throw new HttpException("Response " + status + " must not enclose an entity");
                }
        }
    }

    public static String toErrorMessage(final Exception ex) {
        final String message = ex.getMessage();
        return message != null ? message : ex.toString();
    }

    public static int toStatusCode(final Exception ex) {
        final int code;
        if (ex instanceof MethodNotSupportedException) {
            code = HttpStatus.SC_NOT_IMPLEMENTED;
        } else if (ex instanceof UnsupportedHttpVersionException) {
            code = HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED;
        } else if (ex instanceof NotImplementedException) {
            code = HttpStatus.SC_NOT_IMPLEMENTED;
        } else if (ex instanceof RequestHeaderFieldsTooLargeException) {
            code = HttpStatus.SC_REQUEST_HEADER_FIELDS_TOO_LARGE;
        } else if (ex instanceof MisdirectedRequestException) {
            code = HttpStatus.SC_MISDIRECTED_REQUEST;
        } else if (ex instanceof ProtocolException) {
            code = HttpStatus.SC_BAD_REQUEST;
        } else {
            code = HttpStatus.SC_INTERNAL_SERVER_ERROR;
        }
        return code;
    }

}


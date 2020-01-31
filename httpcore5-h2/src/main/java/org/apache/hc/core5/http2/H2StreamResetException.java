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
package org.apache.hc.core5.http2;

import java.util.Objects;

import org.apache.hc.core5.http.HttpStreamResetException;

/**
 * Signals HTTP/2 protocol error that renders the actual HTTP/2 data stream
 * unreliable.
 *
 * @since 5.0
 */
public class H2StreamResetException extends HttpStreamResetException {

    private final int code;

    public H2StreamResetException(final H2Error error, final String message) {
        super(message);
        Objects.requireNonNull(error, "H2 Error code may not be null");
        this.code = error.getCode();
    }

    public H2StreamResetException(final int code, final String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

}

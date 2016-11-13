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

package org.apache.hc.core5.http.message;

import java.io.Serializable;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.util.Args;

/**
 * HTTP/1.1 status line.
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class StatusLine implements Serializable {

    private static final long serialVersionUID = -2443303766890459269L;

    /** The protocol version. */
    private final ProtocolVersion protoVersion;

    /** The status code. */
    private final int statusCode;

    /** The reason phrase. */
    private final String reasonPhrase;

    public StatusLine(final HttpResponse response) {
        super();
        Args.notNull(response, "Response");
        this.protoVersion = response.getVersion() != null ? response.getVersion() : HttpVersion.HTTP_1_1;
        this.statusCode = response.getCode();
        this.reasonPhrase = response.getReasonPhrase();
    }
    /**
     * Creates a new status line with the given version, status, and reason.
     *
     * @param version           the protocol version of the response
     * @param statusCode        the status code of the response
     * @param reasonPhrase      the reason phrase to the status code, or
     *                          {@code null}
     */
    public StatusLine(final ProtocolVersion version, final int statusCode,
                      final String reasonPhrase) {
        super();
        this.statusCode = Args.notNegative(statusCode, "Status code");
        this.protoVersion = version != null ? version : HttpVersion.HTTP_1_1;
        this.reasonPhrase = reasonPhrase;
    }

    public int getStatusCode() {
        return this.statusCode;
    }

    public ProtocolVersion getProtocolVersion() {
        return this.protoVersion;
    }

    public String getReasonPhrase() {
        return this.reasonPhrase;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append(this.protoVersion).append(" ").append(this.statusCode).append(" ");
        if (this.reasonPhrase != null) {
            buf.append(this.reasonPhrase);
        }
        return buf.toString();
    }

}

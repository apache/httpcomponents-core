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

package org.apache.hc.core5.http.impl.io;

import java.io.IOException;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.message.LineFormatter;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * HTTP response writer that serializes its output to an instance of
 * {@link org.apache.hc.core5.http.io.SessionOutputBuffer}.
 *
 * @since 4.3
 */
public class DefaultHttpResponseWriter extends AbstractMessageWriter<ClassicHttpResponse> {

    private final Http1Config http1Config;

    /**
     * @since 5.3
     */
    public DefaultHttpResponseWriter(final Http1Config http1Config, final LineFormatter formatter) {
        super(formatter);
        this.http1Config = http1Config != null ? http1Config : Http1Config.DEFAULT;
    }

    public DefaultHttpResponseWriter(final LineFormatter formatter) {
        this(null, formatter);
    }

    public DefaultHttpResponseWriter() {
        this(null, null);
    }

    /**
     * Determines the HTTP protocol version to be communicated to the opposite
     * endpoint in the message header.
     *
     * @since 5.3
     */
    protected HttpVersion protocolVersion(final HttpResponse message) {
        return http1Config.getVersion();
    }

    @Override
    protected void writeHeadLine(
            final ClassicHttpResponse message, final CharArrayBuffer lineBuf) throws IOException {
        getLineFormatter().formatStatusLine(lineBuf, new StatusLine(
                protocolVersion(message),
                message.getCode(),
                message.getReasonPhrase()));
    }

}

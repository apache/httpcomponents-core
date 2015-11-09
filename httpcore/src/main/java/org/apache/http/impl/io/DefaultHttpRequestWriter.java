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

package org.apache.http.impl.io;

import static org.apache.http.util.TextUtils.join;

import java.io.IOException;
import java.util.Set;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.io.SessionOutputBuffer;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.LineFormatter;
import org.apache.http.util.CharArrayBuffer;

/**
 * HTTP request writer that serializes its output to an instance of
 * {@link org.apache.http.io.SessionOutputBuffer}.
 *
 * @since 4.3
 */
@NotThreadSafe
public class DefaultHttpRequestWriter extends AbstractMessageWriter<HttpRequest> {

    /**
     * Creates an instance of DefaultHttpRequestWriter.
     *
     * @param formatter the line formatter If {@code null}
     *   {@link org.apache.http.message.BasicLineFormatter#INSTANCE}
     *   will be used.
     */
    public DefaultHttpRequestWriter(final LineFormatter formatter) {
        super(formatter);
    }

    public DefaultHttpRequestWriter() {
        this(null);
    }

    @Override
    protected void writeHeadLine(
            final HttpRequest message, final CharArrayBuffer lineBuf) throws IOException {
        getLineFormatter().formatRequestLine(lineBuf, message.getRequestLine());
    }

    protected void addTrailerHeader(final SessionOutputBuffer buffer,
                                    final HttpRequest message)
            throws IOException {
        final HttpEntity entity = message.getEntity();
        if (entity == null) {
            return;
        }
        final Set<String> trailerNames = entity.getExpectedTrailerNames();
        if (trailerNames.isEmpty()) {
            return;
        }
        this.lineBuf.clear();
        lineFormatter.formatHeader(this.lineBuf,
                new BasicHeader(HttpHeaders.TRAILER, join(", ", trailerNames)));
        buffer.writeLine(this.lineBuf);
    }
}

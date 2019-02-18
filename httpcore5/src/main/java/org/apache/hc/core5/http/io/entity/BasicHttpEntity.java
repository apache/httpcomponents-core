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

package org.apache.hc.core5.http.io.entity;

import java.io.IOException;
import java.io.InputStream;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.impl.io.EmptyInputStream;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.util.Args;

/**
 * A generic streamed, non-repeatable entity that obtains its content from an {@link InputStream}.
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class BasicHttpEntity extends AbstractHttpEntity {

    private final InputStream content;
    private final long length;

    public BasicHttpEntity(
            final InputStream content, final long length, final ContentType contentType, final String contentEncoding,
            final boolean chunked) {
        super(contentType, contentEncoding, chunked);
        this.content = Args.notNull(content, "Content stream");
        this.length = length;
    }

    public BasicHttpEntity(
            final InputStream content, final long length, final ContentType contentType, final String contentEncoding) {
        this(content, length, contentType, contentEncoding, false);
    }

    public BasicHttpEntity(final InputStream content, final long length, final ContentType contentType) {
        this(content, length, contentType, null);
    }

    public BasicHttpEntity(final InputStream content, final ContentType contentType, final String contentEncoding) {
        this(content, -1, contentType, contentEncoding);
    }

    public BasicHttpEntity(final InputStream content, final ContentType contentType) {
        this(content, -1, contentType, null);
    }

    public BasicHttpEntity(final InputStream content, final ContentType contentType, final boolean chunked) {
        this(content, -1, contentType, null, chunked);
    }

    @Override
    public final long getContentLength() {
        return this.length;
    }

    @Override
    public final InputStream getContent() throws IllegalStateException {
        return this.content;
    }

    @Override
    public final boolean isRepeatable() {
        return false;
    }

    @Override
    public final boolean isStreaming() {
        return this.content != null && this.content != EmptyInputStream.INSTANCE;
    }

    @Override
    public final void close() throws IOException {
        Closer.close(content);
    }

}

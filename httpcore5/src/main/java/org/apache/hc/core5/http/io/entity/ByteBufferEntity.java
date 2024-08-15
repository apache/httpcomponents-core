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
import java.nio.ByteBuffer;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.util.Args;

/**
 * An entity that delivers the contents of a {@link ByteBuffer}.
 */
public class ByteBufferEntity extends AbstractHttpEntity {

    private final ByteBuffer buffer;
    private final long length;

    /**
     * Constructs a new instance with the given attributes kept as immutable.
     * <p>
     * The new instance:
     * </p>
     * <ul>
     * <li>is not chunked.</li>
     * </ul>
     *
     * @param buffer          The message body contents as a byte buffer.
     * @param contentType     The content-type, may be null.
     * @param contentEncoding The content encoding string, may be null.
     */
    public ByteBufferEntity(final ByteBuffer buffer, final ContentType contentType, final String contentEncoding) {
        super(contentType, contentEncoding);
        Args.notNull(buffer, "Source byte buffer");
        this.buffer = buffer;
        this.length = buffer.remaining();
    }

    /**
     * Constructs a new instance with the given attributes kept as immutable.
     * <p>
     * The new instance:
     * </p>
     * <ul>
     * <li>is not chunked.</li>
     * <li>does not define a content encoding.</li>
     * </ul>
     *
     * @param buffer          The message body contents as a byte buffer.
     * @param contentType     The content-type, may be null.
     */
    public ByteBufferEntity(final ByteBuffer buffer, final ContentType contentType) {
        this(buffer, contentType, null);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns {@code true}.
     * </p>
     */
    @Override
    public final boolean isRepeatable() {
        return false;
    }

    @Override
    public final long getContentLength() {
        return length;
    }

    @Override
    public final InputStream getContent() throws IOException, UnsupportedOperationException {
        return new InputStream() {

            @Override
            public int read() throws IOException {
                if (!buffer.hasRemaining()) {
                    return -1;
                }
                return buffer.get() & 0xFF;
            }

            @Override
            public int read(final byte[] bytes, final int off, final int len) throws IOException {
                if (len == 0) {
                    return 0;
                }
                if (!buffer.hasRemaining()) {
                    return -1;
                }
                final int chunk = Math.min(len, buffer.remaining());
                buffer.get(bytes, off, chunk);
                return chunk;
            }
        };
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns {@code false}.
     * </p>
     */
    @Override
    public final boolean isStreaming() {
        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is a no-op.
     * </p>
     */
    @Override
    public final void close() throws IOException {
    }

}

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
import java.io.OutputStream;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.util.Args;

/**
 * A streamed, non-repeatable entity that obtains its content from an {@link InputStream}.
 *
 * @since 4.0
 */
public class InputStreamEntity extends AbstractHttpEntity {

    private final InputStream content;
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
     * @param content         The message body contents as an InputStream.
     * @param contentLength   The value for the {@code Content-Length} header for the size of the message body, in bytes.
     * @param contentType     The content-type, may be null.
     * @param contentEncoding The content encoding string, may be null.
     */
    public InputStreamEntity(
            final InputStream content, final long contentLength, final ContentType contentType, final String contentEncoding) {
        super(contentType, contentEncoding);
        this.content = Args.notNull(content, "Source input stream");
        this.length = contentLength;
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
     * @param content         The message body contents as an InputStream.
     * @param contentLength   The value for the {@code Content-Length} header for the size of the message body, in bytes.
     * @param contentType     The content-type, may be null.
     */
    public InputStreamEntity(final InputStream content, final long contentLength, final ContentType contentType) {
        this(content, contentLength, contentType, null);
    }

    /**
     * Constructs a new instance with the given attributes kept as immutable.
     * <p>
     * The new instance:
     * </p>
     * <ul>
     * <li>is not chunked.</li>
     * <li>does not define a content length.</li>
     * <li>does not define a content encoding.</li>
     * </ul>
     *
     * @param content         The message body contents as an InputStream.
     * @param contentType     The content-type, may be null.
     */
    public InputStreamEntity(final InputStream content, final ContentType contentType) {
        this(content, -1, contentType, null);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns {@code false}.
     * </p>
     */
    @Override
    public final boolean isRepeatable() {
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @return the content length or {@code -1} if unknown.
     */
    @Override
    public final long getContentLength() {
        return this.length;
    }

    @Override
    public final InputStream getContent() throws IOException {
        return this.content;
    }

    /**
     * Writes bytes from the {@code InputStream} this entity was constructed
     * with to an {@code OutputStream}.  The content length
     * determines how many bytes are written.  If the length is unknown ({@code -1}), the
     * stream will be completely consumed (to the end of the stream).
     *
     */
    @Override
    public final void writeTo(final OutputStream outStream) throws IOException {
        Args.notNull(outStream, "Output stream");
        try (final InputStream inStream = this.content) {
            final byte[] buffer = new byte[OUTPUT_BUFFER_SIZE];
            int readLen;
            if (this.length < 0) {
                // consume until EOF
                while ((readLen = inStream.read(buffer)) != -1) {
                    outStream.write(buffer, 0, readLen);
                }
            } else {
                // consume no more than length
                long remaining = this.length;
                while (remaining > 0) {
                    readLen = inStream.read(buffer, 0, (int) Math.min(OUTPUT_BUFFER_SIZE, remaining));
                    if (readLen == -1) {
                        break;
                    }
                    outStream.write(buffer, 0, readLen);
                    remaining -= readLen;
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns {@code true}.
     * </p>
     */
    @Override
    public final boolean isStreaming() {
        return true;
    }

    @Override
    public final void close() throws IOException {
        content.close();
    }

}

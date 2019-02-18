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

    public InputStreamEntity(
            final InputStream inStream, final long length, final ContentType contentType, final String contentEncoding) {
        super(contentType, contentEncoding);
        this.content = Args.notNull(inStream, "Source input stream");
        this.length = length;
    }

    public InputStreamEntity(final InputStream inStream, final long length, final ContentType contentType) {
        this(inStream, length, contentType, null);
    }

    public InputStreamEntity(final InputStream inStream, final ContentType contentType) {
        this(inStream, -1, contentType, null);
    }

    @Override
    public final boolean isRepeatable() {
        return false;
    }

    /**
     * @return the content length or {@code -1} if unknown
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

    @Override
    public final boolean isStreaming() {
        return true;
    }

    @Override
    public final void close() throws IOException {
        content.close();
    }

}

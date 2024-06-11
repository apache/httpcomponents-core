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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.io.IOCallback;
import org.apache.hc.core5.util.Args;

/**
 * Entity that delegates the process of content generation to a {@link IOCallback}
 * with {@link OutputStream} as output sink.
 * <p>
 * This class contains {@link ThreadingBehavior#IMMUTABLE_CONDITIONAL immutable attributes} but subclasses may contain
 * additional immutable or mutable attributes.
 * </p>
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public final class EntityTemplate extends AbstractHttpEntity {

    private final long contentLength;
    private final IOCallback<OutputStream> callback;

    /**
     * Constructs a new instance with the given attributes kept as immutable.
     * <p>
     * The new instance:
     * </p>
     * <ul>
     * <li>is not chunked.</li>
     * </ul>
     *
     * @param contentLength   The value for the {@code Content-Length} header for the size of the message body, in bytes.
     * @param contentType     The content-type, may be null.
     * @param contentEncoding The content encoding string, may be null.
     * @param callback        A consumer that write the message body to an output stream.
     */
    public EntityTemplate(
            final long contentLength, final ContentType contentType, final String contentEncoding,
            final IOCallback<OutputStream> callback) {
        super(contentType, contentEncoding);
        this.contentLength = contentLength;
        this.callback = Args.notNull(callback, "I/O callback");
    }

    @Override
    public long getContentLength() {
        return contentLength;
    }

    @Override
    public InputStream getContent() throws IOException {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        writeTo(buf);
        return new ByteArrayInputStream(buf.toByteArray());
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns {@code true}.
     * </p>
     */
    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Override
    public void writeTo(final OutputStream outStream) throws IOException {
        Args.notNull(outStream, "Output stream");
        this.callback.execute(outStream);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns {@code false}.
     * </p>
     */
    @Override
    public boolean isStreaming() {
        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is a no-op.
     * </p>
     */
    @Override
    public void close() throws IOException {
    }

}

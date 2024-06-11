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
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.util.Args;

/**
 * A generic streamed, non-repeatable entity that obtains its content from an {@link InputStream}.
 * <p>
 * This class contains {@link ThreadingBehavior#IMMUTABLE_CONDITIONAL immutable attributes} but subclasses may contain
 * additional immutable or mutable attributes.
 * </p>
 *
 * @see ThreadingBehavior#IMMUTABLE_CONDITIONAL
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class BasicHttpEntity extends AbstractHttpEntity {

    private final InputStream content;
    private final long length;

    /**
     * Constructs a new instance with the given attributes kept as immutable.
     *
     * @param content         The message body contents as an InputStream.
     * @param contentLength   The value for the {@code Content-Length} header for the size of the message body, in bytes.
     * @param contentType     The content-type, may be null.
     * @param contentEncoding The content encoding string, may be null.
     * @param chunked         Whether this entity should be chunked.
     */
    public BasicHttpEntity(
            final InputStream content, final long contentLength, final ContentType contentType, final String contentEncoding,
            final boolean chunked) {
        super(contentType, contentEncoding, chunked);
        this.content = Args.notNull(content, "Content stream");
        this.length = contentLength;
    }

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
    public BasicHttpEntity(
            final InputStream content, final long contentLength, final ContentType contentType, final String contentEncoding) {
        this(content, contentLength, contentType, contentEncoding, false);
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
    public BasicHttpEntity(final InputStream content, final long contentLength, final ContentType contentType) {
        this(content, contentLength, contentType, null);
    }

    /**
     * Constructs a new instance with the given attributes kept as immutable.
     * <p>
     * The new instance:
     * </p>
     * <ul>
     * <li>is not chunked.</li>
     * <li>does not define a length.</li>
     * </ul>
     *
     * @param content         The message body contents as an InputStream.
     * @param contentType     The content-type, may be null.
     * @param contentEncoding The content encoding string, may be null.
     */
    public BasicHttpEntity(final InputStream content, final ContentType contentType, final String contentEncoding) {
        this(content, -1, contentType, contentEncoding);
    }

    /**
     * Constructs a new instance with the given attributes kept as immutable.
     * <p>
     * The new instance:
     * </p>
     * <ul>
     * <li>is not chunked.</li>
     * <li>does not define a length.</li>
     * <li>does not define a content encoding.</li>
     * </ul>
     *
     * @param content         The message body contents as an InputStream.
     * @param contentType     The content-type, may be null.
     */
    public BasicHttpEntity(final InputStream content, final ContentType contentType) {
        this(content, -1, contentType, null);
    }

    /**
     * Constructs a new instance with the given attributes kept as immutable.
     * <p>
     * The new instance:
     * </p>
     * <ul>
     * <li>does not define a length.</li>
     * <li>does not define a content encoding.</li>
     * </ul>
     *
     * @param content         The message body contents as an InputStream.
     * @param contentType     The content-type, may be null.
     * @param chunked         Whether this entity should be chunked.
     */
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
        Closer.close(content);
    }

}

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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Args;

/**
 * Abstract base class for mutable entities. Provides the commonly used attributes for streamed and
 * self-contained implementations.
 * <p>
 * This class contains immutable attributes but subclasses may contain additional immutable or mutable attributes.
 * </p>
 *
 * @since 4.0
 */
public abstract class AbstractHttpEntity implements HttpEntity {

    static final int OUTPUT_BUFFER_SIZE = 4096;

    private final String contentType;
    private final String contentEncoding;
    private final boolean chunked;

    /**
     * Constructs a new instance with the given attributes kept as immutable.
     *
     * @param contentType     The content-type string, may be null.
     * @param contentEncoding The content encoding string, may be null.
     * @param chunked         Whether this entity should be chunked.
     */
    protected AbstractHttpEntity(final String contentType, final String contentEncoding, final boolean chunked) {
        this.contentType = contentType;
        this.contentEncoding = contentEncoding;
        this.chunked = chunked;
    }

    /**
     * Constructs a new instance with the given attributes kept as immutable.
     *
     * @param contentType     The content-type, may be null.
     * @param contentEncoding The content encoding string, may be null.
     * @param chunked         Whether this entity should be chunked.
     */
    protected AbstractHttpEntity(final ContentType contentType, final String contentEncoding, final boolean chunked) {
        this.contentType = Objects.toString(contentType, null);
        this.contentEncoding = contentEncoding;
        this.chunked = chunked;
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
     * @param contentType     The content-type string, may be null.
     * @param contentEncoding The content encoding string, may be null.
     */
    protected AbstractHttpEntity(final String contentType, final String contentEncoding) {
        this(contentType, contentEncoding, false);
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
     * @param contentType     The content-type, may be null.
     * @param contentEncoding The content encoding string, may be null.
     */
    protected AbstractHttpEntity(final ContentType contentType, final String contentEncoding) {
        this(contentType, contentEncoding, false);
    }

    /**
     * Writes an entity to an OutputStream.
     *
     * @param entity    The entity to write, never null.
     * @param outStream Where to write the entity, never null.
     * @throws IOException                   if the entity cannot generate its content stream; also thrown if the output stream is closed.
     * @throws UnsupportedOperationException if entity content cannot be represented as {@link InputStream}.
     */
    public static void writeTo(final HttpEntity entity, final OutputStream outStream) throws IOException {
        Args.notNull(entity, "Entity");
        Args.notNull(outStream, "Output stream");
        try (final InputStream inStream = entity.getContent()) {
            if (inStream != null) {
                int count;
                final byte[] tmp = new byte[OUTPUT_BUFFER_SIZE];
                while ((count = inStream.read(tmp)) != -1) {
                    outStream.write(tmp, 0, count);
                }
            }
        }
    }

    /**
     * Writes this entity to an OutputStream.
     *
     * @param outStream Where to write the entity, never null.
     * @throws IOException                   if the entity cannot generate its content stream; also thrown if the output stream is closed.
     * @throws UnsupportedOperationException if entity content cannot be represented as {@link InputStream}.
     */
    @Override
    public void writeTo(final OutputStream outStream) throws IOException {
        writeTo(this, outStream);
    }

    @Override
    public final String getContentType() {
        return contentType;
    }

    @Override
    public final String getContentEncoding() {
        return contentEncoding;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns {@code false}.
     * </p>
     */
    @Override
    public final boolean isChunked() {
        return chunked;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns {@code false}.
     * </p>
     */
    @Override
    public boolean isRepeatable() {
        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns {@code null}.
     * </p>
     */
    @Override
    public Supplier<List<? extends Header>> getTrailers() {
        return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns an immutable empty set.
     * </p>
     */
    @Override
    public Set<String> getTrailerNames() {
        return Collections.emptySet();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("[Entity-Class: ");
        sb.append(getClass().getSimpleName());
        sb.append(", Content-Type: ");
        sb.append(contentType);
        sb.append(", Content-Encoding: ");
        sb.append(contentEncoding);
        sb.append(", chunked: ");
        sb.append(chunked);
        sb.append(']');
        return sb.toString();
    }

}

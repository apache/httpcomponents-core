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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.util.Args;

/**
 * A self contained, repeatable entity that obtains its content from a byte array.
 * <p>
 * This class contains {@link ThreadingBehavior#IMMUTABLE_CONDITIONAL immutable attributes} but subclasses may contain
 * additional immutable or mutable attributes.
 * </p>
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class ByteArrayEntity extends AbstractHttpEntity {

    private final byte[] buf;
    private final int off, len;

    /**
     * Constructs a new instance with the given attributes kept as immutable.
     *
     * @param buf             The message body contents as a byte array buffer.
     * @param off             The offset in the buffer of the first byte to read.
     * @param len             The maximum number of bytes to read from the buffer.
     * @param contentType     The content-type, may be null.
     * @param contentEncoding The content encoding string, may be null.
     * @param chunked         Whether this entity should be chunked.
     * @since 5.0
     */
    public ByteArrayEntity(
            final byte[] buf, final int off, final int len, final ContentType contentType, final String contentEncoding,
            final boolean chunked) {
        super(contentType, contentEncoding, chunked);
        Args.notNull(buf, "Source byte array");
        Args.notNegative(off, "offset");
        Args.notNegative(len, "length");
        Args.notNegative(off + len, "off + len");
        Args.check(off <= buf.length, "off %s cannot be greater then b.length %s ", off, buf.length);
        Args.check(off + len <= buf.length, "off + len  %s cannot be less then b.length %s ", off + len, buf.length);
        this.buf = buf;
        this.off = off;
        this.len = len;
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
     * @param buf             The message body contents as a byte array buffer.
     * @param off             The offset in the buffer of the first byte to read.
     * @param len             The maximum number of bytes to read from the buffer.
     * @param contentType     The content-type, may be null.
     * @param contentEncoding The content encoding string, may be null.
     * @since 5.0
     */
    public ByteArrayEntity(
            final byte[] buf, final int off, final int len, final ContentType contentType, final String contentEncoding) {
        this(buf, off, len, contentType, contentEncoding, false);
    }

    /**
     * Constructs a new instance with the given attributes kept as immutable.
     * <p>
     * The new instance:
     * </p>
     * <ul>
     * <li>starts reading it's contents at index 0 in the buffer.</li>
     * <li>has the content length of the given buffer.</li>
     * </ul>
     *
     * @param buf             The message body contents as a byte array buffer.
     * @param contentType     The content-type, may be null.
     * @param contentEncoding The content encoding string, may be null.
     * @param chunked         Whether this entity should be chunked.
     * @since 5.0
     */
    public ByteArrayEntity(
            final byte[] buf, final ContentType contentType, final String contentEncoding, final boolean chunked) {
        super(contentType, contentEncoding, chunked);
        Args.notNull(buf, "Source byte array");
        this.buf = buf;
        this.off = 0;
        this.len = this.buf.length;
    }

    /**
     * Constructs a new instance with the given attributes kept as immutable.
     * <p>
     * The new instance:
     * </p>
     * <ul>
     * <li>is not chunked.</li>
     * <li>starts reading it's contents at index 0 in the buffer.</li>
     * <li>has the content length of the given buffer.</li>
     * </ul>
     *
     * @param buf             The message body contents as a byte array buffer.
     * @param contentType     The content-type, may be null.
     * @param contentEncoding The content encoding string, may be null.
     * @since 5.0
     */
    public ByteArrayEntity(final byte[] buf, final ContentType contentType, final String contentEncoding) {
        this(buf, contentType, contentEncoding, false);
    }

    /**
     * Constructs a new instance with the given attributes kept as immutable.
     * <p>
     * The new instance:
     * </p>
     * <ul>
     * <li>starts reading it's contents at index 0 in the buffer.</li>
     * <li>has the content length of the given buffer.</li>
     * <li>does not define a content encoding.</li>
     * </ul>
     *
     * @param buf             The message body contents as a byte array buffer.
     * @param contentType     The content-type, may be null.
     * @param chunked         Whether this entity should be chunked.
     */
    public ByteArrayEntity(final byte[] buf, final ContentType contentType, final boolean chunked) {
        this(buf, contentType, null, chunked);
    }

    /**
     * Constructs a new instance with the given attributes kept as immutable.
     * <p>
     * The new instance:
     * </p>
     * <ul>
     * <li>is not chunked.</li>
     * <li>starts reading it's contents at index 0 in the buffer.</li>
     * <li>has the content length of the given buffer.</li>
     * <li>does not define a content encoding.</li>
     * </ul>
     *
     * @param buf              The message body contents as a byte array buffer.
     * @param contentType     The content-type, may be null.
     */
    public ByteArrayEntity(final byte[] buf, final ContentType contentType) {
        this(buf, contentType, null, false);
    }

    /**
     * Constructs a new instance with the given attributes kept as immutable.
     * <p>
     * The new instance:
     * </p>
     * <ul>
     * <li>does not define a content encoding.</li>
     * </ul>
     *
     * @param buf             The message body contents as a byte array buffer.
     * @param off             The offset in the buffer of the first byte to read.
     * @param len             The maximum number of bytes to read from the buffer.
     * @param contentType     The content-type, may be null.
     * @param chunked         Whether this entity should be chunked.
     */
    public ByteArrayEntity(
            final byte[] buf, final int off, final int len, final ContentType contentType, final boolean chunked) {
        this(buf, off, len, contentType, null, chunked);
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
     * @param buf             The message body contents as a byte array buffer.
     * @param off             The offset in the buffer of the first byte to read.
     * @param len             The maximum number of bytes to read from the buffer.
     * @param contentType     The content-type, may be null.
     * @since 5.0
     */
    public ByteArrayEntity(final byte[] buf, final int off, final int len, final ContentType contentType) {
        this(buf, off, len, contentType, null, false);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation always returns {@code true}.
     * </p>
     */
    @Override
    public final boolean isRepeatable() {
        return true;
    }

    @Override
    public final long getContentLength() {
        return this.len;
    }

    @Override
    public final InputStream getContent() {
        return new ByteArrayInputStream(this.buf, this.off, this.len);
    }

    @Override
    public final void writeTo(final OutputStream outStream) throws IOException {
        Args.notNull(outStream, "Output stream");
        outStream.write(this.buf, this.off, this.len);
        outStream.flush();
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

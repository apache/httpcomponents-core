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
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class ByteArrayEntity extends AbstractHttpEntity {

    private final byte[] b;
    private final int off, len;

    /**
     * @since 5.0
     */
    public ByteArrayEntity(
            final byte[] b, final int off, final int len, final ContentType contentType, final String contentEncoding,
            final boolean chunked) {
        super(contentType, contentEncoding, chunked);
        Args.notNull(b, "Source byte array");
        if ((off < 0) || (off > b.length) || (len < 0) ||
                ((off + len) < 0) || ((off + len) > b.length)) {
            throw new IndexOutOfBoundsException("off: " + off + " len: " + len + " b.length: " + b.length);
        }
        this.b = b;
        this.off = off;
        this.len = len;
    }

    /**
     * @since 5.0
     */
    public ByteArrayEntity(
            final byte[] b, final int off, final int len, final ContentType contentType, final String contentEncoding) {
        this(b, off, len, contentType, contentEncoding, false);
    }

    /**
     * @since 5.0
     */
    public ByteArrayEntity(
            final byte[] b, final ContentType contentType, final String contentEncoding, final boolean chunked) {
        super(contentType, contentEncoding, chunked);
        Args.notNull(b, "Source byte array");
        this.b = b;
        this.off = 0;
        this.len = this.b.length;
    }

    /**
     * @since 5.0
     */
    public ByteArrayEntity(final byte[] b, final ContentType contentType, final String contentEncoding) {
        this(b, contentType, contentEncoding, false);
    }

    public ByteArrayEntity(final byte[] b, final ContentType contentType, final boolean chunked) {
        this(b, contentType, null, chunked);
    }

    public ByteArrayEntity(final byte[] b, final ContentType contentType) {
        this(b, contentType, null, false);
    }

    public ByteArrayEntity(
            final byte[] b, final int off, final int len, final ContentType contentType,  final boolean chunked) {
        this(b, off, len, contentType, null, chunked);
    }

    public ByteArrayEntity(final byte[] b, final int off, final int len, final ContentType contentType) {
        this(b, off, len, contentType, null, false);
    }

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
        return new ByteArrayInputStream(this.b, this.off, this.len);
    }

    @Override
    public final void writeTo(final OutputStream outStream) throws IOException {
        Args.notNull(outStream, "Output stream");
        outStream.write(this.b, this.off, this.len);
        outStream.flush();
    }

    @Override
    public final boolean isStreaming() {
        return false;
    }

    @Override
    public final void close() throws IOException {
    }

}

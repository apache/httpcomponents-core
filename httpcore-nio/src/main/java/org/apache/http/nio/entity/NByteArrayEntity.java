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

package org.apache.http.nio.entity;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;

/**
 * A simple self contained, repeatable non-blocking entity that retrieves
 * its content from a byte array.
 *
 * @since 4.0
 */
@SuppressWarnings("deprecation")
@NotThreadSafe
public class NByteArrayEntity extends AbstractHttpEntity
                              implements HttpAsyncContentProducer, ProducingNHttpEntity {

    private final byte[] b;
    private final int off, len;
    private final ByteBuffer buf;
    /**
     * @deprecated (4.2)
     */
    @Deprecated
    protected final byte[] content;
    /**
     * @deprecated (4.2)
     */
    @Deprecated
    protected final ByteBuffer buffer;

    /**
     * @since 4.2
     */
    public NByteArrayEntity(final byte[] b, final ContentType contentType) {
        super();
        if (b == null) {
            throw new IllegalArgumentException("Source byte array may not be null");
        }
        this.b = b;
        this.off = 0;
        this.len = b.length;
        this.buf = ByteBuffer.wrap(b);
        this.content = b;
        this.buffer = this.buf;
        if (contentType != null) {
            setContentType(contentType.toString());
        }
    }

    /**
     * @since 4.2
     */
    public NByteArrayEntity(final byte[] b, int off, int len, final ContentType contentType) {
        super();
        if (b == null) {
            throw new IllegalArgumentException("Source byte array may not be null");
        }
        if ((off < 0) || (off > b.length) || (len < 0) ||
                ((off + len) < 0) || ((off + len) > b.length)) {
            throw new IndexOutOfBoundsException("off: " + off + " len: " + len + " b.length: " + b.length);
        }
        this.b = b;
        this.off = off;
        this.len = len;
        this.buf = ByteBuffer.wrap(b, off, len);
        this.content = b;
        this.buffer = this.buf;
        if (contentType != null) {
            setContentType(contentType.toString());
        }
    }

    public NByteArrayEntity(final byte[] b) {
        this(b, null);
    }

    public NByteArrayEntity(final byte[] b, int off, int len) {
        this(b, off, len, null);
    }

    /**
     * {@inheritDoc}
     *
     * @since 4.2
     */
    public void close() {
        this.buf.rewind();
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated (4.2) use {@link #close()}
     */
    public void finish() {
        close();
    }

    public void produceContent(final ContentEncoder encoder, final IOControl ioctrl)
            throws IOException {
        encoder.write(this.buf);
        if(!this.buf.hasRemaining()) {
            encoder.complete();
        }
    }

    public long getContentLength() {
        return this.len;
    }

    public boolean isRepeatable() {
        return true;
    }

    public boolean isStreaming() {
        return false;
    }

    public InputStream getContent() {
        return new ByteArrayInputStream(this.b, this.off, this.len);
    }

    public void writeTo(final OutputStream outstream) throws IOException {
        if (outstream == null) {
            throw new IllegalArgumentException("Output stream may not be null");
        }
        outstream.write(this.b, this.off, this.len);
        outstream.flush();
    }

}

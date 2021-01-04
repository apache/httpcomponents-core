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

package org.apache.hc.core5.http2.impl.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.apache.hc.core5.util.Args;

/**
 * @since 5.0
 */
abstract class FrameOutputStream extends OutputStream {

    private final OutputStream outputStream;

    private final byte[] cache;
    private int cachePosition;

    public FrameOutputStream(final int minChunkSize, final OutputStream outputStream) {
        super();
        this.outputStream = Args.notNull(outputStream, "Output stream");
        this.cache = new byte[minChunkSize];
    }

    protected abstract void write(final ByteBuffer src, final boolean endStream, final OutputStream outputStream) throws IOException;

    private void flushCache(final boolean endStream) throws IOException {
        if (this.cachePosition > 0) {
            write(ByteBuffer.wrap(this.cache, 0, this.cachePosition), endStream, this.outputStream);
            this.cachePosition = 0;
        }
    }

    @Override
    public void write(final int b) throws IOException {
        this.cache[this.cachePosition] = (byte) b;
        this.cachePosition++;
        if (this.cachePosition == this.cache.length) {
            flushCache(false);
        }
    }

    @Override
    public void write(final byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(final byte[] src, final int off, final int len) throws IOException {
        if (len >= this.cache.length - this.cachePosition) {
            flushCache(false);
            write(ByteBuffer.wrap(src, off, len), false, this.outputStream);
        } else {
            System.arraycopy(src, off, cache, this.cachePosition, len);
            this.cachePosition += len;
        }
    }

    @Override
    public void flush() throws IOException {
        flushCache(false);
        this.outputStream.flush();
    }

    @Override
    public void close() throws IOException {
        if (this.cachePosition > 0) {
            flushCache(true);
        } else {
            write(null, true, this.outputStream);
        }
        flushCache(true);
        this.outputStream.flush();
    }

}

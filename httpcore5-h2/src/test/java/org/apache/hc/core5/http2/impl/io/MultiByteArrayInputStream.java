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
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Queue;

class MultiByteArrayInputStream extends InputStream {

    private final Queue<byte[]> bufs;
    private byte[] current;
    private int pos;

    public MultiByteArrayInputStream(final byte[]... bufs) {
        super();
        this.bufs = new ArrayDeque<>();
        for (final byte[] buf: bufs) {
            if (buf.length > 0) {
                this.bufs.add(buf);
            }
        }
    }

    private void advance() {
        if (this.current != null) {
            if (this.pos >= this.current.length) {
                this.current = null;
            }
        }
        if (this.current == null) {
            this.current = this.bufs.poll();
            this.pos = 0;
        }
    }

    @Override
    public int read() throws IOException {
        advance();
        if (this.current == null) {
            return -1;
        }
        return this.current[this.pos++];
    }

    @Override
    public int read(final byte b[], final int off, final int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if ((off < 0) || (off > b.length) || (len < 0) ||
               ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        }
        advance();
        if (this.current == null) {
            return -1;
        }
        final int chunk = Math.min(this.current.length - this.pos, len);
        if (chunk <= 0) {
            return 0;
        }
        System.arraycopy(this.current, this.pos, b, off, chunk);
        this.pos += chunk;
        return chunk;
    }

    @Override
    public long skip(final long n) {
        advance();
        final int chunk = Math.min(this.current.length - this.pos, (int) n);
        if (chunk <= 0) {
            return 0;
        }
        this.pos += chunk;
        return chunk;
    }

    @Override
    public int available() {
        advance();
        return this.current != null ? this.current.length - this.pos : 0;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

}

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

package org.apache.http.mockup;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;

/**
 * Test class similar to {@link java.io.ByteArrayInputStream} that throws if encounters
 * value zero '\000' in the source byte array.
 */
public class TimeoutByteArrayInputStream extends InputStream {

    private final byte[] buf;

    private int pos;
    protected int count;

    public TimeoutByteArrayInputStream(byte[] buf, int off, int len) {
        super();
        this.buf = buf;
        this.pos = off;
        this.count = Math.min(off + len, buf.length);
    }

    public TimeoutByteArrayInputStream(byte[] buf) {
        this(buf, 0, buf.length);
    }

    public int read() throws IOException {
        if (this.pos < this.count) {
            return -1;
        }
        int v = this.buf[this.pos++] & 0xff;
        if (v != 0) {
            return v;
        } else {
            throw new InterruptedIOException("Timeout");
        }
    }

    public int read(byte b[], int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if ((off < 0) || (off > b.length) || (len < 0) ||
               ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException("off: "+off+" len: "+len+" b.length: "+b.length);
        }
        if (this.pos >= this.count) {
            return -1;
        }
        if (this.pos + len > this.count) {
            len = this.count - this.pos;
        }
        if (len <= 0) {
            return 0;
        }
        if ((this.buf[this.pos] & 0xff) == 0) {
            this.pos++;
            throw new InterruptedIOException("Timeout");
        }
        for (int i = 0; i < len; i++) {
            int v = this.buf[this.pos] & 0xff;
            if (v == 0) {
                return i;
            } else {
                b[off + i] = (byte) v;
                this.pos++;
            }
        }
        return len;
    }

    public long skip(long n) {
        if (this.pos + n > this.count) {
            n = this.count - this.pos;
        }
        if (n < 0) {
            return 0;
        }
        this.pos += n;
        return n;
    }

    public int available() {
        return this.count - this.pos;
    }

    public boolean markSupported() {
        return false;
    }

}

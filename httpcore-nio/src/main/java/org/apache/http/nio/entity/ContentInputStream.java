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

import java.io.IOException;
import java.io.InputStream;

import org.apache.http.io.BufferInfo;
import org.apache.http.nio.util.ContentInputBuffer;

/**
 * {@link InputStream} adaptor for {@link ContentInputBuffer}.
 *
 * @since 4.0
 */
public class ContentInputStream extends InputStream {

    private final ContentInputBuffer buffer;

    public ContentInputStream(final ContentInputBuffer buffer) {
        super();
        if (buffer == null) {
            throw new IllegalArgumentException("Input buffer may not be null");
        }
        this.buffer = buffer;
    }

    @Override
    public int available() throws IOException {
        if (this.buffer instanceof BufferInfo) {
            return ((BufferInfo) this.buffer).length();
        } else {
            return super.available();
        }
    }

    @Override
    public int read(final byte[] b, int off, int len) throws IOException {
        return this.buffer.read(b, off, len);
    }

    @Override
    public int read(final byte[] b) throws IOException {
        if (b == null) {
            return 0;
        }
        return this.buffer.read(b, 0, b.length);
    }

    @Override
    public int read() throws IOException {
        return this.buffer.read();
    }

    @Override
    public void close() throws IOException {
        // read and discard the remainder of the message
        byte tmp[] = new byte[1024];
        while (this.buffer.read(tmp, 0, tmp.length) >= 0) {
        }
        super.close();
    }

}

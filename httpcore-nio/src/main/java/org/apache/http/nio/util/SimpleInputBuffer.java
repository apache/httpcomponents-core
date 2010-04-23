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
package org.apache.http.nio.util;

import java.io.IOException;

import org.apache.http.nio.ContentDecoder;

/**
 * Basic implementation of the {@link ContentInputBuffer} interface.
 * <p>
 * This class is not thread safe.
 *
 * @since 4.0
 */
public class SimpleInputBuffer extends ExpandableBuffer implements ContentInputBuffer {

    private boolean endOfStream = false;

    public SimpleInputBuffer(int buffersize, final ByteBufferAllocator allocator) {
        super(buffersize, allocator);
    }

    public void reset() {
        this.endOfStream = false;
        super.clear();
    }

    public int consumeContent(final ContentDecoder decoder) throws IOException {
        setInputMode();
        int totalRead = 0;
        int bytesRead;
        while ((bytesRead = decoder.read(this.buffer)) != -1) {
            if (bytesRead == 0) {
                if (!this.buffer.hasRemaining()) {
                    expand();
                } else {
                    break;
                }
            } else {
                totalRead += bytesRead;
            }
        }
        if (bytesRead == -1 || decoder.isCompleted()) {
            this.endOfStream = true;
        }
        return totalRead;
    }

    public boolean isEndOfStream() {
        return !hasData() && this.endOfStream;
    }

    public int read() throws IOException {
        if (isEndOfStream()) {
            return -1;
        }
        return this.buffer.get() & 0xff;
    }

    public int read(final byte[] b, int off, int len) throws IOException {
        if (isEndOfStream()) {
            return -1;
        }
        if (b == null) {
            return 0;
        }
        setOutputMode();
        int chunk = len;
        if (chunk > this.buffer.remaining()) {
            chunk = this.buffer.remaining();
        }
        this.buffer.get(b, off, chunk);
        return chunk;
    }

    public int read(final byte[] b) throws IOException {
        if (isEndOfStream()) {
            return -1;
        }
        if (b == null) {
            return 0;
        }
        return read(b, 0, b.length);
    }

    public void shutdown() {
        this.endOfStream = true;
    }

}

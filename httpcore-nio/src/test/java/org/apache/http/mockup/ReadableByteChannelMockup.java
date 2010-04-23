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
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

import org.apache.http.util.EncodingUtils;

public class ReadableByteChannelMockup implements ReadableByteChannel {

    private final String[] chunks;
    private final String charset;

    private int chunkCount = 0;

    private ByteBuffer currentChunk;
    private boolean closed = false;

    public ReadableByteChannelMockup(final String[] chunks, final String charset) {
        super();
        this.chunks = chunks;
        this.charset = charset;
    }

    private void prepareChunk() {
        if (this.currentChunk == null || !this.currentChunk.hasRemaining()) {
            if (this.chunkCount < this.chunks.length) {
                String s = this.chunks[this.chunkCount];
                this.chunkCount++;
                this.currentChunk = ByteBuffer.wrap(EncodingUtils.getBytes(s, this.charset));
            } else {
                this.closed = true;
            }
        }
    }

    public int read(final ByteBuffer dst) throws IOException {
        prepareChunk();
        if (this.closed) {
            return -1;
        }
        int i = 0;
        while (dst.hasRemaining() && this.currentChunk.hasRemaining()) {
            dst.put(this.currentChunk.get());
            i++;
        }
        return i;
    }

    public void close() throws IOException {
        this.closed = true;
    }

    public boolean isOpen() {
        return !this.closed;
    }


}

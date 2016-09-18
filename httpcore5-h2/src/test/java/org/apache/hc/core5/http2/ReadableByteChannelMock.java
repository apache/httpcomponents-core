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

package org.apache.hc.core5.http2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;

public class ReadableByteChannelMock implements ReadableByteChannel {

    private final byte[][] chunks;

    private int chunkCount = 0;

    private ByteBuffer currentChunk;
    private boolean eof = false;
    private boolean closed = false;

    public ReadableByteChannelMock(final byte[]... chunks) {
        super();
        this.chunks = chunks;
    }

    private void prepareChunk() {
        if (this.currentChunk == null || !this.currentChunk.hasRemaining()) {
            if (this.chunkCount < this.chunks.length) {
                final byte[] bytes = this.chunks[this.chunkCount];
                this.chunkCount++;
                this.currentChunk = ByteBuffer.wrap(bytes);
            } else {
                this.eof = true;
            }
        }
    }

    @Override
    public int read(final ByteBuffer dst) throws IOException {
        if (this.closed) {
            throw new ClosedChannelException();
        }
        prepareChunk();
        if (this.eof) {
            return -1;
        }
        int i = 0;
        while (dst.hasRemaining() && this.currentChunk.hasRemaining()) {
            dst.put(this.currentChunk.get());
            i++;
        }
        return i;
    }

    @Override
    public void close() throws IOException {
        this.closed = true;
    }

    @Override
    public boolean isOpen() {
        return !this.closed && !this.eof;
    }

}

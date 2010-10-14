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

package org.apache.http.impl.nio.codecs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.io.BufferInfo;
import org.apache.http.nio.reactor.SessionOutputBuffer;
import org.apache.http.util.CharArrayBuffer;

/**
 * Implements chunked transfer coding. The content is sent in small chunks.
 * Entities transferred using this decoder can be of unlimited length.
 *
 * @since 4.0
 */
public class ChunkEncoder extends AbstractContentEncoder {

    private final CharArrayBuffer lineBuffer;

    private final BufferInfo bufferinfo;

    public ChunkEncoder(
            final WritableByteChannel channel,
            final SessionOutputBuffer buffer,
            final HttpTransportMetricsImpl metrics) {
        super(channel, buffer, metrics);
        this.lineBuffer = new CharArrayBuffer(16);
        if (buffer instanceof BufferInfo) {
            this.bufferinfo = (BufferInfo) buffer;
        } else {
            this.bufferinfo = null;
        }
    }

    public int write(final ByteBuffer src) throws IOException {
        if (src == null) {
            return 0;
        }
        assertNotCompleted();
        int chunk = src.remaining();
        if (chunk == 0) {
            return 0;
        }

        long bytesWritten = this.buffer.flush(this.channel);
        if (bytesWritten > 0) {
            this.metrics.incrementBytesTransferred(bytesWritten);
        }
        int avail;
        if (this.bufferinfo != null) {
            avail = this.bufferinfo.available();
        } else {
            avail = 4096;
        }

        // subtract the length of the longest chunk header
        // 12345678\r\n
        // <chunk-data>\r\n
        avail -= 12;
        if (avail <= 0) {
            return 0;
        } else if (avail < chunk) {
            // write no more than 'avail' bytes
            chunk = avail;
            this.lineBuffer.clear();
            this.lineBuffer.append(Integer.toHexString(chunk));
            this.buffer.writeLine(this.lineBuffer);
            int oldlimit = src.limit();
            src.limit(src.position() + chunk);
            this.buffer.write(src);
            src.limit(oldlimit);
        } else {
            // write all
            this.lineBuffer.clear();
            this.lineBuffer.append(Integer.toHexString(chunk));
            this.buffer.writeLine(this.lineBuffer);
            this.buffer.write(src);
        }
        this.lineBuffer.clear();
        this.buffer.writeLine(this.lineBuffer);
        return chunk;
    }

    @Override
    public void complete() throws IOException {
        assertNotCompleted();
        this.lineBuffer.clear();
        this.lineBuffer.append("0");
        this.buffer.writeLine(this.lineBuffer);
        this.lineBuffer.clear();
        this.buffer.writeLine(this.lineBuffer);
        this.completed = true; // == super.complete()
    }

    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("[chunk-coded; completed: ");
        buffer.append(this.completed);
        buffer.append("]");
        return buffer.toString();
    }

}

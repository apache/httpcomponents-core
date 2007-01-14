/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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

package org.apache.http.nio.impl.codecs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

import org.apache.http.nio.impl.reactor.SessionInputBuffer;

public class LengthDelimitedDecoder extends AbstractContentDecoder {
    
    private final long contentLength;
    
    private long len;

    public LengthDelimitedDecoder(
            final ReadableByteChannel channel, 
            final SessionInputBuffer buffer,
            long contentLength) {
        super(channel, buffer);
        if (contentLength < 0) {
            throw new IllegalArgumentException("Content length may not be negative");
        }
        this.contentLength = contentLength;
    }

    public int read(final ByteBuffer dst) throws IOException {
        if (dst == null) {
            throw new IllegalArgumentException("Byte buffer may not be null");
        }
        if (this.completed) {
            return -1;
        }
        int lenRemaining = (int) (this.contentLength - this.len);
        
        int bytesRead;
        if (this.buffer.hasData()) {
            int maxLen = Math.min(lenRemaining, this.buffer.length());
            bytesRead = this.buffer.read(dst, maxLen);
        } else {
            if (dst.remaining() > lenRemaining) {
                int oldLimit = dst.limit();
                int newLimit = oldLimit - (dst.remaining() - lenRemaining);
                dst.limit(newLimit);
                bytesRead = this.channel.read(dst);
                dst.limit(oldLimit);
            } else {
                bytesRead = this.channel.read(dst);
            }
        }
        this.len += bytesRead;
        if (this.len >= this.contentLength) {
            this.completed = true;
        }
        return bytesRead;
    }

}

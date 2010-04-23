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
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.nio.FileContentEncoder;
import org.apache.http.nio.reactor.SessionOutputBuffer;

/**
 * Content encoder that writes data without any transformation. The end of
 * the content entity is demarcated by closing the underlying connection
 * (EOF condition). Entities transferred using this input stream can be of
 * unlimited length.
 * <p>
 * This decoder is optimized to transfer data directly from
 * a {@link FileChannel} to the underlying I/O session's channel whenever
 * possible avoiding intermediate buffering in the session buffer.
 *
 * @since 4.0
 */
public class IdentityEncoder extends AbstractContentEncoder
        implements FileContentEncoder {

    public IdentityEncoder(
            final WritableByteChannel channel,
            final SessionOutputBuffer buffer,
            final HttpTransportMetricsImpl metrics) {
        super(channel, buffer, metrics);
    }

    public int write(final ByteBuffer src) throws IOException {
        if (src == null) {
            return 0;
        }
        assertNotCompleted();
        int bytesWritten = this.channel.write(src);
        if (bytesWritten > 0) {
            this.metrics.incrementBytesTransferred(bytesWritten);
        }
        return bytesWritten;
    }

    public long transfer(
            final FileChannel src,
            long position,
            long count) throws IOException {

        if (src == null) {
            return 0;
        }
        assertNotCompleted();
        long bytesWritten = src.transferTo(position, count, this.channel);
        if (bytesWritten > 0) {
            this.metrics.incrementBytesTransferred(bytesWritten);
        }
        return bytesWritten;
    }

    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("[identity; completed: ");
        buffer.append(this.completed);
        buffer.append("]");
        return buffer.toString();
    }

}

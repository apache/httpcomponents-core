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

package org.apache.hc.core5.http.impl.io;

import java.io.IOException;
import java.io.InputStream;

import org.apache.hc.core5.http.StreamClosedException;
import org.apache.hc.core5.http.io.SessionInputBuffer;
import org.apache.hc.core5.util.Args;

/**
 * Input stream that reads data without any transformation. The end of the
 * content entity is demarcated by closing the underlying connection
 * (EOF condition). Entities transferred using this input stream can be of
 * unlimited length.
 * <p>
 * Note that this class NEVER closes the underlying stream, even when
 * {@link #close()} gets called.  Instead, the stream will be marked as
 * closed and no further reading will be permitted.
 *
 * @since 4.0
 */
public class IdentityInputStream extends InputStream {

    private final SessionInputBuffer buffer;
    private final InputStream inputStream;

    private boolean closed;

    /**
     * Default constructor.
     *
     * @param buffer Session input buffer
     * @param inputStream Input stream
     */
    public IdentityInputStream(final SessionInputBuffer buffer, final InputStream inputStream) {
        super();
        this.buffer = Args.notNull(buffer, "Session input buffer");
        this.inputStream = Args.notNull(inputStream, "Input stream");
    }

    @Override
    public int available() throws IOException {
        if (this.closed) {
            return 0;
        }
        final int n = this.buffer.length();
        return n > 0 ? n : this.inputStream.available();
    }

    /**
     * Marks this stream as closed, but does NOT close the underlying stream.
     * @throws IOException If an I/O problem occurs.
     */
    @Override
    public void close() throws IOException {
        this.closed = true;
    }

    @Override
    public int read() throws IOException {
        if (this.closed) {
            throw new StreamClosedException();
        }
        return this.buffer.read(this.inputStream);
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        if (this.closed) {
            throw new StreamClosedException();
        }
        return this.buffer.read(b, off, len, this.inputStream);
    }

}

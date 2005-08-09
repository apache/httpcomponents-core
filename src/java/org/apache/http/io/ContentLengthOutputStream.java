/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class ContentLengthOutputStream extends OutputStream {
    
    /**
     * Wrapped output stream that all calls are delegated to.
     */
    private final OutputStream wrappedStream;

    /**
     * The maximum number of bytes that can be written the stream. Subsequent
     * write operations will be ignored.
     */
    private final long contentLength;

    /** Total bytes written */
    private long total = 0;

    /** True if the stream is closed. */
    private boolean closed = false;

    /**
     * Creates a new length limited stream
     *
     * @param in The stream to wrap
     * @param contentLength The maximum number of bytes that can be written to
     * the stream. Subsequent write operations will be ignored.
     * 
     * @since 4.0
     */
    public ContentLengthOutputStream(final OutputStream out, long contentLength) {
        super();
        if (out == null) {
            throw new IllegalArgumentException("Output stream may not be null");
        }
        if (contentLength < 0) {
            throw new IllegalArgumentException("Content length may not be negative");
        }
        this.wrappedStream = out;
        this.contentLength = contentLength;
    }

    /**
     * <p>Does not close the underlying socket output.</p>
     * 
     * @throws IOException If an I/O problem occurs.
     */
    public void close() throws IOException {
        this.closed = true;
    }

    public void flush() throws IOException {
        this.wrappedStream.flush();
    }

    public void write(byte[] b, int off, int len) throws IOException {
        if (this.closed) {
            throw new IOException("Attempted write to closed stream.");
        }
        if (this.total < this.contentLength) {
            long max = this.contentLength - this.total;
            if (len > max) {
                len = (int) max;
            }
            this.wrappedStream.write(b, off, len);
            this.total += len;
        }
    }

    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    public void write(int b) throws IOException {
        if (this.closed) {
            throw new IOException("Attempted write to closed stream.");
        }
        if (this.total < this.contentLength) {
            this.wrappedStream.write(b);
            this.total++;
        }
    }
    
}

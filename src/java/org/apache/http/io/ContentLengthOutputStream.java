/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2006 The Apache Software Foundation
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
 * A stream wrapper that closes itself after a defined number of bytes.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class ContentLengthOutputStream extends OutputStream {
    
    /**
     * Wrapped data transmitter that all calls are delegated to.
     */
    private final HttpDataTransmitter out;

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
     * @param out The data transmitter to wrap
     * @param contentLength The maximum number of bytes that can be written to
     * the stream. Subsequent write operations will be ignored.
     * 
     * @since 4.0
     */
    public ContentLengthOutputStream(final HttpDataTransmitter out, long contentLength) {
        super();
        if (out == null) {
            throw new IllegalArgumentException("HTTP data transmitter may not be null");
        }
        if (contentLength < 0) {
            throw new IllegalArgumentException("Content length may not be negative");
        }
        this.out = out;
        this.contentLength = contentLength;
    }

    /**
     * <p>Does not close the underlying socket output.</p>
     * 
     * @throws IOException If an I/O problem occurs.
     */
    public void close() throws IOException {
        if (!this.closed) {
            this.closed = true;
            this.out.flush();
        }
    }

    public void flush() throws IOException {
        this.out.flush();
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
            this.out.write(b, off, len);
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
            this.out.write(b);
            this.total++;
        }
    }
    
}

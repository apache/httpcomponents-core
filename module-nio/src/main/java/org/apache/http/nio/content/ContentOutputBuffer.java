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
package org.apache.http.nio.content;

import java.io.IOException;

import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.ContentIOControl;

public class ContentOutputBuffer extends ExpandableBuffer {
    
    private final ContentIOControl ioctrl;
    private final Object mutex;
    
    private volatile boolean shutdown = false;
    private volatile boolean endOfStream = false;
    private volatile IOException exception = null;
    
    public ContentOutputBuffer(int buffersize, final ContentIOControl ioctrl) {
        super(buffersize);
        if (ioctrl == null) {
            throw new IllegalArgumentException("I/O content control may not be null");
        }
        this.ioctrl = ioctrl;
        this.mutex = new Object();
    }

    public void reset() {
        if (this.shutdown) {
            return;
        }
        synchronized (this.mutex) {
            clear();
            this.endOfStream = false;
        }
    }
    
    public void produceContent(final ContentEncoder encoder) throws IOException {
        if (this.shutdown) {
            return;
        }
        synchronized (this.mutex) {
            setOutputMode();
            encoder.write(this.buffer);
            if (!hasData()) {
                if (this.endOfStream) {
                    encoder.complete();
                }
                this.ioctrl.suspendOutput();
                this.mutex.notifyAll();            
            }
        }
    }
    
    protected void flushBuffer() throws IOException {
        this.ioctrl.requestOutput();
        synchronized (this.mutex) {
            setOutputMode();
            try {
                while (hasData() && !this.shutdown) {
                    this.mutex.wait();
                }

                IOException ex = this.exception;
                if (ex != null) {
                    throw ex;
                }
                
            } catch (InterruptedException ex) {
                throw new IOException("Interrupted while waiting for more data");
            }
        }
    }

    public void shutdown() {
        if (this.shutdown) {
            return;
        }
        this.shutdown = true;
        synchronized (this.mutex) {
            this.mutex.notifyAll();
        }
    }

    public void shutdown(final IOException exception) {
        this.exception = exception;
        shutdown();
    }

    public void write(final byte[] b, int off, int len) throws IOException {
        if (b == null) {
            return;
        }
        if (this.shutdown || this.endOfStream) {
            return;
        }
        synchronized (this.mutex) {
            setInputMode();
            int remaining = len;
            while (remaining > 0) {
                if (!this.buffer.hasRemaining()) {
                    flushBuffer();
                    setInputMode();
                }
                int chunk = Math.min(remaining, this.buffer.remaining());
                this.buffer.put(b, off, chunk);
                remaining -= chunk;
                off += chunk;
            }
        }
    }

    public void write(final byte[] b) throws IOException {
        if (b == null) {
            return;
        }
        write(b, 0, b.length);
    }

    public void write(int b) throws IOException {
        if (this.shutdown || this.endOfStream) {
            return;
        }
        synchronized (this.mutex) {
            setInputMode();
            if (!this.buffer.hasRemaining()) {
                flushBuffer();
                setInputMode();
            }
            this.buffer.put((byte)b);
        }
    }

    public void flush() throws IOException {
        flushBuffer();
    }
    
    public void close() throws IOException {
        this.endOfStream = true;
        flushBuffer();
    }
    
}

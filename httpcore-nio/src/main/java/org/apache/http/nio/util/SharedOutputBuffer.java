/*
 * $HeadURL:https://svn.apache.org/repos/asf/jakarta/httpcomponents/httpcore/trunk/module-nio/src/main/java/org/apache/http/nio/buffer/ContentOutputBuffer.java $
 * $Revision:473999 $
 * $Date:2006-11-12 17:31:38 +0000 (Sun, 12 Nov 2006) $
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
package org.apache.http.nio.util;

import java.io.IOException;
import java.io.InterruptedIOException;

import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;

/**
 * Implementation of the {@link ContentOutputBuffer} interface that can be 
 * shared by multiple threads, usually the I/O dispatch of an I/O reactor and
 * a worker thread.
 * <p>
 * Please note this class is thread safe only when used though 
 * the {@link ContentOutputBuffer} interface. 
 *
 * @since 4.0
 */
public class SharedOutputBuffer extends ExpandableBuffer implements ContentOutputBuffer {

    private final IOControl ioctrl;
    private final Object mutex;
    
    private volatile boolean shutdown = false;
    private volatile boolean endOfStream = false;
    
    public SharedOutputBuffer(int buffersize, final IOControl ioctrl, final ByteBufferAllocator allocator) {
        super(buffersize, allocator);
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
    
    public int produceContent(final ContentEncoder encoder) throws IOException {
        if (this.shutdown) {
            return -1;
        }
        synchronized (this.mutex) {
            setOutputMode();
            int bytesWritten = 0;
            if (hasData()) {
                bytesWritten = encoder.write(this.buffer);
                if (encoder.isCompleted()) {
                    this.endOfStream = true;
                }
            }
            if (!hasData()) {
                // No more buffered content
                // If at the end of the stream, terminate
                if (this.endOfStream && !encoder.isCompleted()) {
                    encoder.complete();
                } 
                if (!this.endOfStream) {
                    // suspend output events
                    this.ioctrl.suspendOutput();
                }
            }
            this.mutex.notifyAll();            
            return bytesWritten;
        }
    }
    
    public void close() {
        shutdown();
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

    public void write(final byte[] b, int off, int len) throws IOException {
        if (b == null) {
            return;
        }
        synchronized (this.mutex) {
            if (this.shutdown || this.endOfStream) {
                throw new IllegalStateException("Buffer already closed for writing");
            }
            setInputMode();
            int remaining = len;
            while (remaining > 0) {
                if (!this.buffer.hasRemaining()) {
                    flushContent();
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
        synchronized (this.mutex) {
            if (this.shutdown || this.endOfStream) {
                throw new IllegalStateException("Buffer already closed for writing");
            }
            setInputMode();
            if (!this.buffer.hasRemaining()) {
                flushContent();
                setInputMode();
            }
            this.buffer.put((byte)b);
        }
    }

    public void flush() throws IOException {
    }

    private void flushContent() throws IOException {
        synchronized (this.mutex) {
            try {
                while (hasData()) {
                    if (this.shutdown) {
                        throw new InterruptedIOException("Output operation aborted");
                    }
                    this.ioctrl.requestOutput();
                    this.mutex.wait();
                }
            } catch (InterruptedException ex) {
                throw new IOException("Interrupted while flushing the content buffer");
            }
        }
    }
    
    public void writeCompleted() throws IOException {
        synchronized (this.mutex) {
            if (this.endOfStream) {
                return;
            }
            this.endOfStream = true;
            this.ioctrl.requestOutput();
        }
    }
    
}

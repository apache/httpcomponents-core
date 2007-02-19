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

import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.ContentIOControl;

public class SharedOutputBuffer extends ExpandableBuffer implements ContentOutputBuffer {

    private static final int READY      = 0;
    private static final int STREAMING  = 1;
    private static final int CLOSING    = 2;
    private static final int CLOSED     = 4;
    
    private final ContentIOControl ioctrl;
    private final Object mutex;
    
    private volatile boolean shutdown = false;
    private volatile int state;
    
    public SharedOutputBuffer(int buffersize, final ContentIOControl ioctrl) {
        super(buffersize);
        if (ioctrl == null) {
            throw new IllegalArgumentException("I/O content control may not be null");
        }
        this.ioctrl = ioctrl;
        this.mutex = new Object();
        this.state = READY;
    }

    public void reset() {
        if (this.shutdown) {
            return;
        }
        synchronized (this.mutex) {
            clear();
            this.state = READY;
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
                    this.state = CLOSED;
                }
            }
            if (!hasData()) {
                // No more buffered content
                // If at the end of the stream, terminate
                if (this.state == CLOSING && !encoder.isCompleted()) {
                    encoder.complete();
                    this.state = CLOSED;
                } else {
                    // suspend output events
                    this.ioctrl.suspendOutput();
                }
            }
            this.mutex.notifyAll();            
            return bytesWritten;
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

    public void write(final byte[] b, int off, int len) throws IOException {
        if (b == null) {
            return;
        }
        synchronized (this.mutex) {
            if (this.shutdown || this.state == CLOSING || this.state == CLOSED) {
                throw new IllegalStateException("Buffer already closed for writing");
            }
            this.state = STREAMING;
            setInputMode();
            int remaining = len;
            while (remaining > 0) {
                if (!this.buffer.hasRemaining()) {
                    flush();
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
            if (this.shutdown || this.state == CLOSING || this.state == CLOSED) {
                throw new IllegalStateException("Buffer already closed for writing");
            }
            this.state = STREAMING;
            setInputMode();
            if (!this.buffer.hasRemaining()) {
                flush();
                setInputMode();
            }
            this.buffer.put((byte)b);
        }
    }

    public void flush() throws IOException {
        synchronized (this.mutex) {
            try {
                while (hasData() && !this.shutdown) {
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
            this.state = CLOSING;
            try {
                while (hasData() && this.state != CLOSED && !this.shutdown) {
                    this.ioctrl.requestOutput();
                    this.mutex.wait();
                }
            } catch (InterruptedException ex) {
                throw new IOException("Interrupted while closing the content buffer");
            }
        }
    }
    
}

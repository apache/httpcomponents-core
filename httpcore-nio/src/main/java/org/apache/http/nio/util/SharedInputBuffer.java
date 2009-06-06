/*
 * $HeadURL:https://svn.apache.org/repos/asf/jakarta/httpcomponents/httpcore/trunk/module-nio/src/main/java/org/apache/http/nio/buffer/ContentInputBuffer.java $
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

import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;

/**
 * Implementation of the {@link ContentInputBuffer} interface that can be 
 * shared by multiple threads, usually the I/O dispatch of an I/O reactor and
 * a worker thread. 
 * <p>
 * Please note this class is thread safe only when used though 
 * the {@link ContentInputBuffer} interface. 
 *
 * @since 4.0
 */
public class SharedInputBuffer extends ExpandableBuffer implements ContentInputBuffer {

    private final IOControl ioctrl;
    private final Object mutex;
    
    private volatile boolean shutdown = false;
    private volatile boolean endOfStream = false;
    
    public SharedInputBuffer(int buffersize, final IOControl ioctrl, final ByteBufferAllocator allocator) {
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
    
    public int consumeContent(final ContentDecoder decoder) throws IOException {
        if (this.shutdown) {
            return -1;
        }
        synchronized (this.mutex) {
            setInputMode();
            int totalRead = 0;
            int bytesRead;
            while ((bytesRead = decoder.read(this.buffer)) > 0) {
                totalRead += bytesRead; 
            }
            if (bytesRead == -1 || decoder.isCompleted()) {
                this.endOfStream = true;
            }
            if (!this.buffer.hasRemaining()) {
                this.ioctrl.suspendInput();
            }
            this.mutex.notifyAll();
            
            if (totalRead > 0) {
                return totalRead;
            } else {
                if (this.endOfStream) {
                    return -1;
                } else {
                    return 0;
                }
            }
        }
    }
    
    protected void waitForData() throws IOException {
        synchronized (this.mutex) {
            try {
                while (!hasData() && !this.endOfStream) {
                    if (this.shutdown) {
                        throw new InterruptedIOException("Input operation aborted");
                    }
                    this.ioctrl.requestInput();
                    this.mutex.wait();
                }
            } catch (InterruptedException ex) {
                throw new IOException("Interrupted while waiting for more data");
            }
        }
    }

    public void close() {
        if (this.shutdown) {
            return;
        }
        this.endOfStream = true;
        synchronized (this.mutex) {
            this.mutex.notifyAll();
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

    protected boolean isShutdown() {
        return this.shutdown;
    }

    protected boolean isEndOfStream() {
        return this.shutdown || (!hasData() && this.endOfStream);
    }

    public int read() throws IOException {
        if (this.shutdown) {
            return -1;
        }
        synchronized (this.mutex) {
            if (!hasData()) {
                waitForData();
            }
            if (isEndOfStream()) {
                return -1; 
            }
            return this.buffer.get() & 0xff;
        }
    }

    public int read(final byte[] b, int off, int len) throws IOException {
        if (this.shutdown) {
            return -1;
        }
        if (b == null) {
            return 0;
        }
        synchronized (this.mutex) {
            if (!hasData()) {
                waitForData();
            }
            if (isEndOfStream()) {
                return -1; 
            }
            setOutputMode();
            int chunk = len;
            if (chunk > this.buffer.remaining()) {
                chunk = this.buffer.remaining();
            }
            this.buffer.get(b, off, chunk);
            return chunk;
        }
    }

    public int read(final byte[] b) throws IOException {
        if (this.shutdown) {
            return -1;
        }
        if (b == null) {
            return 0;
        }
        return read(b, 0, b.length);
    }

}

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
package org.apache.http.nio.util;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;

/**
 * Implementation of the {@link ContentInputBuffer} interface that can be
 * shared by multiple threads, usually the I/O dispatch of an I/O reactor and
 * a worker thread.
 * <p>
 * The I/O dispatch thread is expect to transfer data from {@link ContentDecoder} to the buffer
 *   by calling {@link #consumeContent(ContentDecoder)}.
 * <p>
 * The worker thread is expected to read the data from the buffer by calling
 *   {@link #read()} or {@link #read(byte[], int, int)} methods.
 * <p>
 * In case of an abnormal situation or when no longer needed the buffer must be shut down
 * using {@link #shutdown()} method.
 *
 * @since 4.0
 */
public class SharedInputBuffer extends ExpandableBuffer implements ContentInputBuffer {

    private final IOControl ioctrl;
    private final ReentrantLock lock;
    private final Condition condition;

    private volatile boolean shutdown = false;
    private volatile boolean endOfStream = false;

    public SharedInputBuffer(int buffersize, final IOControl ioctrl, final ByteBufferAllocator allocator) {
        super(buffersize, allocator);
        if (ioctrl == null) {
            throw new IllegalArgumentException("I/O content control may not be null");
        }
        this.ioctrl = ioctrl;
        this.lock = new ReentrantLock();
        this.condition = this.lock.newCondition();
    }

    public void reset() {
        if (this.shutdown) {
            return;
        }
        this.lock.lock();
        try {
            clear();
            this.endOfStream = false;
        } finally {
            this.lock.unlock();
        }
    }

    public int consumeContent(final ContentDecoder decoder) throws IOException {
        if (this.shutdown) {
            return -1;
        }
        this.lock.lock();
        try {
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
            this.condition.signalAll();

            if (totalRead > 0) {
                return totalRead;
            } else {
                if (this.endOfStream) {
                    return -1;
                } else {
                    return 0;
                }
            }
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public boolean hasData() {
        this.lock.lock();
        try {
            return super.hasData();
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public int available() {
        this.lock.lock();
        try {
            return super.available();
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public int capacity() {
        this.lock.lock();
        try {
            return super.capacity();
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public int length() {
        this.lock.lock();
        try {
            return super.length();
        } finally {
            this.lock.unlock();
        }
    }

    protected void waitForData() throws IOException {
        this.lock.lock();
        try {
            try {
                while (!super.hasData() && !this.endOfStream) {
                    if (this.shutdown) {
                        throw new InterruptedIOException("Input operation aborted");
                    }
                    this.ioctrl.requestInput();
                    this.condition.await();
                }
            } catch (InterruptedException ex) {
                throw new IOException("Interrupted while waiting for more data");
            }
        } finally {
            this.lock.unlock();
        }
    }

    public void close() {
        if (this.shutdown) {
            return;
        }
        this.endOfStream = true;
        this.lock.lock();
        try {
            this.condition.signalAll();
        } finally {
            this.lock.unlock();
        }
    }

    public void shutdown() {
        if (this.shutdown) {
            return;
        }
        this.shutdown = true;
        this.lock.lock();
        try {
            this.condition.signalAll();
        } finally {
            this.lock.unlock();
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
        this.lock.lock();
        try {
            if (!hasData()) {
                waitForData();
            }
            if (isEndOfStream()) {
                return -1;
            }
            return this.buffer.get() & 0xff;
        } finally {
            this.lock.unlock();
        }
    }

    public int read(final byte[] b, int off, int len) throws IOException {
        if (this.shutdown) {
            return -1;
        }
        if (b == null) {
            return 0;
        }
        this.lock.lock();
        try {
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
        } finally {
            this.lock.unlock();
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

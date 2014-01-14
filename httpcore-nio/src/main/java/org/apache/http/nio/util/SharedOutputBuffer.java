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

import org.apache.http.annotation.ThreadSafe;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.util.Args;
import org.apache.http.util.Asserts;

/**
 * Implementation of the {@link ContentOutputBuffer} interface that can be
 * shared by multiple threads, usually the I/O dispatch of an I/O reactor and
 * a worker thread.
 * <p>
 * The I/O dispatch thread is expected to transfer data from the buffer to
 *   {@link ContentEncoder} by calling {@link #produceContent(ContentEncoder)}.
 * <p>
 * The worker thread is expected to write data to the buffer by calling
 * {@link #write(int)}, {@link #write(byte[], int, int)} or {@link #writeCompleted()}
 * <p>
 * In case of an abnormal situation or when no longer needed the buffer must be
 * shut down using {@link #shutdown()} method.
 *
 * @since 4.0
 */
@ThreadSafe
public class SharedOutputBuffer extends ExpandableBuffer implements ContentOutputBuffer {

    private final ReentrantLock lock;
    private final Condition condition;

    private volatile IOControl ioctrl;
    private volatile boolean shutdown = false;
    private volatile boolean endOfStream = false;

    /**
     * @deprecated (4.3) use {@link SharedOutputBuffer#SharedOutputBuffer(int, ByteBufferAllocator)}
     */
    @Deprecated
    public SharedOutputBuffer(final int buffersize, final IOControl ioctrl, final ByteBufferAllocator allocator) {
        super(buffersize, allocator);
        Args.notNull(ioctrl, "I/O content control");
        this.ioctrl = ioctrl;
        this.lock = new ReentrantLock();
        this.condition = this.lock.newCondition();
    }

    /**
     * @since 4.3
     */
    public SharedOutputBuffer(final int buffersize, final ByteBufferAllocator allocator) {
        super(buffersize, allocator);
        this.lock = new ReentrantLock();
        this.condition = this.lock.newCondition();
    }

    /**
     * @since 4.3
     */
    public SharedOutputBuffer(final int buffersize) {
        this(buffersize, HeapByteBufferAllocator.INSTANCE);
    }

    @Override
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

    /**
     * @deprecated (4.3) use {@link #produceContent(ContentEncoder, IOControl)}
     */
    @Override
    @Deprecated
    public int produceContent(final ContentEncoder encoder) throws IOException {
        return produceContent(encoder, null);
    }

    /**
     * @since 4.3
     */
    public int produceContent(final ContentEncoder encoder, final IOControl ioctrl) throws IOException {
        if (this.shutdown) {
            return -1;
        }
        this.lock.lock();
        try {
            if (ioctrl != null) {
                this.ioctrl = ioctrl;
            }
            setOutputMode();
            int bytesWritten = 0;
            if (super.hasData()) {
                bytesWritten = encoder.write(this.buffer);
                if (encoder.isCompleted()) {
                    this.endOfStream = true;
                }
            }
            if (!super.hasData()) {
                // No more buffered content
                // If at the end of the stream, terminate
                if (this.endOfStream && !encoder.isCompleted()) {
                    encoder.complete();
                }
                if (!this.endOfStream) {
                    // suspend output events
                    if (this.ioctrl != null) {
                        this.ioctrl.suspendOutput();
                    }
                }
            }
            this.condition.signalAll();
            return bytesWritten;
        } finally {
            this.lock.unlock();
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
        this.lock.lock();
        try {
            this.condition.signalAll();
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        if (b == null) {
            return;
        }
        int pos = off;
        this.lock.lock();
        try {
            Asserts.check(!this.shutdown && !this.endOfStream, "Buffer already closed for writing");
            setInputMode();
            int remaining = len;
            while (remaining > 0) {
                if (!this.buffer.hasRemaining()) {
                    flushContent();
                    setInputMode();
                }
                final int chunk = Math.min(remaining, this.buffer.remaining());
                this.buffer.put(b, pos, chunk);
                remaining -= chunk;
                pos += chunk;
            }
        } finally {
            this.lock.unlock();
        }
    }

    public void write(final byte[] b) throws IOException {
        if (b == null) {
            return;
        }
        write(b, 0, b.length);
    }

    @Override
    public void write(final int b) throws IOException {
        this.lock.lock();
        try {
            Asserts.check(!this.shutdown && !this.endOfStream, "Buffer already closed for writing");
            setInputMode();
            if (!this.buffer.hasRemaining()) {
                flushContent();
                setInputMode();
            }
            this.buffer.put((byte)b);
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public void flush() throws IOException {
    }

    private void flushContent() throws IOException {
        this.lock.lock();
        try {
            try {
                while (super.hasData()) {
                    if (this.shutdown) {
                        throw new InterruptedIOException("Output operation aborted");
                    }
                    if (this.ioctrl != null) {
                        this.ioctrl.requestOutput();
                    }
                    this.condition.await();
                }
            } catch (final InterruptedException ex) {
                throw new IOException("Interrupted while flushing the content buffer");
            }
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public void writeCompleted() throws IOException {
        this.lock.lock();
        try {
            if (this.endOfStream) {
                return;
            }
            this.endOfStream = true;
            if (this.ioctrl != null) {
                this.ioctrl.requestOutput();
            }
        } finally {
            this.lock.unlock();
        }
    }

}

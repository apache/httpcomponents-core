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
package org.apache.hc.core5.http.nio.support.classic;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.nio.CapacityChannel;

/**
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
public final class SharedInputBuffer extends AbstractSharedBuffer implements ContentInputBuffer {

    private volatile CapacityChannel capacityChannel;

    public SharedInputBuffer(final ReentrantLock lock, final int initialBufferSize) {
        super(lock, initialBufferSize);
    }

    public SharedInputBuffer(final int bufferSize) {
        super(new ReentrantLock(), bufferSize);
    }

    public int fill(final ByteBuffer src) {
        lock.lock();
        try {
            setInputMode();
            ensureAdjustedCapacity(buffer().position() + src.remaining());
            buffer().put(src);
            final int remaining = buffer().remaining();
            condition.signalAll();
            return remaining;
        } finally {
            lock.unlock();
        }
    }

    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        lock.lock();
        try {
            this.capacityChannel = capacityChannel;
            setInputMode();
            if (buffer().hasRemaining()) {
                capacityChannel.update(buffer().remaining());
            }
        } finally {
            lock.unlock();
        }
    }

    private void awaitInput() throws InterruptedIOException {
        if (!buffer().hasRemaining()) {
            setInputMode();
            while (buffer().position() == 0 && !endStream && !aborted) {
                try {
                    condition.await();
                } catch (final InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException(ex.getMessage());
                }
            }
            setOutputMode();
        }
    }

    @Override
    public int read() throws IOException {
        lock.lock();
        try {
            setOutputMode();
            awaitInput();
            if (aborted) {
                return -1;
            }
            if (!buffer().hasRemaining() && endStream) {
                return -1;
            }
            final int b = buffer().get() & 0xff;
            if (!buffer().hasRemaining() && capacityChannel != null) {
                setInputMode();
                if (buffer().hasRemaining()) {
                    capacityChannel.update(buffer().remaining());
                }
            }
            return b;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        lock.lock();
        try {
            setOutputMode();
            awaitInput();
            if (aborted) {
                return -1;
            }
            if (!buffer().hasRemaining() && endStream) {
                return -1;
            }
            final int chunk = Math.min(buffer().remaining(), len);
            buffer().get(b, off, chunk);
            if (!buffer().hasRemaining() && capacityChannel != null) {
                setInputMode();
                if (buffer().hasRemaining()) {
                    capacityChannel.update(buffer().remaining());
                }
            }
            return chunk;
        } finally {
            lock.unlock();
        }
    }

    public void markEndStream() {
        if (endStream) {
            return;
        }
        lock.lock();
        try {
            if (!endStream) {
                endStream = true;
                capacityChannel = null;
                condition.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

}

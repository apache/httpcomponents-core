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
package org.apache.hc.core5.reactive;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpStreamResetException;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.util.Args;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * An asynchronous data consumer that supports Reactive Streams.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
final class ReactiveDataConsumer implements AsyncDataConsumer, Publisher<ByteBuffer> {

    static final int MAX_BUFFER = 1024 * 1024;

    private final AtomicLong requests = new AtomicLong(0);
    private final AtomicInteger remainingBufferSpace = new AtomicInteger(MAX_BUFFER);

    private final BlockingQueue<ByteBuffer> buffers = new LinkedBlockingQueue<>();
    private final AtomicBoolean flushInProgress = new AtomicBoolean(false);
    private volatile boolean cancelled = false;
    private volatile boolean completed = false;
    private volatile Exception exception;
    private volatile CapacityChannel capacityChannel;
    private volatile Subscriber<? super ByteBuffer> subscriber;

    public void failed(final Exception cause) {
        exception = cause;
        flushToSubscriber();
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        throwIfCancelled();
        this.capacityChannel = capacityChannel;
    }

    private void throwIfCancelled() throws IOException {
        if (cancelled) {
            throw new HttpStreamResetException("Downstream subscriber to ReactiveDataConsumer cancelled");
        }
    }

    @Override
    public int consume(final ByteBuffer byteBuffer) throws IOException {
        if (completed) {
            throw new IllegalStateException("Received data past end of stream");
        }
        throwIfCancelled();

        final byte[] copy = new byte[byteBuffer.remaining()];
        byteBuffer.get(copy);
        remainingBufferSpace.addAndGet(-copy.length);
        buffers.add(ByteBuffer.wrap(copy));

        flushToSubscriber();
        return remainingBufferSpace.get();
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) {
        completed = true;
        flushToSubscriber();
    }

    @Override
    public void releaseResources() {
        this.capacityChannel = null;
    }

    private void flushToSubscriber() {
        final Subscriber<? super ByteBuffer> s = subscriber;
        if (flushInProgress.getAndSet(true)) {
            return;
        }
        try {
            if (s == null) {
                return;
            }
            if (exception != null) {
                subscriber = null;
                s.onError(exception);
                return;
            }
            int windowScalingIncrement = 0;
            ByteBuffer next;
            while (requests.get() > 0 && ((next = buffers.poll()) != null)) {
                final int bytesFreed = next.remaining();
                remainingBufferSpace.addAndGet(bytesFreed);
                s.onNext(next);
                requests.decrementAndGet();
                windowScalingIncrement += bytesFreed;
            }
            if (capacityChannel != null && windowScalingIncrement > 0) {
                try {
                    capacityChannel.update(windowScalingIncrement);
                } catch (final IOException ex) {
                    failed(ex);
                    return;
                }
            }
            if (completed && buffers.isEmpty()) {
                subscriber = null;
                s.onComplete();
            }
        } finally {
            flushInProgress.set(false);
        }
    }

    @Override
    public void subscribe(final Subscriber<? super ByteBuffer> subscriber) {
        this.subscriber = Args.notNull(subscriber, "subscriber");
        subscriber.onSubscribe(new Subscription() {
            @Override
            public void request(final long increment) {
                if (increment <= 0) {
                    failed(new IllegalArgumentException("The number of elements requested must be strictly positive"));
                    return;
                }
                requests.addAndGet(increment);
                flushToSubscriber();
            }

            @Override
            public void cancel() {
                ReactiveDataConsumer.this.cancelled = true;
                ReactiveDataConsumer.this.subscriber = null;
            }
        });
    }
}

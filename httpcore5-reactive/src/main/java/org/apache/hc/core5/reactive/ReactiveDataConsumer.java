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

    private final AtomicLong requests = new AtomicLong(0);

    private final BlockingQueue<ByteBuffer> buffers = new LinkedBlockingQueue<>();
    private final AtomicBoolean flushInProgress = new AtomicBoolean(false);
    private final Object flushLock = new Object();
    private final AtomicInteger windowScalingIncrement = new AtomicInteger(0);
    private volatile boolean cancelled;
    private volatile boolean completed;
    private volatile Exception exception;
    private volatile CapacityChannel capacityChannel;
    private volatile Subscriber<? super ByteBuffer> subscriber;

    public void failed(final Exception cause) {
        if (!completed) {
            exception = cause;
            flushToSubscriber();
        }
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        throwIfCancelled();
        this.capacityChannel = capacityChannel;
        signalCapacity(capacityChannel);
    }

    private void signalCapacity(final CapacityChannel channel) throws IOException {
        final int increment = windowScalingIncrement.getAndSet(0);
        if (increment > 0) {
            channel.update(increment);
        }
    }

    private void throwIfCancelled() throws IOException {
        if (cancelled) {
            throw new HttpStreamResetException("Downstream subscriber to ReactiveDataConsumer cancelled");
        }
    }

    @Override
    public void consume(final ByteBuffer byteBuffer) throws IOException {
        if (completed) {
            throw new IllegalStateException("Received data past end of stream");
        }
        throwIfCancelled();

        final byte[] copy = new byte[byteBuffer.remaining()];
        byteBuffer.get(copy);
        buffers.add(ByteBuffer.wrap(copy));

        flushToSubscriber();
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
        synchronized (flushLock) {
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
                ByteBuffer next;
                while (requests.get() > 0 && ((next = buffers.poll()) != null)) {
                    final int bytesFreed = next.remaining();
                    s.onNext(next);
                    requests.decrementAndGet();
                    windowScalingIncrement.addAndGet(bytesFreed);
                }
                final CapacityChannel localChannel = capacityChannel;
                if (localChannel != null) {
                    try {
                        signalCapacity(localChannel);
                    } catch (final IOException e) {
                        exception = e;
                        s.onError(e);
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

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
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpStreamResetException;
import org.apache.hc.core5.http.nio.AsyncDataProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.util.Args;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * An asynchronous data producer that supports Reactive Streams.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
final class ReactiveDataProducer implements AsyncDataProducer, Subscriber<ByteBuffer> {

    private static final int BUFFER_WINDOW_SIZE = 5;

    private final AtomicReference<DataStreamChannel> requestChannel = new AtomicReference<>();
    private final AtomicReference<Throwable> exception = new AtomicReference<>(null);
    private final AtomicBoolean complete = new AtomicBoolean(false);
    private final Publisher<ByteBuffer> publisher;
    private final AtomicReference<Subscription> subscription = new AtomicReference<>(null);
    private final ArrayDeque<ByteBuffer> buffers = new ArrayDeque<>(); // This field requires synchronization

    public ReactiveDataProducer(final Publisher<ByteBuffer> publisher) {
        this.publisher = Args.notNull(publisher, "publisher");
    }

    void setChannel(final DataStreamChannel channel) {
        requestChannel.set(channel);
    }

    @Override
    public void onSubscribe(final Subscription subscription) {
        if (this.subscription.getAndSet(subscription) != null) {
            throw new IllegalStateException("Already subscribed");
        }

        subscription.request(BUFFER_WINDOW_SIZE);
    }

    @Override
    public void onNext(final ByteBuffer byteBuffer) {
        final byte[] copy = new byte[byteBuffer.remaining()];
        byteBuffer.get(copy);
        synchronized (buffers) {
            buffers.add(ByteBuffer.wrap(copy));
        }
        signalReadiness();
    }

    @Override
    public void onError(final Throwable throwable) {
        subscription.set(null);
        exception.set(throwable);
        signalReadiness();
    }

    @Override
    public void onComplete() {
        subscription.set(null);
        complete.set(true);
        signalReadiness();
    }

    private void signalReadiness() {
        final DataStreamChannel channel = requestChannel.get();
        if (channel == null) {
            throw new IllegalStateException("Output channel is not set");
        }
        channel.requestOutput();
    }

    @Override
    public int available() {
        if (exception.get() != null || complete.get()) {
            return 1;
        } else {
            synchronized (buffers) {
                int sum = 0;
                for (final ByteBuffer buffer : buffers) {
                    sum += buffer.remaining();
                }
                return sum;
            }
        }
    }

    @Override
    public void produce(final DataStreamChannel channel) throws IOException {
        if (requestChannel.get() == null) {
            requestChannel.set(channel);
            publisher.subscribe(this);
        }

        final Throwable t = exception.get();
        final Subscription s = subscription.get();
        int buffersToReplenish = 0;
        try {
            synchronized (buffers) {
                if (t != null) {
                    throw new HttpStreamResetException(t.getMessage(), t);
                } else if (this.complete.get() && buffers.isEmpty()) {
                    channel.endStream();
                } else {
                    while (!buffers.isEmpty()) {
                        final ByteBuffer nextBuffer = buffers.remove();
                        channel.write(nextBuffer);
                        if (nextBuffer.remaining() > 0) {
                            buffers.push(nextBuffer);
                            break;
                        } else if (s != null) {
                            // We defer the #request call until after we release the buffer lock.
                            buffersToReplenish++;
                        }
                    }
                }
            }
        } finally {
            if (s != null && buffersToReplenish > 0) {
                s.request(buffersToReplenish);
            }
        }
    }

    @Override
    public void releaseResources() {
        final Subscription s = subscription.getAndSet(null);
        if (s != null) {
            s.cancel();
        }
    }
}

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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Notification;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import org.apache.hc.core5.http.HttpStreamResetException;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

class TestReactiveDataConsumer {

    @Test
    void testStreamThatEndsNormally() throws Exception {
        final ReactiveDataConsumer consumer = new ReactiveDataConsumer();

        final List<ByteBuffer> output = Collections.synchronizedList(new ArrayList<>());

        final CountDownLatch complete = new CountDownLatch(1);
        Observable.fromPublisher(consumer)
            .materialize()
            .forEach(byteBufferNotification -> {
                if (byteBufferNotification.isOnComplete()) {
                    complete.countDown();
                } else if (byteBufferNotification.isOnNext()) {
                    output.add(byteBufferNotification.getValue());
                } else {
                    throw new IllegalArgumentException();
                }
            });

        consumer.consume(ByteBuffer.wrap(new byte[]{ '1' }));
        consumer.consume(ByteBuffer.wrap(new byte[]{ '2' }));
        consumer.consume(ByteBuffer.wrap(new byte[]{ '3' }));
        consumer.streamEnd(null);

        Assertions.assertTrue(complete.await(1, TimeUnit.SECONDS), "Stream did not finish before timeout");
        Assertions.assertEquals(3, output.size());
        Assertions.assertEquals(ByteBuffer.wrap(new byte[]{ '1' }), output.get(0));
        Assertions.assertEquals(ByteBuffer.wrap(new byte[]{ '2' }), output.get(1));
        Assertions.assertEquals(ByteBuffer.wrap(new byte[]{ '3' }), output.get(2));
    }

    @Test
    void testStreamThatEndsWithError() {
        final ReactiveDataConsumer consumer = new ReactiveDataConsumer();
        final Single<List<Notification<ByteBuffer>>> single = Observable.fromPublisher(consumer)
            .materialize()
            .toList();

        final Exception ex = new RuntimeException();
        consumer.failed(ex);

        Assertions.assertSame(ex, single.blockingGet().get(0).getError());
    }

    @Test
    void testCancellation() {
        final ReactiveDataConsumer consumer = new ReactiveDataConsumer();
        consumer.subscribe(new Subscriber<ByteBuffer>() {
            @Override
            public void onSubscribe(final Subscription s) {
                s.cancel();
            }

            @Override
            public void onNext(final ByteBuffer byteBuffer) {
            }

            @Override
            public void onError(final Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });

        Assertions.assertThrows(HttpStreamResetException.class, () ->
            consumer.consume(ByteBuffer.wrap(new byte[1024])));
    }

    @Test
    void testCapacityIncrements() throws Exception {
        final ReactiveDataConsumer consumer = new ReactiveDataConsumer();
        final ByteBuffer data = ByteBuffer.wrap(new byte[1024]);

        final AtomicInteger lastIncrement = new AtomicInteger(-1);
        final CapacityChannel channel = lastIncrement::set;
        consumer.updateCapacity(channel);
        Assertions.assertEquals(-1, lastIncrement.get(), "CapacityChannel#update should not have been invoked yet");

        final AtomicInteger received = new AtomicInteger(0);
        final AtomicReference<Subscription> subscription = new AtomicReference<>();
        consumer.subscribe(new Subscriber<ByteBuffer>() {
            @Override
            public void onSubscribe(final Subscription s) {
                subscription.set(s);
            }

            @Override
            public void onNext(final ByteBuffer byteBuffer) {
                received.incrementAndGet();
            }

            @Override
            public void onError(final Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });

        consumer.consume(data.duplicate());
        consumer.consume(data.duplicate());
        consumer.consume(data.duplicate());
        consumer.consume(data.duplicate());

        subscription.get().request(1);
        Assertions.assertEquals(1024, lastIncrement.get());

        subscription.get().request(2);
        Assertions.assertEquals(2 * 1024, lastIncrement.get());

        subscription.get().request(99);
        Assertions.assertEquals(1024, lastIncrement.get());
    }

    @Test
    void testFullResponseBuffering() throws Exception {
        // Due to inherent race conditions, is possible for the entire response to be buffered and completed before
        // the Subscriber shows up. This must be handled correctly.
        final ReactiveDataConsumer consumer = new ReactiveDataConsumer();
        final ByteBuffer data = ByteBuffer.wrap(new byte[1024]);

        consumer.consume(data.duplicate());
        consumer.consume(data.duplicate());
        consumer.consume(data.duplicate());
        consumer.streamEnd(null);

        Assertions.assertEquals(3L, Flowable.fromPublisher(consumer).count().blockingGet().longValue());
    }

    @Test
    void testErrorBuffering() throws Exception {
        final ReactiveDataConsumer consumer = new ReactiveDataConsumer();
        final ByteBuffer data = ByteBuffer.wrap(new byte[1024]);

        final RuntimeException ex = new RuntimeException();
        consumer.consume(data.duplicate());
        consumer.consume(data.duplicate());
        consumer.consume(data.duplicate());
        consumer.failed(ex);

        final Notification<ByteBuffer> result = Flowable.fromPublisher(consumer)
            .materialize()
            .singleOrError()
            .blockingGet();
        Assertions.assertSame(ex, result.getError());
    }

    @Test
    void testFailAfterCompletion() {
        // Calling consumer.failed() after consumer.streamEnd() must be a no-op.
        // The exception must be discarded, and the subscriber must see that
        // the stream was successfully completed.
        final ReactiveDataConsumer consumer = new ReactiveDataConsumer();

        consumer.streamEnd(null);

        final RuntimeException ex = new RuntimeException();
        consumer.failed(ex);

        final Notification<ByteBuffer> result = Flowable.fromPublisher(consumer)
                .materialize()
                .singleOrError()
                .blockingGet();
        Assertions.assertFalse(result.isOnError());
        Assertions.assertTrue(result.isOnComplete());
    }
}

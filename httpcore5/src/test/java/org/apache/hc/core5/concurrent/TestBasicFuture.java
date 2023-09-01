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
package org.apache.hc.core5.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.util.TimeoutValueException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class TestBasicFuture {

    @Test
    public void testCompleted() throws Exception {
        final FutureCallback<Object> callback = Mockito.mock(FutureCallback.class);
        final BasicFuture<Object> future = new BasicFuture<>(callback);

        Assertions.assertFalse(future.isDone());

        final Object result = new Object();
        final Exception boom = new Exception();
        future.completed(result);
        future.failed(boom);
        Mockito.verify(callback).completed(result);
        Mockito.verify(callback, Mockito.never()).failed(Mockito.any());
        Mockito.verify(callback, Mockito.never()).cancelled();

        Assertions.assertSame(result, future.get());
        Assertions.assertTrue(future.isDone());
        Assertions.assertFalse(future.isCancelled());

    }

    @Test
    public void testCompletedWithTimeout() throws Exception {
        final FutureCallback<Object> callback = Mockito.mock(FutureCallback.class);
        final BasicFuture<Object> future = new BasicFuture<>(callback);

        Assertions.assertFalse(future.isDone());

        final Object result = new Object();
        final Exception boom = new Exception();
        future.completed(result);
        future.failed(boom);
        Mockito.verify(callback).completed(result);
        Mockito.verify(callback, Mockito.never()).failed(Mockito.any());
        Mockito.verify(callback, Mockito.never()).cancelled();

        Assertions.assertSame(result, future.get(1, TimeUnit.MILLISECONDS));
        Assertions.assertTrue(future.isDone());
        Assertions.assertFalse(future.isCancelled());
    }

    @Test
    public void testFailed() throws Exception {
        final FutureCallback<Object> callback = Mockito.mock(FutureCallback.class);
        final BasicFuture<Object> future = new BasicFuture<>(callback);
        final Object result = new Object();
        final Exception boom = new Exception();
        future.failed(boom);
        future.completed(result);
        Mockito.verify(callback, Mockito.never()).completed(Mockito.any());
        Mockito.verify(callback).failed(boom);
        Mockito.verify(callback, Mockito.never()).cancelled();

        try {
            future.get();
        } catch (final ExecutionException ex) {
            Assertions.assertSame(boom, ex.getCause());
        }
        Assertions.assertTrue(future.isDone());
        Assertions.assertFalse(future.isCancelled());
    }

    @Test
    public void testCancelled() throws Exception {
        final FutureCallback<Object> callback = Mockito.mock(FutureCallback.class);
        final BasicFuture<Object> future = new BasicFuture<>(callback);
        final Object result = new Object();
        final Exception boom = new Exception();
        future.cancel(true);
        future.failed(boom);
        future.completed(result);
        Mockito.verify(callback, Mockito.never()).completed(Mockito.any());
        Mockito.verify(callback, Mockito.never()).failed(Mockito.any());
        Mockito.verify(callback).cancelled();

        assertThrows(CancellationException.class, future::get);
        Assertions.assertTrue(future.isDone());
        Assertions.assertTrue(future.isCancelled());
    }

    @Test
    public void testAsyncCompleted() throws Exception {
        final BasicFuture<Object> future = new BasicFuture<>(null);
        final Object result = new Object();

        final Thread t = new Thread(() -> {
            try {
                Thread.sleep(100);
                future.completed(result);
            } catch (final InterruptedException boom) {
            }
        });
        t.setDaemon(true);
        t.start();
        Assertions.assertSame(result, future.get(60, TimeUnit.SECONDS));
        Assertions.assertTrue(future.isDone());
        Assertions.assertFalse(future.isCancelled());
    }

    @Test
    public void testAsyncFailed() throws Exception {
        final BasicFuture<Object> future = new BasicFuture<>(null);
        final Exception boom = new Exception();

        final Thread t = new Thread(() -> {
            try {
                Thread.sleep(100);
                future.failed(boom);
            } catch (final InterruptedException ex) {
            }
        });
        t.setDaemon(true);
        t.start();
        try {
            future.get(60, TimeUnit.SECONDS);
        } catch (final ExecutionException ex) {
            Assertions.assertSame(boom, ex.getCause());
        }
        Assertions.assertTrue(future.isDone());
        Assertions.assertFalse(future.isCancelled());
    }

    @Test
    public void testAsyncCancelled() throws Exception {
        final BasicFuture<Object> future = new BasicFuture<>(null);

        final Thread t = new Thread(() -> {
            try {
                Thread.sleep(100);
                future.cancel(true);
            } catch (final InterruptedException ex) {
            }
        });
        t.setDaemon(true);
        t.start();
        assertThrows(CancellationException.class, () ->
                future.get(60, TimeUnit.SECONDS));
    }

    @Test
    public void testAsyncTimeout() throws Exception {
        final BasicFuture<Object> future = new BasicFuture<>(null);
        final Object result = new Object();

        final Thread t = new Thread(() -> {
            try {
                Thread.sleep(200);
                future.completed(result);
            } catch (final InterruptedException ex) {
            }
        });
        t.setDaemon(true);
        t.start();
        assertThrows(TimeoutException.class, () ->
                future.get(1, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testAsyncNegativeTimeout() throws Exception {
        final BasicFuture<Object> future = new BasicFuture<>(null);
        assertThrows(TimeoutValueException.class, () ->
                future.get(-1, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testConcurrentOperations() throws InterruptedException, ExecutionException {
        final FutureCallback<Object> callback = new FutureCallback<Object>() {
            public void completed(final Object result) {
            }

            public void failed(final Exception ex) {
            }

            public void cancelled() {
            }
        };

        final ExecutorService executor = Executors.newFixedThreadPool(3);
        final BasicFuture<Object> future = new BasicFuture<>(callback);
        final Object expectedResult = new Object();

        final AtomicBoolean completedSuccessfully = new AtomicBoolean(false);
        final AtomicBoolean failedSuccessfully = new AtomicBoolean(false);
        final AtomicBoolean cancelledSuccessfully = new AtomicBoolean(false);

        // Run 3 tasks concurrently: complete, fail, and cancel the future.
        final Future<?> future1 = executor.submit(() -> completedSuccessfully.set(future.completed(expectedResult)));
        final Future<?> future2 = executor.submit(() -> failedSuccessfully.set(future.failed(new Exception("Test Exception"))));
        final Future<?> future3 = executor.submit(() -> cancelledSuccessfully.set(future.cancel()));

        // Wait for the tasks to finish.
        future1.get();
        future2.get();
        future3.get();

        // Verify that the first operation won and the other two failed.
        if (completedSuccessfully.get()) {
            assertEquals(expectedResult, future.get());
        } else if (failedSuccessfully.get()) {
            assertThrows(ExecutionException.class, future::get);
        } else if (cancelledSuccessfully.get()) {
            assertThrows(CancellationException.class, future::get);
        } else {
            fail("No operation was successful on the future.");
        }

        // Shutdown the executor.
        executor.shutdown();
    }

    @Test
    void testGetWithTimeout() {
        final AtomicBoolean isFutureCompleted = new AtomicBoolean(false);

        final FutureCallback<String> callback = new FutureCallback<String>() {
            @Override
            public void completed(final String result) {
                isFutureCompleted.set(true);
            }

            @Override
            public void failed(final Exception ex) {
                // Nothing to do here for this example
            }

            @Override
            public void cancelled() {
                // Nothing to do here for this example
            }
        };

        final BasicFuture<String> future = new BasicFuture<>(callback);

        new Thread(() -> future.completed("test")).start();

        // Poll until the future is completed or timeout
        assertTimeoutPreemptively(Duration.ofMillis(200), () -> {
            while (!isFutureCompleted.get()) {
                // This loop will spin until the future is completed or the assertTimeoutPreemptively times out.
                Thread.yield();
            }

            try {
                assertEquals("test", future.get(1, TimeUnit.SECONDS));
            } catch (final ExecutionException | TimeoutException e) {
                fail("Test failed due to exception: " + e.getMessage());
            }
        });
    }
}

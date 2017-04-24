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
package org.apache.http.concurrent;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Assert;
import org.junit.Test;

public class TestBasicFuture {

    @Test
    public void testCompleted() throws Exception {
        final BasicFutureCallback<Object> callback = new BasicFutureCallback<Object>();
        final BasicFuture<Object> future = new BasicFuture<Object>(callback);

        Assert.assertFalse(future.isDone());

        final Object result = new Object();
        final Exception boom = new Exception();
        future.completed(result);
        future.failed(boom);
        Assert.assertTrue(callback.isCompleted());
        Assert.assertSame(result, callback.getResult());
        Assert.assertFalse(callback.isFailed());
        Assert.assertNull(callback.getException());
        Assert.assertFalse(callback.isCancelled());

        Assert.assertSame(result, future.get());
        Assert.assertTrue(future.isDone());
        Assert.assertFalse(future.isCancelled());

    }

    @Test
    public void testCompletedWithTimeout() throws Exception {
        final BasicFutureCallback<Object> callback = new BasicFutureCallback<Object>();
        final BasicFuture<Object> future = new BasicFuture<Object>(callback);

        Assert.assertFalse(future.isDone());

        final Object result = new Object();
        final Exception boom = new Exception();
        future.completed(result);
        future.failed(boom);
        Assert.assertTrue(callback.isCompleted());
        Assert.assertSame(result, callback.getResult());
        Assert.assertFalse(callback.isFailed());
        Assert.assertNull(callback.getException());
        Assert.assertFalse(callback.isCancelled());

        Assert.assertSame(result, future.get(1, TimeUnit.MILLISECONDS));
        Assert.assertTrue(future.isDone());
        Assert.assertFalse(future.isCancelled());
    }

    @Test
    public void testFailed() throws Exception {
        final BasicFutureCallback<Object> callback = new BasicFutureCallback<Object>();
        final BasicFuture<Object> future = new BasicFuture<Object>(callback);
        final Object result = new Object();
        final Exception boom = new Exception();
        future.failed(boom);
        future.completed(result);
        Assert.assertFalse(callback.isCompleted());
        Assert.assertNull(callback.getResult());
        Assert.assertTrue(callback.isFailed());
        Assert.assertSame(boom, callback.getException());
        Assert.assertFalse(callback.isCancelled());

        try {
            future.get();
        } catch (final ExecutionException ex) {
            Assert.assertSame(boom, ex.getCause());
        }
        Assert.assertTrue(future.isDone());
        Assert.assertFalse(future.isCancelled());
    }

    @Test
    public void testCancelled() throws Exception {
        final BasicFutureCallback<Object> callback = new BasicFutureCallback<Object>();
        final BasicFuture<Object> future = new BasicFuture<Object>(callback);
        final Object result = new Object();
        final Exception boom = new Exception();
        future.cancel(true);
        future.failed(boom);
        future.completed(result);
        Assert.assertFalse(callback.isCompleted());
        Assert.assertNull(callback.getResult());
        Assert.assertFalse(callback.isFailed());
        Assert.assertNull(callback.getException());
        Assert.assertTrue(callback.isCancelled());

        try {
            future.get();
            Assert.fail("CancellationException expected");
        } catch (final CancellationException ex) {
        }
        Assert.assertTrue(future.isDone());
        Assert.assertTrue(future.isCancelled());
    }

    @Test
    public void testAsyncCompleted() throws Exception {
        final BasicFuture<Object> future = new BasicFuture<Object>(null);
        final Object result = new Object();

        final Thread t = new Thread() {

            @Override
            public void run() {
                try {
                    Thread.sleep(100);
                    future.completed(result);
                } catch (final InterruptedException boom) {
                }
            }

        };
        t.setDaemon(true);
        t.start();
        Assert.assertSame(result, future.get(60, TimeUnit.SECONDS));
        Assert.assertTrue(future.isDone());
        Assert.assertFalse(future.isCancelled());
    }

    @Test
    public void testAsyncFailed() throws Exception {
        final BasicFuture<Object> future = new BasicFuture<Object>(null);
        final Exception boom = new Exception();

        final Thread t = new Thread() {

            @Override
            public void run() {
                try {
                    Thread.sleep(100);
                    future.failed(boom);
                } catch (final InterruptedException ex) {
                }
            }

        };
        t.setDaemon(true);
        t.start();
        try {
            future.get(60, TimeUnit.SECONDS);
        } catch (final ExecutionException ex) {
            Assert.assertSame(boom, ex.getCause());
        }
        Assert.assertTrue(future.isDone());
        Assert.assertFalse(future.isCancelled());
    }

    @Test(expected = CancellationException.class)
    public void testAsyncCancelled() throws Exception {
        final BasicFuture<Object> future = new BasicFuture<Object>(null);

        final Thread t = new Thread() {

            @Override
            public void run() {
                try {
                    Thread.sleep(100);
                    future.cancel(true);
                } catch (final InterruptedException ex) {
                }
            }

        };
        t.setDaemon(true);
        t.start();
        future.get(60, TimeUnit.SECONDS);
    }

    @Test(expected=TimeoutException.class)
    public void testAsyncTimeout() throws Exception {
        final BasicFuture<Object> future = new BasicFuture<Object>(null);
        final Object result = new Object();

        final Thread t = new Thread() {

            @Override
            public void run() {
                try {
                    Thread.sleep(200);
                    future.completed(result);
                } catch (final InterruptedException ex) {
                }
            }

        };
        t.setDaemon(true);
        t.start();
        future.get(1, TimeUnit.MILLISECONDS);
    }

    @Test(expected=TimeoutException.class)
    public void testAsyncNegativeTimeout() throws Exception {
        final BasicFuture<Object> future = new BasicFuture<Object>(null);
        future.get(-1, TimeUnit.MILLISECONDS);
    }

}

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
package org.apache.hc.core5.pool;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Deadline;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestPoolEntry {

    private AtomicLong count;
    private Supplier<Long> currentTimeSupplier;

    @BeforeEach
    void setup() {
        count = new AtomicLong(1);
        currentTimeSupplier = () -> count.addAndGet(1);
    }

    @Test
    void testBasics() {
        final PoolEntry<String, HttpConnection> entry1 = new PoolEntry<>(
                "route1", TimeValue.of(10L, TimeUnit.MILLISECONDS), currentTimeSupplier);

        Assertions.assertEquals("route1", entry1.getRoute());
        Assertions.assertEquals(0, entry1.getUpdated());
        Assertions.assertEquals(Deadline.MIN_VALUE, entry1.getExpiryDeadline());

        entry1.assignConnection(Mockito.mock(HttpConnection.class));
        final long now = System.currentTimeMillis();
        Assertions.assertEquals("route1", entry1.getRoute());
        Assertions.assertTrue(now >= entry1.getUpdated());
        Assertions.assertEquals(entry1.getValidityDeadline(), entry1.getExpiryDeadline());
        Assertions.assertEquals(entry1.getUpdated() + 10L, entry1.getValidityDeadline().getValue());

        entry1.discardConnection(CloseMode.IMMEDIATE);
        Assertions.assertEquals(0, entry1.getUpdated());
        Assertions.assertEquals(Deadline.MIN_VALUE, entry1.getExpiryDeadline());
    }

    @Test
    void testNullConstructor() {
        Assertions.assertThrows(NullPointerException.class, () ->
                new PoolEntry<String, HttpConnection>(null));
    }

    @Test
    void testValidInfinitely() {
        final PoolEntry<String, HttpConnection> entry1 = new PoolEntry<>(
                "route1", TimeValue.ZERO_MILLISECONDS, currentTimeSupplier);
        entry1.assignConnection(Mockito.mock(HttpConnection.class));
        Assertions.assertEquals(Deadline.MAX_VALUE, entry1.getValidityDeadline());
        Assertions.assertEquals(entry1.getValidityDeadline(), entry1.getExpiryDeadline());
    }

    @Test
    void testExpiry() {
        final PoolEntry<String, HttpConnection> entry1 = new PoolEntry<>(
                "route1", TimeValue.ZERO_MILLISECONDS, currentTimeSupplier);
        entry1.assignConnection(Mockito.mock(HttpConnection.class));
        Assertions.assertEquals(Deadline.MAX_VALUE, entry1.getExpiryDeadline());
        entry1.updateExpiry(TimeValue.of(50L, TimeUnit.MILLISECONDS));
        Assertions.assertEquals(entry1.getUpdated() + 50L, entry1.getExpiryDeadline().getValue());
        entry1.updateExpiry(TimeValue.ZERO_MILLISECONDS);
        Assertions.assertEquals(Deadline.MAX_VALUE, entry1.getExpiryDeadline());

        final PoolEntry<String, HttpConnection> entry2 = new PoolEntry<>(
                "route1", TimeValue.of(100L, TimeUnit.MILLISECONDS), currentTimeSupplier);
        entry2.assignConnection(Mockito.mock(HttpConnection.class));
        final Deadline validityDeadline = entry2.getValidityDeadline();
        Assertions.assertEquals(entry2.getUpdated() + 100L, entry2.getExpiryDeadline().getValue());
        entry2.updateExpiry(TimeValue.of(50L, TimeUnit.MILLISECONDS));
        Assertions.assertEquals(entry2.getUpdated() + 50L, entry2.getExpiryDeadline().getValue());
        entry2.updateExpiry(TimeValue.of(150L, TimeUnit.MILLISECONDS));
        Assertions.assertEquals(validityDeadline, entry2.getExpiryDeadline());
    }

    @Test
    void testInvalidExpiry() {
        final PoolEntry<String, HttpConnection> entry = new PoolEntry<>(
                "route1", TimeValue.of(0L, TimeUnit.MILLISECONDS), currentTimeSupplier);
        Assertions.assertThrows(NullPointerException.class, () ->
                entry.updateExpiry(null));
    }

    @Test
    void testExpiryDoesNotOverflow() {
        final PoolEntry<String, HttpConnection> entry = new PoolEntry<>(
                "route1", TimeValue.of(Long.MAX_VALUE, TimeUnit.MILLISECONDS), currentTimeSupplier);
        entry.assignConnection(Mockito.mock(HttpConnection.class));
        Assertions.assertEquals(Deadline.MAX_VALUE, entry.getValidityDeadline());
    }

}

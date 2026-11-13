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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Deadline;
import org.apache.hc.core5.util.TimeValue;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestPoolEntry {

    private AtomicLong count;
    private Clock clock;

    @BeforeEach
    void setup() {
        count = new AtomicLong(1);
        clock = new Clock() {

            private final ZoneId zoneId = ZoneId.systemDefault();

            @Override
            public ZoneId getZone() {
                return zoneId;
            }

            @Override
            public Clock withZone(final ZoneId zone) {
                return this;
            }

            @Override
            public long millis() {
                return count.addAndGet(1);
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(millis());
            }

        };
    }

    @Test
    void testBasics() {
        final PoolEntry<String, HttpConnection> entry1 = new PoolEntry<>(
                "route1", TimeValue.of(10L, TimeUnit.MILLISECONDS), clock);

        Assertions.assertThat(entry1.getRoute()).isEqualTo("route1");
        Assertions.assertThat(entry1.getUpdated()).isEqualTo(0);
        Assertions.assertThat(entry1.getExpiryDeadline()).isEqualTo(Deadline.MIN_VALUE);

        entry1.assignConnection(Mockito.mock(HttpConnection.class));
        final long now = System.currentTimeMillis();
        Assertions.assertThat(entry1.getRoute()).isEqualTo("route1");
        Assertions.assertThat(now >= entry1.getUpdated()).isTrue();
        Assertions.assertThat(entry1.getExpiryDeadline()).isEqualTo(entry1.getValidityDeadline());
        Assertions.assertThat(entry1.getValidityDeadline().getValue()).isEqualTo(entry1.getUpdated() + 10L);

        entry1.discardConnection(CloseMode.IMMEDIATE);
        Assertions.assertThat(entry1.getUpdated()).isEqualTo(0);
        Assertions.assertThat(entry1.getExpiryDeadline()).isEqualTo(Deadline.MIN_VALUE);
    }

    @Test
    void testNullConstructor() {
        Assertions.assertThatThrownBy(() ->
                new PoolEntry<String, HttpConnection>(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testValidInfinitely() {
        final PoolEntry<String, HttpConnection> entry1 = new PoolEntry<>(
                "route1", TimeValue.ZERO_MILLISECONDS, clock);
        entry1.assignConnection(Mockito.mock(HttpConnection.class));
        Assertions.assertThat(entry1.getValidityDeadline()).isEqualTo(Deadline.MAX_VALUE);
        Assertions.assertThat(entry1.getExpiryDeadline()).isEqualTo(entry1.getValidityDeadline());
    }

    @Test
    void testExpiry() {
        final PoolEntry<String, HttpConnection> entry1 = new PoolEntry<>(
                "route1", TimeValue.ZERO_MILLISECONDS, clock);
        entry1.assignConnection(Mockito.mock(HttpConnection.class));
        Assertions.assertThat(entry1.getExpiryDeadline()).isEqualTo(Deadline.MAX_VALUE);
        entry1.updateExpiry(TimeValue.of(50L, TimeUnit.MILLISECONDS));
        Assertions.assertThat(entry1.getExpiryDeadline().getValue()).isEqualTo(entry1.getUpdated() + 50L);
        entry1.updateExpiry(TimeValue.ZERO_MILLISECONDS);
        Assertions.assertThat(entry1.getExpiryDeadline()).isEqualTo(Deadline.MAX_VALUE);

        final PoolEntry<String, HttpConnection> entry2 = new PoolEntry<>(
                "route1", TimeValue.of(100L, TimeUnit.MILLISECONDS), clock);
        entry2.assignConnection(Mockito.mock(HttpConnection.class));
        final Deadline validityDeadline = entry2.getValidityDeadline();
        Assertions.assertThat(entry2.getExpiryDeadline().getValue()).isEqualTo(entry2.getUpdated() + 100L);
        entry2.updateExpiry(TimeValue.of(50L, TimeUnit.MILLISECONDS));
        Assertions.assertThat(entry2.getExpiryDeadline().getValue()).isEqualTo(entry2.getUpdated() + 50L);
        entry2.updateExpiry(TimeValue.of(150L, TimeUnit.MILLISECONDS));
        Assertions.assertThat(entry2.getExpiryDeadline()).isEqualTo(validityDeadline);
    }

    @Test
    void testInvalidExpiry() {
        final PoolEntry<String, HttpConnection> entry = new PoolEntry<>(
                "route1", TimeValue.of(0L, TimeUnit.MILLISECONDS), clock);
        Assertions.assertThatThrownBy(() ->
                entry.updateExpiry(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testExpiryDoesNotOverflow() {
        final PoolEntry<String, HttpConnection> entry = new PoolEntry<>(
                "route1", TimeValue.of(Long.MAX_VALUE, TimeUnit.MILLISECONDS), clock);
        entry.assignConnection(Mockito.mock(HttpConnection.class));
        Assertions.assertThat(entry.getValidityDeadline()).isEqualTo(Deadline.MAX_VALUE);
    }

}
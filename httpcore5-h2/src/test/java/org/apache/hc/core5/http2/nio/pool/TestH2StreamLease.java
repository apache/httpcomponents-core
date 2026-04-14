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
package org.apache.hc.core5.http2.nio.pool;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.core5.reactor.IOSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestH2StreamLease {

    @Test
    void testReleaseInvokesAction() {
        final IOSession session = Mockito.mock(IOSession.class);
        final AtomicInteger count = new AtomicInteger();
        final H2StreamLease lease = new H2StreamLease(session, count::incrementAndGet);

        Assertions.assertFalse(lease.isReleased());
        Assertions.assertSame(session, lease.getSession());

        lease.releaseReservation();

        Assertions.assertTrue(lease.isReleased());
        Assertions.assertEquals(1, count.get());
    }

    @Test
    void testReleaseIsIdempotent() {
        final IOSession session = Mockito.mock(IOSession.class);
        final AtomicInteger count = new AtomicInteger();
        final H2StreamLease lease = new H2StreamLease(session, count::incrementAndGet);

        lease.releaseReservation();
        lease.releaseReservation();
        lease.releaseReservation();

        Assertions.assertTrue(lease.isReleased());
        Assertions.assertEquals(1, count.get(), "release action must run at most once");
    }

    @Test
    void testToStringReportsReleasedState() {
        final IOSession session = Mockito.mock(IOSession.class);
        Mockito.when(session.getId()).thenReturn("session-1");
        final H2StreamLease lease = new H2StreamLease(session, () -> {
        });

        Assertions.assertTrue(lease.toString().contains("released: false"));

        lease.releaseReservation();

        Assertions.assertTrue(lease.toString().contains("released: true"));
    }

    @Test
    void testNullSessionRejected() {
        Assertions.assertThrows(NullPointerException.class,
                () -> new H2StreamLease(null, () -> {
                }));
    }

    @Test
    void testNullReleaseActionRejected() {
        final IOSession session = Mockito.mock(IOSession.class);
        Assertions.assertThrows(NullPointerException.class,
                () -> new H2StreamLease(session, null));
    }

}

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
package org.apache.hc.core5.http2.impl.nio.bootstrap;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.StreamControl;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.nio.command.RequestExecutionCommand;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http2.nio.pool.H2StreamLease;
import org.apache.hc.core5.http2.nio.pool.H2StreamLeaseTestFactory;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.TimeValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Unit tests for the requester-side lease lifecycle. These tests exercise
 * {@link H2MultiplexingRequester#dispatchLease} directly so that every
 * terminal path that returns a stream reservation to the pool can be covered
 * without spinning up a real I/O reactor.
 */
class TestH2MultiplexingRequesterLease {

    private static H2MultiplexingRequester newRequester(final int maxCommandsPerConnection) {
        final IOEventHandlerFactory factory = Mockito.mock(IOEventHandlerFactory.class);
        return new H2MultiplexingRequester(
                null, factory, null, null, null, null, null, null, null,
                new AtomicReference<>(TimeValue.NEG_ONE_MILLISECOND),
                maxCommandsPerConnection);
    }

    private static IOSession openSession() {
        final IOSession session = Mockito.mock(IOSession.class);
        Mockito.when(session.isOpen()).thenReturn(true);
        return session;
    }

    private static H2StreamLease leaseFor(final IOSession session, final AtomicInteger releaseCounter) {
        return H2StreamLeaseTestFactory.newLease(session, releaseCounter::incrementAndGet);
    }

    private static RequestExecutionCommand capturedCommand(final IOSession session) {
        final ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
        Mockito.verify(session).enqueue(captor.capture(), Mockito.eq(Command.Priority.NORMAL));
        return (RequestExecutionCommand) captor.getValue();
    }

    private static final class TrackingDependency implements CancellableDependency {

        private final AtomicReference<Cancellable> dependency = new AtomicReference<>();

        @Override
        public void setDependency(final Cancellable cancellable) {
            dependency.set(cancellable);
        }

        @Override
        public boolean cancel() {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        Cancellable getDependency() {
            return dependency.get();
        }
    }

    private static final class TrackingExchangeHandler implements AsyncClientExchangeHandler {

        private final AtomicReference<Exception> failedWith = new AtomicReference<>();
        private final AtomicInteger releaseCount = new AtomicInteger();

        @Override
        public void produceRequest(final RequestChannel channel, final HttpContext context) {
        }

        @Override
        public int available() {
            return 0;
        }

        @Override
        public void produce(final DataStreamChannel channel) {
        }

        @Override
        public void consumeInformation(final HttpResponse response, final HttpContext context) {
        }

        @Override
        public void consumeResponse(final HttpResponse response, final EntityDetails entityDetails,
                                    final HttpContext context) {
        }

        @Override
        public void updateCapacity(final CapacityChannel capacityChannel) {
        }

        @Override
        public void consume(final ByteBuffer src) {
        }

        @Override
        public void streamEnd(final List<? extends Header> trailers) {
        }

        @Override
        public void cancel() {
        }

        @Override
        public void failed(final Exception cause) {
            failedWith.set(cause);
        }

        @Override
        public void releaseResources() {
            releaseCount.incrementAndGet();
        }
    }

    @Test
    void testStreamControlCallbackReleasesLease() throws IOException {
        try (H2MultiplexingRequester requester = newRequester(0)) {
            final IOSession session = openSession();
            final AtomicInteger released = new AtomicInteger();
            final H2StreamLease lease = leaseFor(session, released);

            final TrackingExchangeHandler handler = new TrackingExchangeHandler();
            final TrackingDependency dependency = new TrackingDependency();

            requester.dispatchLease(lease, handler, new BasicHttpRequest("GET", "/"), null, null,
                    dependency, HttpCoreContext.create());

            final RequestExecutionCommand command = capturedCommand(session);
            Assertions.assertFalse(lease.isReleased(), "lease must not be released before stream initiation");
            Assertions.assertEquals(0, released.get());

            final StreamControl streamControl = Mockito.mock(StreamControl.class);
            command.initiated(streamControl);

            Assertions.assertTrue(lease.isReleased(), "stream-control callback must release the lease");
            Assertions.assertEquals(1, released.get());
            Assertions.assertSame(streamControl, dependency.getDependency());
            Assertions.assertNull(handler.failedWith.get());
        }
    }

    @Test
    void testHandlerReleaseResourcesReleasesLease() throws IOException {
        try (H2MultiplexingRequester requester = newRequester(0)) {
            final IOSession session = openSession();
            final AtomicInteger released = new AtomicInteger();
            final H2StreamLease lease = leaseFor(session, released);

            final TrackingExchangeHandler handler = new TrackingExchangeHandler();

            requester.dispatchLease(lease, handler, new BasicHttpRequest("GET", "/"), null, null,
                    new TrackingDependency(), HttpCoreContext.create());

            // Stream is never initiated; the multiplexer drops the command and only
            // calls releaseResources on the proxied handler. The lease must still
            // be released so the pool slot is returned.
            capturedCommand(session).getExchangeHandler().releaseResources();

            Assertions.assertTrue(lease.isReleased(), "releaseResources must release the lease");
            Assertions.assertEquals(1, released.get());
            Assertions.assertEquals(1, handler.releaseCount.get());
        }
    }

    @Test
    void testMaxCommandsRejectionReleasesLease() throws IOException {
        try (H2MultiplexingRequester requester = newRequester(1)) {
            final IOSession session = openSession();
            Mockito.when(session.getPendingCommandCount()).thenReturn(2);

            final AtomicInteger released = new AtomicInteger();
            final H2StreamLease lease = leaseFor(session, released);

            final TrackingExchangeHandler handler = new TrackingExchangeHandler();
            requester.dispatchLease(lease, handler, new BasicHttpRequest("GET", "/"), null, null,
                    new TrackingDependency(), HttpCoreContext.create());

            Assertions.assertTrue(lease.isReleased(), "rejection path must release the lease");
            Assertions.assertEquals(1, released.get());
            Assertions.assertEquals(1, handler.releaseCount.get());
            Assertions.assertTrue(handler.failedWith.get() instanceof RejectedExecutionException);
            Mockito.verify(session, Mockito.never()).enqueue(Mockito.any(Command.class), Mockito.any(Command.Priority.class));
        }
    }

    @Test
    void testEnqueueRuntimeExceptionReleasesLease() throws IOException {
        try (H2MultiplexingRequester requester = newRequester(0)) {
            final IOSession session = openSession();
            final RuntimeException boom = new IllegalStateException("enqueue rejected");
            Mockito.doThrow(boom).when(session).enqueue(Mockito.any(Command.class), Mockito.any(Command.Priority.class));

            final AtomicInteger released = new AtomicInteger();
            final H2StreamLease lease = leaseFor(session, released);

            final TrackingExchangeHandler handler = new TrackingExchangeHandler();
            requester.dispatchLease(lease, handler, new BasicHttpRequest("GET", "/"), null, null,
                    new TrackingDependency(), HttpCoreContext.create());

            Assertions.assertTrue(lease.isReleased(), "enqueue failure must release the lease");
            Assertions.assertEquals(1, released.get());
            Assertions.assertSame(boom, handler.failedWith.get());
            Assertions.assertEquals(1, handler.releaseCount.get());
        }
    }

    @Test
    void testSessionClosedAfterEnqueueReleasesLease() throws IOException {
        try (H2MultiplexingRequester requester = newRequester(0)) {
            final IOSession session = Mockito.mock(IOSession.class);
            final AtomicBoolean open = new AtomicBoolean(true);
            Mockito.when(session.isOpen()).thenAnswer(invocation -> open.get());
            Mockito.doAnswer(invocation -> {
                open.set(false);
                return null;
            }).when(session).enqueue(Mockito.any(Command.class), Mockito.any(Command.Priority.class));

            final AtomicInteger released = new AtomicInteger();
            final H2StreamLease lease = leaseFor(session, released);

            final TrackingExchangeHandler handler = new TrackingExchangeHandler();
            requester.dispatchLease(lease, handler, new BasicHttpRequest("GET", "/"), null, null,
                    new TrackingDependency(), HttpCoreContext.create());

            Assertions.assertTrue(lease.isReleased(), "post-enqueue close must release the lease");
            Assertions.assertEquals(1, released.get());
            Assertions.assertTrue(handler.failedWith.get() instanceof ConnectionClosedException);
        }
    }

    @Test
    void testHandlerProxyForwardsProduceRequest() throws HttpException, IOException {
        try (H2MultiplexingRequester requester = newRequester(0)) {
            final IOSession session = openSession();
            final H2StreamLease lease = H2StreamLeaseTestFactory.newLease(session, () -> {
            });

            final BasicHttpRequest request = new BasicHttpRequest("GET", "/");
            final EntityDetails entityDetails = Mockito.mock(EntityDetails.class);
            final TrackingExchangeHandler handler = new TrackingExchangeHandler();

            requester.dispatchLease(lease, handler, request, entityDetails, null,
                    new TrackingDependency(), HttpCoreContext.create());

            final RequestChannel channel = Mockito.mock(RequestChannel.class);
            final HttpContext context = HttpCoreContext.create();
            capturedCommand(session).getExchangeHandler().produceRequest(channel, context);

            Mockito.verify(channel).sendRequest(Mockito.same(request), Mockito.same(entityDetails), Mockito.same(context));
        }
    }
}

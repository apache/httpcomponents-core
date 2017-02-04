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

package org.apache.hc.core5.testing.nio;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.reactor.DefaultListeningIOReactor;
import org.apache.hc.core5.reactor.IOEventHandler;
import org.apache.hc.core5.reactor.IOEventHandlerFactory;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.IOReactorExceptionHandler;
import org.apache.hc.core5.reactor.IOReactorStatus;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Basic tests for {@link DefaultListeningIOReactor}.
 */
public class TestDefaultListeningIOReactor {

    protected DefaultListeningIOReactor ioreactor;

    @Before
    public void setup() throws Exception {
        final IOReactorConfig reactorConfig = IOReactorConfig.custom()
                .setIoThreadCount(1)
                .build();
        this.ioreactor = new DefaultListeningIOReactor(new IOEventHandlerFactory() {

            @Override
            public IOEventHandler createHandler(final IOSession ioSession, final Object attachment) {
                return new IOEventHandler() {

                    @Override
                    public void connected(final IOSession session) {
                    }

                    @Override
                    public void inputReady(final IOSession session) {
                    }

                    @Override
                    public void outputReady(final IOSession session) {
                    }

                    @Override
                    public void timeout(final IOSession session) {
                    }

                    @Override
                    public void exception(final IOSession session, final Exception cause) {
                    }

                    @Override
                    public void disconnected(final IOSession session) {
                    }
                };
            }
        }, reactorConfig, null);
    }

    @After
    public void cleanup() throws Exception {
        if (this.ioreactor != null) {
            this.ioreactor.shutdown();
        }
    }

    @Test
    public void testEndpointUpAndDown() throws Exception {

        final Thread t = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    ioreactor.execute();
                } catch (final IOException ex) {
                }
            }

        });

        t.start();

        Set<ListenerEndpoint> endpoints = ioreactor.getEndpoints();
        Assert.assertNotNull(endpoints);
        Assert.assertEquals(0, endpoints.size());

        final ListenerEndpoint endpoint1 = ioreactor.listen(new InetSocketAddress(0));
        endpoint1.waitFor();

        final ListenerEndpoint endpoint2 = ioreactor.listen(new InetSocketAddress(0));
        endpoint2.waitFor();
        final int port = ((InetSocketAddress) endpoint2.getAddress()).getPort();

        endpoints = ioreactor.getEndpoints();
        Assert.assertNotNull(endpoints);
        Assert.assertEquals(2, endpoints.size());

        endpoint1.close();

        endpoints = ioreactor.getEndpoints();
        Assert.assertNotNull(endpoints);
        Assert.assertEquals(1, endpoints.size());

        final ListenerEndpoint endpoint = endpoints.iterator().next();

        Assert.assertEquals(port, ((InetSocketAddress) endpoint.getAddress()).getPort());

        ioreactor.shutdown();
        t.join(1000);

        Assert.assertEquals(IOReactorStatus.SHUT_DOWN, ioreactor.getStatus());
    }

    @Test
    public void testEndpointAlreadyBoundFatal() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        final Thread t = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    ioreactor.execute();
                    Assert.fail("IOException should have been thrown");
                } catch (final IOException ex) {
                    latch.countDown();
                }
            }

        });

        t.start();

        final ListenerEndpoint endpoint1 = ioreactor.listen(new InetSocketAddress(0));
        endpoint1.waitFor();
        final int port = ((InetSocketAddress) endpoint1.getAddress()).getPort();

        final ListenerEndpoint endpoint2 = ioreactor.listen(new InetSocketAddress(port));
        endpoint2.waitFor();
        Assert.assertNotNull(endpoint2.getException());

        // I/O reactor is now expected to be shutting down
        latch.await(2000, TimeUnit.MILLISECONDS);
        Assert.assertTrue(ioreactor.getStatus().compareTo(IOReactorStatus.SHUTTING_DOWN) >= 0);

        final Set<ListenerEndpoint> endpoints = ioreactor.getEndpoints();
        Assert.assertNotNull(endpoints);
        Assert.assertEquals(0, endpoints.size());

        ioreactor.shutdown();
        t.join(1000);

        Assert.assertEquals(IOReactorStatus.SHUT_DOWN, ioreactor.getStatus());
    }

    @Test
    public void testEndpointAlreadyBoundNonFatal() throws Exception {
        ioreactor.setExceptionHandler(new IOReactorExceptionHandler() {

            @Override
            public boolean handle(final IOException ex) {
                return (ex instanceof BindException);
            }

            @Override
            public boolean handle(final RuntimeException ex) {
                return false;
            }

        });

        final Thread t = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    ioreactor.execute();
                } catch (final IOException ex) {
                }
            }

        });

        t.start();

        final ListenerEndpoint endpoint1 = ioreactor.listen(new InetSocketAddress(9999));
        endpoint1.waitFor();

        final ListenerEndpoint endpoint2 = ioreactor.listen(new InetSocketAddress(9999));
        endpoint2.waitFor();
        Assert.assertNotNull(endpoint2.getException());

        // Sleep a little to make sure the I/O reactor is not shutting down
        Thread.sleep(500);

        Assert.assertEquals(IOReactorStatus.ACTIVE, ioreactor.getStatus());

        ioreactor.shutdown(1, TimeUnit.SECONDS);
        t.join(1000);

        Assert.assertEquals(IOReactorStatus.SHUT_DOWN, ioreactor.getStatus());
    }

}

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

package org.apache.hc.core5.reactor;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import org.apache.hc.core5.concurrent.DefaultThreadFactory;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;

/**
 * Multi-core I/O reactor that can act as both {@link ConnectionInitiator}
 * and {@link ConnectionAcceptor}. Internally this I/O reactor distributes newly created
 * I/O session equally across multiple I/O worker threads for a more optimal resource
 * utilization and a better I/O performance. Usually it is recommended to have
 * one worker I/O reactor per physical CPU core.
 *
 * @since 4.0
 */
public class DefaultListeningIOReactor extends AbstractIOReactorBase implements ConnectionAcceptor {

    private final static ThreadFactory DISPATCH_THREAD_FACTORY = new DefaultThreadFactory("I/O server dispatch", true);
    private final static ThreadFactory LISTENER_THREAD_FACTORY = new DefaultThreadFactory("I/O listener", true);

    private final int workerCount;
    private final SingleCoreIOReactor[] workers;
    private final SingleCoreListeningIOReactor listener;
    private final MultiCoreIOReactor ioReactor;
    private final IOWorkers.Selector workerSelector;

    /**
     * Creates an instance of DefaultListeningIOReactor with the given configuration.
     *
     * @param eventHandlerFactory the factory to create I/O event handlers.
     * @param ioReactorConfig I/O reactor configuration.
     * @param listenerThreadFactory the factory to create listener thread.
     *   Can be {@code null}.
     *
     * @since 5.0
     */
    public DefaultListeningIOReactor(
            final IOEventHandlerFactory eventHandlerFactory,
            final IOReactorConfig ioReactorConfig,
            final ThreadFactory dispatchThreadFactory,
            final ThreadFactory listenerThreadFactory,
            final Decorator<IOSession> ioSessionDecorator,
            final Callback<Exception> exceptionCallback,
            final IOSessionListener sessionListener,
            final Callback<IOSession> sessionShutdownCallback) {
        Args.notNull(eventHandlerFactory, "Event handler factory");
        this.workerCount = ioReactorConfig != null ? ioReactorConfig.getIoThreadCount() : IOReactorConfig.DEFAULT.getIoThreadCount();
        this.workers = new SingleCoreIOReactor[workerCount];
        final Thread[] threads = new Thread[workerCount + 1];
        for (int i = 0; i < this.workers.length; i++) {
            final SingleCoreIOReactor dispatcher = new SingleCoreIOReactor(
                    exceptionCallback,
                    eventHandlerFactory,
                    ioReactorConfig != null ? ioReactorConfig : IOReactorConfig.DEFAULT,
                    ioSessionDecorator,
                    sessionListener,
                    sessionShutdownCallback);
            this.workers[i] = dispatcher;
            threads[i + 1] = (dispatchThreadFactory != null ? dispatchThreadFactory : DISPATCH_THREAD_FACTORY).newThread(new IOReactorWorker(dispatcher));
        }
        final IOReactor[] ioReactors = new IOReactor[this.workerCount + 1];
        System.arraycopy(this.workers, 0, ioReactors, 1, this.workerCount);
        this.listener = new SingleCoreListeningIOReactor(exceptionCallback, ioReactorConfig, this::enqueueChannel);
        ioReactors[0] = this.listener;
        threads[0] = (listenerThreadFactory != null ? listenerThreadFactory : LISTENER_THREAD_FACTORY).newThread(new IOReactorWorker(listener));

        this.ioReactor = new MultiCoreIOReactor(ioReactors, threads);

        workerSelector = IOWorkers.newSelector(workers);
    }

    /**
     * Creates an instance of DefaultListeningIOReactor with the given configuration.
     *
     * @param eventHandlerFactory the factory to create I/O event handlers.
     * @param config I/O reactor configuration.
     *   Can be {@code null}.
     *
     * @since 5.0
     */
    public DefaultListeningIOReactor(
            final IOEventHandlerFactory eventHandlerFactory,
            final IOReactorConfig config,
            final Callback<IOSession> sessionShutdownCallback) {
        this(eventHandlerFactory, config, null, null, null, null, null, sessionShutdownCallback);
    }

    /**
     * Creates an instance of DefaultListeningIOReactor with default configuration.
     *
     * @param eventHandlerFactory the factory to create I/O event handlers.
     *
     * @since 5.0
     */
    public DefaultListeningIOReactor(final IOEventHandlerFactory eventHandlerFactory) {
        this(eventHandlerFactory, null, null);
    }

    @Override
    public void start() {
        ioReactor.start();
    }

    public Future<ListenerEndpoint> listen(
            final SocketAddress address, final Object attachment, final FutureCallback<ListenerEndpoint> callback) {
        return listener.listen(address, attachment, callback);
    }

    @Override
    public Future<ListenerEndpoint> listen(final SocketAddress address, final FutureCallback<ListenerEndpoint> callback) {
        return listen(address, null, callback);
    }

    public Future<ListenerEndpoint> listen(final SocketAddress address) {
        return listen(address, null);
    }

    @Override
    public Set<ListenerEndpoint> getEndpoints() {
        return listener.getEndpoints();
    }

    @Override
    public void pause() throws IOException {
        listener.pause();
    }

    @Override
    public void resume() throws IOException {
        listener.resume();
    }

    @Override
    public IOReactorStatus getStatus() {
        return ioReactor.getStatus();
    }

    @Override
    IOWorkers.Selector getWorkerSelector() {
        return workerSelector;
    }

    private void enqueueChannel(final ChannelEntry entry) {
        try {
            workerSelector.next().enqueueChannel(entry);
        } catch (final IOReactorShutdownException ex) {
            initiateShutdown();
        }
    }


    @Override
    public void initiateShutdown() {
        ioReactor.initiateShutdown();
    }

    @Override
    public void awaitShutdown(final TimeValue waitTime) throws InterruptedException {
        ioReactor.awaitShutdown(waitTime);
    }

    @Override
    public void close(final CloseMode closeMode) {
        ioReactor.close(closeMode);
    }

    @Override
    public void close() throws IOException {
        ioReactor.close();
    }

}

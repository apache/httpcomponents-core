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

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.ComplexFuture;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.io.GracefullyCloseable;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
public abstract class AbstractIOSessionPool<T> implements GracefullyCloseable {

    private final ConcurrentMap<T, PoolEntry> sessionPool;
    private final AtomicBoolean closed;

    public AbstractIOSessionPool() {
        super();
        this.sessionPool = new ConcurrentHashMap<>();
        this.closed = new AtomicBoolean(false);
    }

    protected abstract Future<IOSession> connectSession(
            T namedEndpoint,
            Timeout requestTimeout,
            FutureCallback<IOSession> callback);

    protected abstract void validateSession(
            IOSession ioSession,
            Callback<Boolean> callback);

    protected abstract void closeSession(
            IOSession ioSession,
            ShutdownType shutdownType);

    @Override
    public final void shutdown(final ShutdownType shutdownType) {
        if (closed.compareAndSet(false, true)) {
            for (final PoolEntry poolEntry : sessionPool.values()) {
                synchronized (poolEntry) {
                    if (poolEntry.session != null) {
                        closeSession(poolEntry.session, shutdownType);
                        poolEntry.session = null;
                    }
                    if (poolEntry.sessionFuture != null) {
                        poolEntry.sessionFuture.cancel(true);
                        poolEntry.sessionFuture = null;
                    }
                    for (;;) {
                        final FutureCallback<IOSession> callback = poolEntry.requestQueue.poll();
                        if (callback != null) {
                            callback.cancelled();
                        } else {
                            break;
                        }
                    }
                }
            }
            sessionPool.clear();
        }
    }

    @Override
    public final void close() {
        shutdown(ShutdownType.GRACEFUL);
    }

    PoolEntry getPoolEntry(final T endpoint) {
        PoolEntry poolEntry = sessionPool.get(endpoint);
        if (poolEntry == null) {
            final PoolEntry newPoolEntry = new PoolEntry();
            poolEntry = sessionPool.putIfAbsent(endpoint, newPoolEntry);
            if (poolEntry == null) {
                poolEntry = newPoolEntry;
            }
        }
        return poolEntry;
    }

    public final Future<IOSession> getSession(
            final T endpoint,
            final Timeout requestTimeout,
            final FutureCallback<IOSession> callback) {
        Args.notNull(endpoint, "Endpoint");
        Asserts.check(!closed.get(), "Connection pool shut down");
        final ComplexFuture<IOSession> future = new ComplexFuture<>(callback);
        final PoolEntry poolEntry = getPoolEntry(endpoint);
        getSessionInternal(poolEntry, false, endpoint, requestTimeout, new FutureCallback<IOSession>() {

            @Override
            public void completed(final IOSession ioSession) {
                validateSession(ioSession, new Callback<Boolean>() {

                    @Override
                    public void execute(final Boolean result) {
                        if (result) {
                            future.completed(ioSession);
                        } else {
                            getSessionInternal(poolEntry, true, endpoint, requestTimeout, new FutureCallback<IOSession>() {

                                @Override
                                public void completed(final IOSession ioSession) {
                                    future.completed(ioSession);
                                }

                                @Override
                                public void failed(final Exception ex) {
                                    future.failed(ex);
                                }

                                @Override
                                public void cancelled() {
                                    future.cancel();
                                }

                            });
                        }
                    }

                });
            }

            @Override
            public void failed(final Exception ex) {
                future.failed(ex);
            }

            @Override
            public void cancelled() {
                future.cancel();
            }

        });
        return future;
    }

    private void getSessionInternal(
            final PoolEntry poolEntry,
            final boolean requestNew,
            final T namedEndpoint,
            final Timeout requestTimeout,
            final FutureCallback<IOSession> callback) {
        synchronized (poolEntry) {
            if (poolEntry.session != null && requestNew) {
                closeSession(poolEntry.session, ShutdownType.GRACEFUL);
                poolEntry.session = null;
            }
            if (poolEntry.session != null && poolEntry.session.isClosed()) {
                poolEntry.session = null;
            }
            if (poolEntry.session != null) {
                callback.completed(poolEntry.session);
            } else {
                poolEntry.requestQueue.add(callback);
                if (poolEntry.sessionFuture == null) {
                    poolEntry.sessionFuture = connectSession(
                            namedEndpoint,
                            requestTimeout,
                            new FutureCallback<IOSession>() {

                                @Override
                                public void completed(final IOSession result) {
                                    synchronized (poolEntry) {
                                        poolEntry.session = result;
                                        poolEntry.sessionFuture = null;
                                        for (;;) {
                                            final FutureCallback<IOSession> callback = poolEntry.requestQueue.poll();
                                            if (callback != null) {
                                                callback.completed(result);
                                            } else {
                                                break;
                                            }
                                        }
                                    }
                                }

                                @Override
                                public void failed(final Exception ex) {
                                    synchronized (poolEntry) {
                                        poolEntry.session = null;
                                        poolEntry.sessionFuture = null;
                                        for (;;) {
                                            final FutureCallback<IOSession> callback = poolEntry.requestQueue.poll();
                                            if (callback != null) {
                                                callback.failed(ex);
                                            } else {
                                                break;
                                            }
                                        }
                                    }
                                }

                                @Override
                                public void cancelled() {
                                    failed(new ConnectionClosedException("Connection request cancelled"));
                                }

                            });
                }
            }
        }
    }

    public final void enumAvailable(final Callback<IOSession> callback) {
        for (final PoolEntry poolEntry: sessionPool.values()) {
            if (poolEntry.session != null) {
                synchronized (poolEntry) {
                    if (poolEntry.session != null) {
                        callback.execute(poolEntry.session);
                        if (poolEntry.session.isClosed()) {
                            poolEntry.session = null;
                        }
                    }
                }
            }
        }
    }

    public final void closeIdle(final TimeValue idleTime) {
        final long deadline = System.currentTimeMillis() - (TimeValue.isPositive(idleTime) ? idleTime.toMillis() : 0);
        for (final PoolEntry poolEntry: sessionPool.values()) {
            if (poolEntry.session != null) {
                synchronized (poolEntry) {
                    if (poolEntry.session != null && poolEntry.session.getLastReadTime() <= deadline) {
                        closeSession(poolEntry.session, ShutdownType.GRACEFUL);
                        poolEntry.session = null;
                    }
                }
            }
        }
    }

    public final Set<T> getRoutes() {
        return new HashSet<>(sessionPool.keySet());
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder();
        buffer.append("I/O sessions: ");
        buffer.append(sessionPool.size());
        return buffer.toString();
    }

    static class PoolEntry {

        final Queue<FutureCallback<IOSession>> requestQueue;
        volatile Future<IOSession> sessionFuture;
        volatile IOSession session;

        PoolEntry() {
            this.requestQueue = new ArrayDeque<>();
        }

    }

}
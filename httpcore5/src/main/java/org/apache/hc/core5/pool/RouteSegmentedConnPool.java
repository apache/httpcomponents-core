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

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * Lock-free, route-segmented connection pool.
 *
 * <p>This implementation keeps per-route state in independent segments and avoids
 * holding a global lock while disposing of connections. Under slow closes
 * (for example TLS shutdown or OS-level socket stalls), threads leasing
 * connections on other routes are not blocked by disposal work.</p>
 *
 * @param <R> route key type
 * @param <C> connection type (must be {@link org.apache.hc.core5.io.ModalCloseable})
 * @see ManagedConnPool
 * @see PoolReusePolicy
 * @see DisposalCallback
 * @since 5.4
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
@Experimental
public final class RouteSegmentedConnPool<R, C extends ModalCloseable> implements ManagedConnPool<R, C> {

    private final PoolReusePolicy reusePolicy;
    private final TimeValue timeToLive;
    private final DisposalCallback<C> disposal;

    private final AtomicInteger defaultMaxPerRoute = new AtomicInteger(5);

    private final ConcurrentHashMap<R, Segment> segments = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<R, Integer> maxPerRoute = new ConcurrentHashMap<>();
    private final AtomicInteger totalAllocated = new AtomicInteger(0);
    private final AtomicInteger maxTotal = new AtomicInteger(25);

    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final ScheduledExecutorService timeouts;

    public RouteSegmentedConnPool(
            final int defaultMaxPerRoute,
            final int maxTotal,
            final TimeValue timeToLive,
            final PoolReusePolicy reusePolicy,
            final DisposalCallback<C> disposal) {

        this.defaultMaxPerRoute.set(defaultMaxPerRoute > 0 ? defaultMaxPerRoute : 5);
        this.maxTotal.set(maxTotal > 0 ? maxTotal : 25);
        this.timeToLive = timeToLive != null ? timeToLive : TimeValue.NEG_ONE_MILLISECOND;
        this.reusePolicy = reusePolicy != null ? reusePolicy : PoolReusePolicy.LIFO;
        this.disposal = Args.notNull(disposal, "disposal");

        final ThreadFactory tf = r -> {
            final Thread t = new Thread(r, "seg-pool-timeouts");
            t.setDaemon(true);
            return t;
        };
        this.timeouts = Executors.newSingleThreadScheduledExecutor(tf);
    }

    final class Segment {
        final ConcurrentLinkedDeque<PoolEntry<R, C>> available = new ConcurrentLinkedDeque<>();
        final ConcurrentLinkedQueue<Waiter> waiters = new ConcurrentLinkedQueue<>();
        final AtomicInteger allocated = new AtomicInteger(0);

        int limitPerRoute(final R route) {
            final Integer v = maxPerRoute.get(route);
            return v != null ? v : defaultMaxPerRoute.get();
        }
    }

    final class Waiter extends CompletableFuture<PoolEntry<R, C>> {
        final Timeout requestTimeout;
        final Object state;
        volatile boolean cancelled;

        Waiter(final Timeout t, final Object s) {
            this.requestTimeout = t != null ? t : Timeout.DISABLED;
            this.state = s;
        }
    }

    @Override
    public Future<PoolEntry<R, C>> lease(
            final R route,
            final Object state,
            final Timeout requestTimeout,
            final FutureCallback<PoolEntry<R, C>> callback) {

        ensureOpen();
        final Segment seg = segments.computeIfAbsent(route, r -> new Segment());

        PoolEntry<R, C> hit;
        for (; ; ) {
            hit = pollAvailable(seg, state);
            if (hit == null) {
                break;
            }
            final long now = System.currentTimeMillis();
            if (hit.getExpiryDeadline().isBefore(now) || isPastTtl(hit)) {
                discardAndDecr(hit, CloseMode.GRACEFUL);
                continue;
            }
            break;
        }
        if (hit != null) {
            if (callback != null) {
                callback.completed(hit);
            }
            return CompletableFuture.completedFuture(hit);
        }

        for (; ; ) {
            final int tot = totalAllocated.get();
            if (tot >= maxTotal.get()) {
                break;
            }
            if (totalAllocated.compareAndSet(tot, tot + 1)) {
                for (; ; ) {
                    final int per = seg.allocated.get();
                    if (per >= seg.limitPerRoute(route)) {
                        totalAllocated.decrementAndGet();
                        break;
                    }
                    if (seg.allocated.compareAndSet(per, per + 1)) {
                        final PoolEntry<R, C> entry = new PoolEntry<>(route, timeToLive, disposal);
                        if (callback != null) {
                            callback.completed(entry);
                        }
                        return CompletableFuture.completedFuture(entry);
                    }
                }
                break;
            }
        }

        final Waiter w = new Waiter(requestTimeout, state);
        seg.waiters.add(w);

        final PoolEntry<R, C> late = pollAvailable(seg, state);
        if (late != null && seg.waiters.remove(w)) {
            if (callback != null) {
                callback.completed(late);
            }
            w.complete(late);
            return w;
        }

        scheduleTimeout(w, seg);

        if (callback != null) {
            w.whenComplete((pe, ex) -> {
                if (ex != null) {
                    callback.failed(ex instanceof Exception ? (Exception) ex : new Exception(ex));
                } else {
                    callback.completed(pe);
                }
            });
        }
        return w;
    }

    @Override
    public void release(final PoolEntry<R, C> entry, final boolean reusable) {
        if (entry == null) {
            return;
        }
        final R route = entry.getRoute();
        final Segment seg = segments.get(route);
        if (seg == null) {
            entry.discardConnection(CloseMode.GRACEFUL);
            return;
        }

        final long now = System.currentTimeMillis();
        final boolean stillValid = reusable && !isPastTtl(entry) && !entry.getExpiryDeadline().isBefore(now);

        if (stillValid) {
            for (; ; ) {
                final Waiter w = seg.waiters.poll();
                if (w == null) {
                    break;
                }
                if (w.cancelled) {
                    continue;
                }
                if (compatible(w.state, entry.getState())) {
                    if (w.complete(entry)) {
                        return;
                    }
                }
            }
            offerAvailable(seg, entry);
        } else {
            discardAndDecr(entry, CloseMode.GRACEFUL);
        }

        maybeCleanupSegment(route, seg);
    }

    @Override
    public void close() throws IOException {
        close(CloseMode.GRACEFUL);
    }

    @Override
    public void close(final CloseMode closeMode) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        timeouts.shutdownNow();

        for (final Map.Entry<R, Segment> e : segments.entrySet()) {
            final Segment seg = e.getValue();

            // cancel waiters
            for (final Waiter w : seg.waiters) {
                w.cancelled = true;
                w.completeExceptionally(new TimeoutException("Pool closed"));
            }
            seg.waiters.clear();

            for (final PoolEntry<R, C> p : seg.available) {
                p.discardConnection(orImmediate(closeMode));
            }
            seg.available.clear();

            final int alloc = seg.allocated.getAndSet(0);
            if (alloc != 0) {
                totalAllocated.addAndGet(-alloc);
            }
        }
        segments.clear();
    }

    @Override
    public void closeIdle(final TimeValue idleTime) {
        final long cutoff = System.currentTimeMillis()
                - Math.max(0L, idleTime != null ? idleTime.toMilliseconds() : 0L);

        for (final Map.Entry<R, Segment> e : segments.entrySet()) {
            final R route = e.getKey();
            final Segment seg = e.getValue();

            int processed = 0;
            final int cap = 64;
            for (final Iterator<PoolEntry<R, C>> it = seg.available.iterator(); it.hasNext(); ) {
                final PoolEntry<R, C> p = it.next();
                if (p.getUpdated() <= cutoff) {
                    it.remove();
                    discardAndDecr(p, CloseMode.GRACEFUL);
                    if (++processed == cap) {
                        break;
                    }
                }
            }
            maybeCleanupSegment(route, seg);
        }
    }

    @Override
    public void closeExpired() {
        final long now = System.currentTimeMillis();

        for (final Map.Entry<R, Segment> e : segments.entrySet()) {
            final R route = e.getKey();
            final Segment seg = e.getValue();

            int processed = 0;
            final int cap = 64;
            for (final Iterator<PoolEntry<R, C>> it = seg.available.iterator(); it.hasNext(); ) {
                final PoolEntry<R, C> p = it.next();
                if (p.getExpiryDeadline().isBefore(now) || isPastTtl(p)) {
                    it.remove();
                    discardAndDecr(p, CloseMode.GRACEFUL);
                    if (++processed == cap) {
                        break;
                    }
                }
            }
            maybeCleanupSegment(route, seg);
        }
    }

    @Override
    public Set<R> getRoutes() {
        final Set<R> out = new HashSet<>();
        for (final Map.Entry<R, Segment> e : segments.entrySet()) {
            final Segment s = e.getValue();
            if (!s.available.isEmpty() || s.allocated.get() > 0 || !s.waiters.isEmpty()) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    @Override
    public int getMaxTotal() {
        return maxTotal.get();
    }

    @Override
    public void setMaxTotal(final int max) {
        maxTotal.set(Math.max(1, max));
    }

    @Override
    public int getDefaultMaxPerRoute() {
        return defaultMaxPerRoute.get();
    }

    @Override
    public void setDefaultMaxPerRoute(final int max) {
        defaultMaxPerRoute.set(Math.max(1, max));
    }

    @Override
    public int getMaxPerRoute(final R route) {
        final Integer v = maxPerRoute.get(route);
        return v != null ? v : defaultMaxPerRoute.get();
    }

    @Override
    public void setMaxPerRoute(final R route, final int max) {
        if (max <= 0) {
            maxPerRoute.remove(route);
        } else {
            maxPerRoute.put(route, max);
        }
    }

    @Override
    public PoolStats getTotalStats() {
        int leased = 0, availableCount = 0, pending = 0;
        for (final Segment seg : segments.values()) {
            final int alloc = seg.allocated.get();
            final int avail = seg.available.size();
            leased += Math.max(0, alloc - avail);
            availableCount += avail;
            pending += seg.waiters.size();
        }
        return new PoolStats(leased, pending, availableCount, getMaxTotal());
    }

    @Override
    public PoolStats getStats(final R route) {
        final Segment seg = segments.get(route);
        if (seg == null) {
            return new PoolStats(0, 0, 0, getMaxPerRoute(route));
        }
        final int alloc = seg.allocated.get();
        final int avail = seg.available.size();
        final int leased = Math.max(0, alloc - avail);
        final int pending = seg.waiters.size();
        return new PoolStats(leased, pending, avail, getMaxPerRoute(route));
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Pool is closed");
        }
    }

    private boolean isPastTtl(final PoolEntry<R, C> p) {
        if (timeToLive == null || timeToLive.getDuration() < 0) {
            return false;
        }
        return (System.currentTimeMillis() - p.getCreated()) >= timeToLive.toMilliseconds();
    }

    private void scheduleTimeout(
            final Waiter w,
            final Segment seg) {

        if (!TimeValue.isPositive(w.requestTimeout)) {
            return;
        }
        timeouts.schedule(() -> {
            if (w.isDone()) {
                return;
            }
            w.cancelled = true;
            final TimeoutException tex = new TimeoutException("Lease timed out");
            w.completeExceptionally(tex);

            final PoolEntry<R, C> p = pollAvailable(seg, w.state);
            if (p != null) {
                boolean handedOff = false;
                for (Waiter other; (other = seg.waiters.poll()) != null; ) {
                    if (!other.cancelled && compatible(other.state, p.getState())) {
                        handedOff = other.complete(p);
                        if (handedOff) {
                            break;
                        }
                    }
                }
                if (!handedOff) {
                    offerAvailable(seg, p);
                }
            }
        }, w.requestTimeout.toMilliseconds(), TimeUnit.MILLISECONDS);
    }

    private void offerAvailable(final Segment seg, final PoolEntry<R, C> p) {
        if (reusePolicy == PoolReusePolicy.LIFO) {
            seg.available.addFirst(p);
        } else {
            seg.available.addLast(p);
        }
    }

    private PoolEntry<R, C> pollAvailable(final Segment seg, final Object neededState) {
        for (final Iterator<PoolEntry<R, C>> it = seg.available.iterator(); it.hasNext(); ) {
            final PoolEntry<R, C> p = it.next();
            if (compatible(neededState, p.getState())) {
                it.remove();
                return p;
            }
        }
        return null;
    }

    private boolean compatible(final Object needed, final Object have) {
        return needed == null || Objects.equals(needed, have);
    }

    private void discardAndDecr(final PoolEntry<R, C> p, final CloseMode mode) {
        p.discardConnection(orImmediate(mode));
        totalAllocated.decrementAndGet();
        final Segment seg = segments.get(p.getRoute());
        if (seg != null) {
            seg.allocated.decrementAndGet();
        }
    }

    private CloseMode orImmediate(final CloseMode m) {
        return m != null ? m : CloseMode.IMMEDIATE;
    }

    private void maybeCleanupSegment(final R route, final Segment seg) {
        if (seg.allocated.get() == 0 && seg.available.isEmpty() && seg.waiters.isEmpty()) {
            segments.remove(route, seg);
        }
    }
}

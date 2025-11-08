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
import java.util.ArrayDeque;
import java.util.Deque;
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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
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
 * Lock-free, route-segmented connection pool with tiny, conditional round-robin assistance.
 *
 * <p>Per-route state is kept in independent segments. Disposal of connections is offloaded
 * to a bounded executor so slow closes do not block threads leasing on other routes.
 * A minimal round-robin drainer is engaged only when there are many pending routes and
 * there is global headroom; it never scans all routes.</p>
 *
 * @param <R> route key type
 * @param <C> connection type (must be {@link ModalCloseable})
 * @see ManagedConnPool
 * @see PoolReusePolicy
 * @see DisposalCallback
 * @since 5.4
 */
@Contract(threading = ThreadingBehavior.SAFE_CONDITIONAL)
@Experimental
public final class RouteSegmentedConnPool<R, C extends ModalCloseable> implements ManagedConnPool<R, C> {

    // Tiny RR assist: only engage when there are many distinct routes waiting and there is headroom.
    private static final int RR_MIN_PENDING_ROUTES = 12;
    private static final int RR_BUDGET = 64;

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

    /**
     * Dedicated executor for asynchronous, best-effort disposal.
     * Bounded queue; on saturation we fall back to IMMEDIATE close on the caller thread.
     */
    private final ThreadPoolExecutor disposer;

    // Minimal fair round-robin over routes with waiters (no global scans).
    private final ConcurrentLinkedQueue<R> pendingQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final AtomicInteger pendingRouteCount = new AtomicInteger(0);

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

        // Asynchronous disposer for slow GRACEFUL closes.
        final int cores = Math.max(2, Runtime.getRuntime().availableProcessors());
        final int nThreads = Math.min(8, Math.max(2, cores)); // allow up to 8 on bigger boxes
        final int qsize = 1024;
        final ThreadFactory df = r -> {
            final Thread t = new Thread(r, "seg-pool-disposer");
            t.setDaemon(true);
            return t;
        };
        this.disposer = new ThreadPoolExecutor(
                nThreads, nThreads,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(qsize),
                df,
                new ThreadPoolExecutor.AbortPolicy()); // but we preflight capacity to avoid exception storms
    }

    final class Segment {
        final ConcurrentLinkedDeque<PoolEntry<R, C>> available = new ConcurrentLinkedDeque<>();
        final ConcurrentLinkedDeque<Waiter> waiters = new ConcurrentLinkedDeque<>();
        final AtomicInteger allocated = new AtomicInteger(0);
        final AtomicBoolean enqueued = new AtomicBoolean(false);

        int limitPerRoute(final R route) {
            final Integer v = maxPerRoute.get(route);
            return v != null ? v : defaultMaxPerRoute.get();
        }
    }

    final class Waiter extends CompletableFuture<PoolEntry<R, C>> {
        final R route;
        final Timeout requestTimeout;
        final Object state;
        volatile boolean cancelled;
        volatile ScheduledFuture<?> timeoutTask;

        Waiter(final R route, final Timeout t, final Object s) {
            this.route = route;
            this.requestTimeout = t != null ? t : Timeout.DISABLED;
            this.state = s;
            this.cancelled = false;
            this.timeoutTask = null;
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

        // 1) Try available
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

        // 2) Try to allocate new within caps
        if (tryAllocateOne(route, seg)) {
            final PoolEntry<R, C> entry = new PoolEntry<>(route, timeToLive, disposal);
            if (callback != null) {
                callback.completed(entry);
            }
            return CompletableFuture.completedFuture(entry);
        }

        // 3) Enqueue waiter with timeout
        final Waiter w = new Waiter(route, requestTimeout, state);
        seg.waiters.addLast(w);
        enqueueIfNeeded(route, seg);

        // Late hit after enqueuing
        final PoolEntry<R, C> late = pollAvailable(seg, state);
        if (late != null) {
            if (seg.waiters.remove(w)) {
                cancelTimeout(w);
                if (callback != null) {
                    callback.completed(late);
                }
                w.complete(late);
                dequeueIfDrained(seg);
                return w;
            } else {
                boolean handedOff = false;
                for (Waiter other; (other = seg.waiters.pollFirst()) != null; ) {
                    if (!other.cancelled && compatible(other.state, late.getState())) {
                        cancelTimeout(other);
                        handedOff = other.complete(late);
                        if (handedOff) {
                            break;
                        }
                    }
                }
                if (!handedOff) {
                    offerAvailable(seg, late);
                }
            }
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

        triggerDrainIfMany();
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
            // Segment got removed; dispose off-thread and bail.
            discardEntry(entry, CloseMode.GRACEFUL);
            return;
        }

        final long now = System.currentTimeMillis();
        final boolean stillValid = reusable && !isPastTtl(entry) && !entry.getExpiryDeadline().isBefore(now);

        if (stillValid) {
            if (!handOffToCompatibleWaiter(entry, seg)) {
                offerAvailable(seg, entry);
                enqueueIfNeeded(route, seg);
                triggerDrainIfMany();
            }
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

            for (final Waiter w : seg.waiters) {
                w.cancelled = true;
                cancelTimeout(w);
                w.completeExceptionally(new TimeoutException("Pool closed"));
            }
            seg.waiters.clear();
            if (seg.enqueued.getAndSet(false)) {
                pendingRouteCount.decrementAndGet();
            }

            // discard available
            for (final PoolEntry<R, C> p : seg.available) {
                discardEntry(p, closeMode);
            }
            seg.available.clear();

            final int alloc = seg.allocated.getAndSet(0);
            if (alloc != 0) {
                totalAllocated.addAndGet(-alloc);
            }
        }
        segments.clear();
        pendingQueue.clear();
        pendingRouteCount.set(0);

        // Let in-flight graceful closes progress; no blocking here.
        disposer.shutdown();
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
        return System.currentTimeMillis() - p.getCreated() >= timeToLive.toMilliseconds();
    }

    private void scheduleTimeout(final Waiter w, final Segment seg) {
        if (!TimeValue.isPositive(w.requestTimeout)) {
            return;
        }
        w.timeoutTask = timeouts.schedule(() -> {
            if (w.isDone()) {
                return;
            }
            w.cancelled = true;
            seg.waiters.remove(w);
            w.completeExceptionally(new TimeoutException("Lease timed out"));
            dequeueIfDrained(seg);
            maybeCleanupSegment(w.route, seg);

            final PoolEntry<R, C> p = pollAvailable(seg, w.state);
            if (p != null) {
                // Try to hand off that available entry to some other compatible waiter.
                if (!handOffToCompatibleWaiter(p, seg)) {
                    offerAvailable(seg, p);
                }
            }
        }, w.requestTimeout.toMilliseconds(), TimeUnit.MILLISECONDS);
    }

    private void cancelTimeout(final Waiter w) {
        final ScheduledFuture<?> t = w.timeoutTask;
        if (t != null) {
            t.cancel(false);
        }
    }

    private void offerAvailable(final Segment seg, final PoolEntry<R, C> p) {
        if (reusePolicy == PoolReusePolicy.LIFO) {
            seg.available.addFirst(p);
        } else {
            seg.available.addLast(p);
        }
    }

    private PoolEntry<R, C> pollAvailable(final Segment seg, final Object neededState) {
        if (neededState == null) {
            return seg.available.pollFirst();
        }
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

    private boolean handOffToCompatibleWaiter(final PoolEntry<R, C> entry, final Segment seg) {
        final Deque<Waiter> skipped = new ArrayDeque<>();
        boolean handedOff = false;
        for (; ; ) {
            final Waiter w = seg.waiters.pollFirst();
            if (w == null) {
                break;
            }
            if (w.cancelled || w.isDone()) {
                continue;
            }
            if (compatible(w.state, entry.getState())) {
                cancelTimeout(w);
                handedOff = w.complete(entry);
                if (handedOff) {
                    dequeueIfDrained(seg);
                    break;
                }
            } else {
                skipped.addLast(w);
            }
        }
        // Restore non-compatible waiters to the head to preserve ordering.
        while (!skipped.isEmpty()) {
            seg.waiters.addFirst(skipped.pollLast());
        }
        return handedOff;
    }

    private void discardAndDecr(final PoolEntry<R, C> p, final CloseMode mode) {
        totalAllocated.decrementAndGet();
        final Segment seg = segments.get(p.getRoute());
        if (seg != null) {
            seg.allocated.decrementAndGet();
        }
        discardEntry(p, mode);
    }

    private CloseMode orImmediate(final CloseMode m) {
        return m != null ? m : CloseMode.IMMEDIATE;
    }

    private void maybeCleanupSegment(final R route, final Segment seg) {
        if (seg.allocated.get() == 0 && seg.available.isEmpty() && seg.waiters.isEmpty()) {
            segments.remove(route, seg);
            if (seg.enqueued.getAndSet(false)) {
                pendingRouteCount.decrementAndGet();
            }
        }
    }

    private boolean tryAllocateOne(final R route, final Segment seg) {
        for (; ; ) {
            final int tot = totalAllocated.get();
            if (tot >= maxTotal.get()) {
                return false;
            }
            if (!totalAllocated.compareAndSet(tot, tot + 1)) {
                continue;
            }
            for (; ; ) {
                final int per = seg.allocated.get();
                if (per >= seg.limitPerRoute(route)) {
                    totalAllocated.decrementAndGet();
                    return false;
                }
                if (seg.allocated.compareAndSet(per, per + 1)) {
                    return true;
                }
            }
        }
    }

    private void enqueueIfNeeded(final R route, final Segment seg) {
        if (seg.enqueued.compareAndSet(false, true)) {
            pendingQueue.offer(route);
            pendingRouteCount.incrementAndGet();
        }
    }

    private void dequeueIfDrained(final Segment seg) {
        if (seg.waiters.isEmpty() && seg.enqueued.getAndSet(false)) {
            pendingRouteCount.decrementAndGet();
        }
    }

    private void triggerDrainIfMany() {
        // Engage RR only if there is global headroom and many distinct routes pending
        if (pendingRouteCount.get() < RR_MIN_PENDING_ROUTES) {
            return;
        }
        if (totalAllocated.get() >= maxTotal.get()) {
            return;
        }
        if (!draining.compareAndSet(false, true)) {
            return;
        }
        disposer.execute(() -> {
            try {
                serveRoundRobin(RR_BUDGET);
            } finally {
                draining.set(false);
                if (pendingRouteCount.get() >= RR_MIN_PENDING_ROUTES
                        && totalAllocated.get() < maxTotal.get()
                        && !pendingQueue.isEmpty()) {
                    triggerDrainIfMany();
                }
            }
        });
    }

    private void serveRoundRobin(final int budget) {
        int created = 0;
        for (; created < budget; ) {
            final R route = pendingQueue.poll();
            if (route == null) {
                break;
            }
            final Segment seg = segments.get(route);
            if (seg == null) {
                continue;
            }
            if (seg.waiters.isEmpty()) {
                if (seg.enqueued.getAndSet(false)) {
                    pendingRouteCount.decrementAndGet();
                }
                continue;
            }

            if (!tryAllocateOne(route, seg)) {
                // No headroom or hit per-route cap. Re-queue for later.
                pendingQueue.offer(route);
                continue;
            }

            final Waiter w = seg.waiters.pollFirst();
            if (w == null || w.cancelled) {
                seg.allocated.decrementAndGet();
                totalAllocated.decrementAndGet();
            } else {
                final PoolEntry<R, C> entry = new PoolEntry<>(route, timeToLive, disposal);
                cancelTimeout(w);
                w.complete(entry);
                created++;
            }

            if (!seg.waiters.isEmpty()) {
                pendingQueue.offer(route);
            } else {
                if (seg.enqueued.getAndSet(false)) {
                    pendingRouteCount.decrementAndGet();
                }
            }
        }
    }

    /**
     * Dispose a pool entry's connection asynchronously if possible; under pressure fall back to IMMEDIATE on caller.
     */
    private void discardEntry(final PoolEntry<R, C> p, final CloseMode preferred) {
        final CloseMode mode = orImmediate(preferred);
        // Pre-flight capacity to avoid exception storms under saturation
        if (disposer.isShutdown()) {
            p.discardConnection(CloseMode.IMMEDIATE);
            return;
        }
        final LinkedBlockingQueue<Runnable> q = (LinkedBlockingQueue<Runnable>) disposer.getQueue();
        if (q.remainingCapacity() == 0) {
            p.discardConnection(CloseMode.IMMEDIATE);
            return;
        }
        try {
            disposer.execute(() -> {
                try {
                    p.discardConnection(mode);
                } catch (final RuntimeException ignore) {
                    // best-effort
                }
            });
        } catch (final RejectedExecutionException saturated) {
            // Saturated or shutting down: never block caller
            p.discardConnection(CloseMode.IMMEDIATE);
        }
    }
}

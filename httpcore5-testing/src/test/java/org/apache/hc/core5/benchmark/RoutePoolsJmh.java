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
package org.apache.hc.core5.benchmark;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.pool.DisposalCallback;
import org.apache.hc.core5.pool.LaxConnPool;
import org.apache.hc.core5.pool.ManagedConnPool;
import org.apache.hc.core5.pool.PoolEntry;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.pool.RouteSegmentedConnPool;
import org.apache.hc.core5.pool.StrictConnPool;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * JMH harness that drives StrictConnPool, LaxConnPool, and RouteSegmentedConnPool
 * against a local HTTP/1.1 mini-cluster using real sockets and keep-alive.
 */
@BenchmarkMode({Mode.Throughput})
@Warmup(iterations = 10, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@OutputTimeUnit(TimeUnit.SECONDS)
public class RoutePoolsJmh {

    // ---------------------------------------------------------------
    // Utilities
    // ---------------------------------------------------------------
    static ThreadFactory daemonFactory(final String prefix) {
        final AtomicInteger n = new AtomicInteger(1);
        return r -> {
            final Thread t = new Thread(r, prefix + "-" + n.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
    }

    // ---------------------------------------------------------------
    // Real HTTP/1.1 persistent connection used by the pool
    // ---------------------------------------------------------------
    public static final class RealConn implements ModalCloseable {
        private final String host;
        private final int port;
        private final int closeDelayMs;
        private final Socket socket;
        private final BufferedInputStream in;
        private final BufferedOutputStream out;

        public RealConn(
                final String host,
                final int port,
                final int closeDelayMs,
                final int soTimeoutMs,
                final int connectTimeoutMs) throws IOException {
            this.host = host;
            this.port = port;
            this.closeDelayMs = closeDelayMs;
            final Socket s = new Socket();
            s.setTcpNoDelay(true);
            s.setSoTimeout(Math.max(1000, soTimeoutMs)); // read timeout
            s.setKeepAlive(true);
            s.connect(new InetSocketAddress(host, port), Math.max(1, connectTimeoutMs));
            this.socket = s;
            this.in = new BufferedInputStream(s.getInputStream(), 32 * 1024);
            this.out = new BufferedOutputStream(s.getOutputStream(), 32 * 1024);
        }

        public void getOnce(final boolean keepAlive) throws IOException {
            final String req = "GET / HTTP/1.1\r\n" +
                    "Host: " + host + ":" + port + "\r\n" +
                    (keepAlive ? "Connection: keep-alive\r\n" : "Connection: close\r\n") +
                    "\r\n";
            out.write(req.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();

            final String status = readLine();
            if (status == null) {
                throw new IOException("No status line");
            }
            final String[] parts = status.split(" ", 3);
            if (parts.length < 2 || !parts[0].startsWith("HTTP/1.")) {
                throw new IOException("Bad status: " + status);
            }
            final int code;
            try {
                code = Integer.parseInt(parts[1]);
            } catch (final NumberFormatException nfe) {
                throw new IOException("Bad status code in: " + status);
            }
            if (code != 200) {
                throw new IOException("Unexpected status: " + status);
            }

            int contentLength = -1;
            for (; ; ) {
                final String line = readLine();
                if (line == null) {
                    throw new IOException("EOF in headers");
                }
                if (line.isEmpty()) {
                    break;
                }
                final int colon = line.indexOf(':');
                if (colon > 0) {
                    final String name = line.substring(0, colon).trim();
                    if ("Content-Length".equalsIgnoreCase(name)) {
                        try {
                            contentLength = Integer.parseInt(line.substring(colon + 1).trim());
                        } catch (final NumberFormatException ignore) {
                            // ignore
                        }
                    }
                }
            }
            if (contentLength < 0) {
                throw new IOException("Missing Content-Length");
            }

            int remaining = contentLength;
            final byte[] buf = new byte[8192];
            while (remaining > 0) {
                final int r = in.read(buf, 0, Math.min(buf.length, remaining));
                if (r == -1) {
                    throw new IOException("unexpected EOF in body");
                }
                remaining -= r;
            }
        }

        private String readLine() throws IOException {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream(128);
            for (; ; ) {
                final int b = in.read();
                if (b == -1) {
                    if (baos.size() == 0) {
                        return null;
                    }
                    break;
                }
                if (b == '\n') {
                    break;
                }
                baos.write(b);
            }
            final byte[] raw = baos.toByteArray();
            final int len = raw.length;
            final int eff = (len > 0 && raw[len - 1] == '\r') ? len - 1 : len;
            return new String(raw, 0, eff, StandardCharsets.ISO_8859_1);
        }

        @Override
        public void close(final CloseMode closeMode) {
            if (closeDelayMs > 0) {
                try {
                    Thread.sleep(closeDelayMs);
                } catch (final InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            try {
                socket.close();
            } catch (final IOException ignore) {
                // ignore
            }
        }

        @Override
        public void close() throws IOException {
            if (closeDelayMs > 0) {
                try {
                    Thread.sleep(closeDelayMs);
                } catch (final InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            socket.close();
        }
    }

    // ---------------------------------------------------------------
    // Benchmark state & setup
    // ---------------------------------------------------------------
    @State(Scope.Benchmark)
    public static class BenchState {
        @Param({"OFFLOCK", "STRICT", "LAX"})
        public String policy;
        @Param({"1", "4", "10", "25", "50"})
        public int routes;
        @Param({"128"})
        public int payloadBytes;
        @Param({"100"})
        public int maxTotal;
        @Param({"5"})
        public int defMaxPerRoute;
        @Param({"true"})
        public boolean keepAlive;
        @Param({"5000"})
        public int keepAliveMs;
        @Param({"0", "20"})
        public int slowClosePct;
        @Param({"0", "200"})
        public int closeSleepMs;
        @Param({"10000"})
        public int soTimeoutMs;
        @Param({"30000"})
        public int requestTimeoutMs;
        @Param({"1000"})
        public int connectTimeoutMs;

        ManagedConnPool<String, RealConn> pool;
        DisposalCallback<RealConn> disposal;
        MiniCluster cluster;
        String[] routeKeys;
        ScheduledExecutorService maint;

        @Setup(Level.Trial)
        public void setUp() throws Exception {
            cluster = new MiniCluster(routes, payloadBytes);
            routeKeys = cluster.routeKeys();
            disposal = (c, m) -> {
                if (c != null) {
                    c.close(m);
                }
            };
            final TimeValue ttl = TimeValue.NEG_ONE_MILLISECOND;
            switch (policy.toUpperCase(Locale.ROOT)) {
                case "STRICT": {
                    pool = new StrictConnPool<>(defMaxPerRoute, maxTotal, ttl, PoolReusePolicy.LIFO, disposal, null);
                    break;
                }
                case "LAX": {
                    final LaxConnPool<String, RealConn> lax = new LaxConnPool<>(defMaxPerRoute, ttl, PoolReusePolicy.LIFO, disposal, null);
                    lax.setMaxTotal(maxTotal);
                    pool = lax;
                    break;
                }
                case "OFFLOCK": {
                    pool = new RouteSegmentedConnPool<>(defMaxPerRoute, maxTotal, ttl, PoolReusePolicy.LIFO, disposal);
                    break;
                }
                default: {
                    throw new IllegalArgumentException("Unknown policy: " + policy);
                }
            }
            // Light periodic maintenance, close idle/expired like real clients do
            maint = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(daemonFactory("pool-maint"));
            maint.scheduleAtFixedRate(() -> {
                try {
                    pool.closeIdle(TimeValue.ofSeconds(5));
                    pool.closeExpired();
                } catch (final Exception ignore) {
                    // ignore in benchmark
                }
            }, 5, 5, TimeUnit.SECONDS);
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            if (pool != null) {
                pool.close(CloseMode.IMMEDIATE);
            }
            if (cluster != null) {
                cluster.close();
            }
            if (maint != null) {
                maint.shutdownNow();
                try {
                    maint.awaitTermination(5, TimeUnit.SECONDS);
                } catch (final InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        String pickRoute() {
            final int idx = ThreadLocalRandom.current().nextInt(routeKeys.length);
            return routeKeys[idx];
        }

        boolean shouldDiscard() {
            return slowClosePct > 0 && ThreadLocalRandom.current().nextInt(100) < slowClosePct;
        }
    }

    // ---------------------------------------------------------------
    // Benchmark body
    // ---------------------------------------------------------------
    @Benchmark
    @Threads(50)
    public void lease_io_release(final BenchState s) {
        final String key = s.pickRoute();
        final Future<PoolEntry<String, RealConn>> f = s.pool.lease(key, null, Timeout.DISABLED, null);
        final PoolEntry<String, RealConn> e;
        try {
            e = f.get(s.requestTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (final TimeoutException te) {
            // IMPORTANT: drop waiter on pools that queue
            f.cancel(true);
            return;
        } catch (final ExecutionException ee) {
            if (ee.getCause() instanceof TimeoutException) {
                f.cancel(true);
            }
            return;
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
            return;
        }
        if (e == null) {
            return; // defensive
        }

        RealConn c = e.getConnection();
        if (c == null) {
            // parse host:port defensively
            final int colon = key.indexOf(':');
            if (colon <= 0 || colon >= key.length() - 1) {
                s.pool.release(e, false);
                return;
            }
            final String host = key.substring(0, colon);
            final int port;
            try {
                port = Integer.parseInt(key.substring(colon + 1));
            } catch (final NumberFormatException nfe) {
                s.pool.release(e, false);
                return;
            }
            RealConn fresh = null;
            try {
                fresh = new RealConn(host, port, s.closeSleepMs, s.soTimeoutMs, s.connectTimeoutMs);
                // Double-check before assigning to avoid races
                final RealConn existing = e.getConnection();
                if (existing == null) {
                    try {
                        e.assignConnection(fresh);
                        c = fresh;
                        fresh = null; // ownership transferred
                    } catch (final IllegalStateException already) {
                        // someone else assigned concurrently
                        c = e.getConnection();
                        if (c == null) {
                            s.pool.release(e, false);
                            try {
                                fresh.close(CloseMode.IMMEDIATE);
                            } catch (final Exception ignore) {
                            }
                            return;
                        }
                    }
                } else {
                    c = existing;
                }
            } catch (final IOException ioe) {
                s.pool.release(e, false);
                if (fresh != null) {
                    try {
                        fresh.close(CloseMode.IMMEDIATE);
                    } catch (final Exception ignore) {
                    }
                }
                return;
            } finally {
                if (fresh != null) { // we created but didn't assign -> close to avoid leak
                    try {
                        fresh.close(CloseMode.IMMEDIATE);
                    } catch (final Exception ignore) {
                    }
                }
            }
        }

        if (c == null) {
            s.pool.release(e, false);
            return;
        }

        try {
            c.getOnce(s.keepAlive);
        } catch (final IOException ioe) {
            s.pool.release(e, false);
            return;
        }

        final boolean reusable = s.keepAlive && !s.shouldDiscard();
        if (reusable) {
            e.updateExpiry(TimeValue.ofMilliseconds(s.keepAliveMs));
            s.pool.release(e, true);
        } else {
            s.pool.release(e, false);
        }
    }

    // ---------------------------------------------------------------
    // Local HTTP mini-cluster
    // ---------------------------------------------------------------
    static final class MiniCluster {
        private final List<HttpServer> servers = new ArrayList<>();
        private final String[] keys;
        private final byte[] body;
        private final ExecutorService exec;

        MiniCluster(final int n, final int payloadBytes) throws IOException {
            this.keys = new String[n];
            this.body = new byte[payloadBytes];
            // Bounded, CPU-sized pool to keep the com.sun server in check
            final int cores = Math.max(2, Runtime.getRuntime().availableProcessors());
            final int coreThreads = Math.min(64, Math.max(cores, n * 2));
            final int maxThreads = Math.min(128, Math.max(coreThreads, n * 4));
            this.exec = new java.util.concurrent.ThreadPoolExecutor(
                    coreThreads, maxThreads,
                    60L, TimeUnit.SECONDS,
                    new java.util.concurrent.LinkedBlockingQueue<>(2048),
                    daemonFactory("mini-http"),
                    new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
            for (int i = 0; i < n; i++) {
                final InetSocketAddress bind = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
                final HttpServer s = HttpServer.create(bind, 4096);
                s.createContext("/", new FixedHandler(body));
                s.setExecutor(exec);
                s.start();
                servers.add(s);
                keys[i] = "127.0.0.1:" + s.getAddress().getPort();
            }
        }

        String[] routeKeys() {
            return keys;
        }

        void close() {
            for (final HttpServer s : servers) {
                try {
                    s.stop(0);
                } catch (final Exception ignore) {
                }
            }
            exec.shutdownNow();
            try {
                exec.awaitTermination(5, TimeUnit.SECONDS);
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static final class FixedHandler implements HttpHandler {
        private final byte[] body;

        FixedHandler(final byte[] body) {
            this.body = body;
        }

        @Override
        public void handle(final HttpExchange ex) throws IOException {
            try (InputStream in = ex.getRequestBody()) {
                final byte[] buf = new byte[1024];
                while (in.read(buf) != -1) {
                    // drain
                }
            }
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=US-ASCII");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                if (body.length > 0) {
                    os.write(body);
                }
                os.flush();
            }
        }
    }
}

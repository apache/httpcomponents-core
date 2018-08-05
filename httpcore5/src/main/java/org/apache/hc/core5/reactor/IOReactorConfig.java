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

import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * I/O reactor configuration parameters.
 *
 * @since 4.2
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class IOReactorConfig {

    public static final IOReactorConfig DEFAULT = new Builder().build();

    private final long selectIntervalMillis;
    private final int ioThreadCount;
    private final Timeout  soTimeout;
    private final boolean soReuseAddress;
    private final TimeValue soLinger;
    private final boolean soKeepAlive;
    private final boolean tcpNoDelay;
    private final int sndBufSize;
    private final int rcvBufSize;
    private final int backlogSize;

    IOReactorConfig(
            final long selectIntervalMillis,
            final int ioThreadCount,
            final Timeout soTimeout,
            final boolean soReuseAddress,
            final TimeValue soLinger,
            final boolean soKeepAlive,
            final boolean tcpNoDelay,
            final int sndBufSize,
            final int rcvBufSize,
            final int backlogSize) {
        super();
        this.selectIntervalMillis = selectIntervalMillis;
        this.ioThreadCount = Args.positive(ioThreadCount, "ioThreadCount");
        this.soTimeout = soTimeout;
        this.soReuseAddress = soReuseAddress;
        this.soLinger = soLinger;
        this.soKeepAlive = soKeepAlive;
        this.tcpNoDelay = tcpNoDelay;
        this.sndBufSize = sndBufSize;
        this.rcvBufSize = rcvBufSize;
        this.backlogSize = backlogSize;
    }

    /**
     * Determines time interval in milliseconds at which the I/O reactor wakes up to check for
     * timed out sessions and session requests.
     * <p>
     * Default: {@code 1000} milliseconds.
     * </p>
     */
    public long getSelectIntervalMillis() {
        return this.selectIntervalMillis;
    }

    /**
     * Determines the number of I/O dispatch threads to be used by the I/O reactor.
     * <p>
     * Default: {@code 2}
     * </p>
     */
    public int getIoThreadCount() {
        return this.ioThreadCount;
    }

    /**
     * Determines the default socket timeout value for non-blocking I/O operations.
     * <p>
     * Default: {@code 0} (no timeout)
     * </p>
     *
     * @see java.net.SocketOptions#SO_TIMEOUT
     */
    public Timeout getSoTimeout() {
        return soTimeout;
    }

    /**
     * Determines the default value of the {@link java.net.SocketOptions#SO_REUSEADDR} parameter
     * for newly created sockets.
     * <p>
     * Default: {@code false}
     * </p>
     *
     * @see java.net.SocketOptions#SO_REUSEADDR
     */
    public boolean isSoReuseAddress() {
        return soReuseAddress;
    }

    /**
     * Determines the default value of the {@link java.net.SocketOptions#SO_LINGER} parameter
     * for newly created sockets.
     * <p>
     * Default: {@code -1}
     * </p>
     *
     * @see java.net.SocketOptions#SO_LINGER
     */
    public TimeValue getSoLinger() {
        return soLinger;
    }

    /**
     * Determines the default value of the {@link java.net.SocketOptions#SO_KEEPALIVE} parameter
     * for newly created sockets.
     * <p>
     * Default: {@code -1}
     * </p>
     *
     * @see java.net.SocketOptions#SO_KEEPALIVE
     */
    public boolean isSoKeepalive() {
        return this.soKeepAlive;
    }

    /**
     * Determines the default value of the {@link java.net.SocketOptions#TCP_NODELAY} parameter
     * for newly created sockets.
     * <p>
     * Default: {@code false}
     * </p>
     *
     * @see java.net.SocketOptions#TCP_NODELAY
     */
    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    /**
     * Determines the default value of the {@link java.net.SocketOptions#SO_SNDBUF} parameter
     * for newly created sockets.
     * <p>
     * Default: {@code 0} (system default)
     * </p>
     *
     * @see java.net.SocketOptions#SO_SNDBUF
     */
    public int getSndBufSize() {
        return sndBufSize;
    }

    /**
     * Determines the default value of the {@link java.net.SocketOptions#SO_RCVBUF} parameter
     * for newly created sockets.
     * <p>
     * Default: {@code 0} (system default)
     * </p>
     *
     * @see java.net.SocketOptions#SO_RCVBUF
     */
    public int getRcvBufSize() {
        return rcvBufSize;
    }

    /**
     * Determines the default backlog size value for server sockets binds.
     * <p>
     * Default: {@code 0} (system default)
     * </p>
     *
     * @since 4.4
     */
    public int getBacklogSize() {
        return backlogSize;
    }

    public static Builder custom() {
        return new Builder();
    }

    public static Builder copy(final IOReactorConfig config) {
        Args.notNull(config, "I/O reactor config");
        return new Builder()
            .setSelectIntervalMillis(config.getSelectIntervalMillis())
            .setIoThreadCount(config.getIoThreadCount())
            .setSoTimeout(config.getSoTimeout())
            .setSoReuseAddress(config.isSoReuseAddress())
            .setSoLinger(config.getSoLinger())
            .setSoKeepAlive(config.isSoKeepalive())
            .setTcpNoDelay(config.isTcpNoDelay())
            .setSndBufSize(config.getSndBufSize())
            .setRcvBufSize(config.getRcvBufSize())
            .setBacklogSize(config.getBacklogSize());
    }

    public static class Builder {

        private static int DefaultMaxIoThreadCount = -1;

        /**
         * Gets the default value for {@code ioThreadCount}. Returns
         * {@link Runtime#availableProcessors()} if
         * {@link #setDefaultMaxIoThreadCount(int)} was called with a value <=0.
         *
         * @return the default value for ioThreadCount.
         * @since 4.4.10
         */
        public static int getDefaultMaxIoThreadCount() {
            return DefaultMaxIoThreadCount > 0 ? DefaultMaxIoThreadCount : Runtime.getRuntime().availableProcessors();
        }

        /**
         * Sets the default value for {@code ioThreadCount}. Use a value <= 0 to
         * cause {@link #getDefaultMaxIoThreadCount()} to return
         * {@link Runtime#availableProcessors()}.
         *
         * @param defaultMaxIoThreadCount
         *            the default value for ioThreadCount.
         * @since 4.4.10
         */
        public static void setDefaultMaxIoThreadCount(final int defaultMaxIoThreadCount) {
            DefaultMaxIoThreadCount = defaultMaxIoThreadCount;
        }

        private long selectIntervalMillis;
        private int ioThreadCount;
        private Timeout  soTimeout;
        private boolean soReuseAddress;
        private TimeValue soLinger;
        private boolean soKeepAlive;
        private boolean tcpNoDelay;
        private int sndBufSize;
        private int rcvBufSize;
        private int backlogSize;

        Builder() {
            this.selectIntervalMillis = 1000;
            this.ioThreadCount = Builder.getDefaultMaxIoThreadCount();
            this.soTimeout = Timeout.ZERO_MILLISECONDS;
            this.soReuseAddress = false;
            this.soLinger = TimeValue.NEG_ONE_SECONDS;
            this.soKeepAlive = false;
            this.tcpNoDelay = true;
            this.sndBufSize = 0;
            this.rcvBufSize = 0;
            this.backlogSize = 0;
        }

        public Builder setSelectIntervalMillis(final long selectIntervalMillis) {
            this.selectIntervalMillis = selectIntervalMillis;
            return this;
        }

        public Builder setIoThreadCount(final int ioThreadCount) {
            this.ioThreadCount = ioThreadCount;
            return this;
        }

        public Builder setSoTimeout(final int soTimeout, final TimeUnit timeUnit) {
            this.soTimeout = Timeout.of(soTimeout, timeUnit);
            return this;
        }

        public Builder setSoTimeout(final Timeout soTimeout) {
            this.soTimeout = soTimeout;
            return this;
        }

        public Builder setSoReuseAddress(final boolean soReuseAddress) {
            this.soReuseAddress = soReuseAddress;
            return this;
        }

        public Builder setSoLinger(final int soLinger, final TimeUnit timeUnit) {
            this.soLinger = TimeValue.of(soLinger, timeUnit);
            return this;
        }

        public Builder setSoLinger(final TimeValue soLinger) {
            this.soLinger = soLinger;
            return this;
        }

        public Builder setSoKeepAlive(final boolean soKeepAlive) {
            this.soKeepAlive = soKeepAlive;
            return this;
        }

        public Builder setTcpNoDelay(final boolean tcpNoDelay) {
            this.tcpNoDelay = tcpNoDelay;
            return this;
        }

        public Builder setSndBufSize(final int sndBufSize) {
            this.sndBufSize = sndBufSize;
            return this;
        }

        public Builder setRcvBufSize(final int rcvBufSize) {
            this.rcvBufSize = rcvBufSize;
            return this;
        }

        public Builder setBacklogSize(final int backlogSize) {
            this.backlogSize = backlogSize;
            return this;
        }

        public IOReactorConfig build() {
            return new IOReactorConfig(
                    selectIntervalMillis, ioThreadCount,
                    Timeout.defaultsToDisabled(soTimeout),
                    soReuseAddress,
                    TimeValue.defaultsToNegativeOneMillisecond(soLinger),
                    soKeepAlive,
                    tcpNoDelay,
                    sndBufSize, rcvBufSize, backlogSize);
        }

    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("[selectIntervalMillis=").append(this.selectIntervalMillis)
                .append(", ioThreadCount=").append(this.ioThreadCount)
                .append(", soTimeout=").append(this.soTimeout)
                .append(", soReuseAddress=").append(this.soReuseAddress)
                .append(", soLinger=").append(this.soLinger)
                .append(", soKeepAlive=").append(this.soKeepAlive)
                .append(", tcpNoDelay=").append(this.tcpNoDelay)
                .append(", sndBufSize=").append(this.sndBufSize)
                .append(", rcvBufSize=").append(this.rcvBufSize)
                .append(", backlogSize=").append(this.backlogSize)
                .append("]");
        return builder.toString();
    }

}

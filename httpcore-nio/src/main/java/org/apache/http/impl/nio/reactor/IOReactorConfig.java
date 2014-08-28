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

package org.apache.http.impl.nio.reactor;

import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.util.Args;

/**
 * I/O reactor configuration parameters.
 *
 * @since 4.2
 */
@NotThreadSafe
public final class IOReactorConfig implements Cloneable {

    private static final int AVAIL_PROCS = Runtime.getRuntime().availableProcessors();

    public static final IOReactorConfig DEFAULT = new Builder().build();

    // TODO: make final
    private long selectInterval;
    private long shutdownGracePeriod;
    private boolean interestOpQueued;
    private int ioThreadCount;
    private int soTimeout;
    private boolean soReuseAddress;
    private int soLinger;
    private boolean soKeepAlive;
    private boolean tcpNoDelay;
    private int connectTimeout;
    private int sndBufSize;
    private int rcvBufSize;
    private int backlogSize;

    @Deprecated
    public IOReactorConfig() {
        super();
        this.selectInterval = 1000;
        this.shutdownGracePeriod = 500;
        this.interestOpQueued = false;
        this.ioThreadCount = AVAIL_PROCS;
        this.soTimeout = 0;
        this.soReuseAddress = false;
        this.soLinger = -1;
        this.soKeepAlive = false;
        this.tcpNoDelay = true;
        this.connectTimeout = 0;
        this.sndBufSize = 0;
        this.rcvBufSize = 0;
        this.backlogSize = 0;
    }

    IOReactorConfig(
            final long selectInterval,
            final long shutdownGracePeriod,
            final boolean interestOpQueued,
            final int ioThreadCount,
            final int soTimeout,
            final boolean soReuseAddress,
            final int soLinger,
            final boolean soKeepAlive,
            final boolean tcpNoDelay,
            final int connectTimeout,
            final int sndBufSize,
            final int rcvBufSize,
            final int backlogSize) {
        super();
        this.selectInterval = selectInterval;
        this.shutdownGracePeriod = shutdownGracePeriod;
        this.interestOpQueued = interestOpQueued;
        this.ioThreadCount = ioThreadCount;
        this.soTimeout = soTimeout;
        this.soReuseAddress = soReuseAddress;
        this.soLinger = soLinger;
        this.soKeepAlive = soKeepAlive;
        this.tcpNoDelay = tcpNoDelay;
        this.connectTimeout = connectTimeout;
        this.sndBufSize = sndBufSize;
        this.rcvBufSize = rcvBufSize;
        this.backlogSize = backlogSize;
    }

    /**
     * Determines time interval in milliseconds at which the I/O reactor wakes up to check for
     * timed out sessions and session requests.
     * <p>
     * Default: {@code 1000} milliseconds.
     */
    public long getSelectInterval() {
        return this.selectInterval;
    }

    /**
     * @deprecated (4.3) use {@link Builder#setSelectInterval(long)}
     */
    @Deprecated
    public void setSelectInterval(final long selectInterval) {
        Args.positive(selectInterval, "Select internal");
        this.selectInterval = selectInterval;
    }

    /**
     * Determines grace period in milliseconds the I/O reactors are expected to block waiting
     * for individual worker threads to terminate cleanly.
     * <p>
     * Default: {@code 500} milliseconds.
     */
    public long getShutdownGracePeriod() {
        return this.shutdownGracePeriod;
    }

    /**
     * @deprecated (4.3) use {@link Builder#setShutdownGracePeriod(long)}
     */
    @Deprecated
    public void setShutdownGracePeriod(final long gracePeriod) {
        Args.positive(gracePeriod, "Shutdown grace period");
        this.shutdownGracePeriod = gracePeriod;
    }

    /**
     * Determines whether or not I/O interest operations are to be queued and executed
     * asynchronously by the I/O reactor thread or to be applied to the underlying
     * {@link java.nio.channels.SelectionKey} immediately.
     * <p>
     * Default: {@code false}
     *
     * @see java.nio.channels.SelectionKey
     * @see java.nio.channels.SelectionKey#interestOps()
     * @see java.nio.channels.SelectionKey#interestOps(int)
     */
    public boolean isInterestOpQueued() {
        return this.interestOpQueued;
    }

    /**
     * @deprecated (4.3) use {@link Builder#setInterestOpQueued(boolean)}
     */
    @Deprecated
    public void setInterestOpQueued(final boolean interestOpQueued) {
        this.interestOpQueued = interestOpQueued;
    }

    /**
     * Determines the number of I/O dispatch threads to be used by the I/O reactor.
     * <p>
     * Default: {@code 2}
     */
    public int getIoThreadCount() {
        return this.ioThreadCount;
    }

    /**
     * @deprecated (4.3) use {@link Builder#setIoThreadCount(int)}
     */
    @Deprecated
    public void setIoThreadCount(final int ioThreadCount) {
        Args.positive(ioThreadCount, "I/O thread count");
        this.ioThreadCount = ioThreadCount;
    }

    /**
     * Determines the default socket timeout value for non-blocking I/O operations.
     * <p>
     * Default: {@code 0} (no timeout)
     *
     * @see java.net.SocketOptions#SO_TIMEOUT
     */
    public int getSoTimeout() {
        return soTimeout;
    }

    /**
     * @deprecated (4.3) use {@link Builder#setSoTimeout(int)}
     */
    @Deprecated
    public void setSoTimeout(final int soTimeout) {
        this.soTimeout = soTimeout;
    }

    /**
     * Determines the default value of the {@link java.net.SocketOptions#SO_REUSEADDR} parameter
     * for newly created sockets.
     * <p>
     * Default: {@code false}
     *
     * @see java.net.SocketOptions#SO_REUSEADDR
     */
    public boolean isSoReuseAddress() {
        return soReuseAddress;
    }

    /**
     * @deprecated (4.3) use {@link Builder#setSoReuseAddress(boolean)}
     */
    @Deprecated
    public void setSoReuseAddress(final boolean soReuseAddress) {
        this.soReuseAddress = soReuseAddress;
    }

    /**
     * Determines the default value of the {@link java.net.SocketOptions#SO_LINGER} parameter
     * for newly created sockets.
     * <p>
     * Default: {@code -1}
     *
     * @see java.net.SocketOptions#SO_LINGER
     */
    public int getSoLinger() {
        return soLinger;
    }

    /**
     * @deprecated (4.3) use {@link Builder#setSoLinger(int)}
     */
    @Deprecated
    public void setSoLinger(final int soLinger) {
        this.soLinger = soLinger;
    }

    /**
     * Determines the default value of the {@link java.net.SocketOptions#SO_KEEPALIVE} parameter
     * for newly created sockets.
     * <p>
     * Default: {@code -1}
     *
     * @see java.net.SocketOptions#SO_KEEPALIVE
     */
    public boolean isSoKeepalive() {
        return this.soKeepAlive;
    }

    /**
     * @deprecated (4.3) use {@link Builder#setSoKeepAlive(boolean)}
     */
    @Deprecated
    public void setSoKeepalive(final boolean soKeepAlive) {
        this.soKeepAlive = soKeepAlive;
    }

    /**
     * Determines the default value of the {@link java.net.SocketOptions#TCP_NODELAY} parameter
     * for newly created sockets.
     * <p>
     * Default: {@code false}
     *
     * @see java.net.SocketOptions#TCP_NODELAY
     */
    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    /**
     * @deprecated (4.3) use {@link Builder#setTcpNoDelay(boolean)}
     */
    @Deprecated
    public void setTcpNoDelay(final boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    /**
     * Determines the default connect timeout value for non-blocking connection requests.
     * <p>
     * Default: {@code 0} (no timeout)
     */
    public int getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * @deprecated (4.3) use {@link Builder#setConnectTimeout(int)}
     */
    @Deprecated
    public void setConnectTimeout(final int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /**
     * Determines the default value of the {@link java.net.SocketOptions#SO_SNDBUF} parameter
     * for newly created sockets.
     * <p>
     * Default: {@code 0} (system default)
     *
     * @see java.net.SocketOptions#SO_SNDBUF
     */
    public int getSndBufSize() {
        return sndBufSize;
    }

    /**
     * @deprecated (4.3) use {@link Builder#setSndBufSize(int)}
     */
    @Deprecated
    public void setSndBufSize(final int sndBufSize) {
        this.sndBufSize = sndBufSize;
    }

    /**
     * Determines the default value of the {@link java.net.SocketOptions#SO_RCVBUF} parameter
     * for newly created sockets.
     * <p>
     * Default: {@code 0} (system default)
     *
     * @see java.net.SocketOptions#SO_RCVBUF
     */
    public int getRcvBufSize() {
        return rcvBufSize;
    }

    /**
     * @deprecated (4.3) use {@link Builder#setRcvBufSize(int)}
     */
    @Deprecated
    public void setRcvBufSize(final int rcvBufSize) {
        this.rcvBufSize = rcvBufSize;
    }

    /**
     * Determines the default backlog size value for server sockets binds.
     * <p>
     * Default: {@code 0} (system default)
     *
     * @since 4.4
     */
    public int getBacklogSize() {
        return backlogSize;
    }

    @Override
    protected IOReactorConfig clone() throws CloneNotSupportedException {
        return (IOReactorConfig) super.clone();
    }

    public static Builder custom() {
        return new Builder();
    }

    public static Builder copy(final IOReactorConfig config) {
        Args.notNull(config, "I/O reactor config");
        return new Builder()
            .setSelectInterval(config.getSelectInterval())
            .setShutdownGracePeriod(config.getShutdownGracePeriod())
            .setInterestOpQueued(config.isInterestOpQueued())
            .setIoThreadCount(config.getIoThreadCount())
            .setSoTimeout(config.getSoTimeout())
            .setSoReuseAddress(config.isSoReuseAddress())
            .setSoLinger(config.getSoLinger())
            .setSoKeepAlive(config.isSoKeepalive())
            .setTcpNoDelay(config.isTcpNoDelay())
            .setConnectTimeout(config.getConnectTimeout())
            .setSndBufSize(config.getSndBufSize())
            .setRcvBufSize(config.getRcvBufSize())
            .setBacklogSize(config.getBacklogSize());
    }

    public static class Builder {

        private long selectInterval;
        private long shutdownGracePeriod;
        private boolean interestOpQueued;
        private int ioThreadCount;
        private int soTimeout;
        private boolean soReuseAddress;
        private int soLinger;
        private boolean soKeepAlive;
        private boolean tcpNoDelay;
        private int connectTimeout;
        private int sndBufSize;
        private int rcvBufSize;
        private int backlogSize;

        Builder() {
            this.selectInterval = 1000;
            this.shutdownGracePeriod = 500;
            this.interestOpQueued = false;
            this.ioThreadCount = AVAIL_PROCS;
            this.soTimeout = 0;
            this.soReuseAddress = false;
            this.soLinger = -1;
            this.soKeepAlive = false;
            this.tcpNoDelay = true;
            this.connectTimeout = 0;
            this.sndBufSize = 0;
            this.rcvBufSize = 0;
            this.backlogSize = 0;
        }

        public Builder setSelectInterval(final long selectInterval) {
            this.selectInterval = selectInterval;
            return this;
        }

        public Builder setShutdownGracePeriod(final long shutdownGracePeriod) {
            this.shutdownGracePeriod = shutdownGracePeriod;
            return this;
        }

        public Builder setInterestOpQueued(final boolean interestOpQueued) {
            this.interestOpQueued = interestOpQueued;
            return this;
        }

        public Builder setIoThreadCount(final int ioThreadCount) {
            this.ioThreadCount = ioThreadCount;
            return this;
        }

        public Builder setSoTimeout(final int soTimeout) {
            this.soTimeout = soTimeout;
            return this;
        }

        public Builder setSoReuseAddress(final boolean soReuseAddress) {
            this.soReuseAddress = soReuseAddress;
            return this;
        }

        public Builder setSoLinger(final int soLinger) {
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

        public Builder setConnectTimeout(final int connectTimeout) {
            this.connectTimeout = connectTimeout;
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
                    selectInterval, shutdownGracePeriod, interestOpQueued, ioThreadCount,
                    soTimeout, soReuseAddress, soLinger, soKeepAlive, tcpNoDelay,
                    connectTimeout, sndBufSize, rcvBufSize, backlogSize);
        }

    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("[selectInterval=").append(this.selectInterval)
                .append(", shutdownGracePeriod=").append(this.shutdownGracePeriod)
                .append(", interestOpQueued=").append(this.interestOpQueued)
                .append(", ioThreadCount=").append(this.ioThreadCount)
                .append(", soTimeout=").append(this.soTimeout)
                .append(", soReuseAddress=").append(this.soReuseAddress)
                .append(", soLinger=").append(this.soLinger)
                .append(", soKeepAlive=").append(this.soKeepAlive)
                .append(", tcpNoDelay=").append(this.tcpNoDelay)
                .append(", connectTimeout=").append(this.connectTimeout)
                .append(", sndBufSize=").append(this.sndBufSize)
                .append(", rcvBufSize=").append(this.rcvBufSize)
                .append(", backlogSize=").append(this.backlogSize)
                .append("]");
        return builder.toString();
    }

}

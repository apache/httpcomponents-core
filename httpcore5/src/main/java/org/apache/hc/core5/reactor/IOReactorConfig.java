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

import java.net.SocketAddress;
import java.net.SocketOptions;
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

    private final TimeValue selectInterval;
    private final int ioThreadCount;
    private final Timeout soTimeout;
    private final boolean soReuseAddress;
    private final TimeValue soLinger;
    private final boolean soKeepAlive;
    private final boolean tcpNoDelay;
    private final int trafficClass;
    private final int sndBufSize;
    private final int rcvBufSize;
    private final int backlogSize;
    private final int tcpKeepIdle;
    private final int tcpKeepInterval;
    private final int tcpKeepCount;
    private final SocketAddress socksProxyAddress;
    private final String socksProxyUsername;
    private final String socksProxyPassword;

    IOReactorConfig(
            final TimeValue selectInterval,
            final int ioThreadCount,
            final Timeout soTimeout,
            final boolean soReuseAddress,
            final TimeValue soLinger,
            final boolean soKeepAlive,
            final boolean tcpNoDelay,
            final int trafficClass,
            final int sndBufSize,
            final int rcvBufSize,
            final int backlogSize,
            final int tcpKeepIdle,
            final int tcpKeepInterval,
            final int tcpKeepCount,
            final SocketAddress socksProxyAddress,
            final String socksProxyUsername,
            final String socksProxyPassword) {
        super();
        this.selectInterval = selectInterval;
        this.ioThreadCount = ioThreadCount;
        this.soTimeout = soTimeout;
        this.soReuseAddress = soReuseAddress;
        this.soLinger = soLinger;
        this.soKeepAlive = soKeepAlive;
        this.tcpNoDelay = tcpNoDelay;
        this.trafficClass = trafficClass;
        this.sndBufSize = sndBufSize;
        this.rcvBufSize = rcvBufSize;
        this.backlogSize = backlogSize;
        this.tcpKeepIdle = tcpKeepIdle;
        this.tcpKeepInterval = tcpKeepInterval;
        this.tcpKeepCount = tcpKeepCount;
        this.socksProxyAddress = socksProxyAddress;
        this.socksProxyUsername = socksProxyUsername;
        this.socksProxyPassword = socksProxyPassword;
    }

    /**
     * @see Builder#setSelectInterval(TimeValue)
     */
    public TimeValue getSelectInterval() {
        return this.selectInterval;
    }

    /**
     * @see Builder#setIoThreadCount(int)
     */
    public int getIoThreadCount() {
        return this.ioThreadCount;
    }

    /**
     * @see Builder#setSoTimeout(Timeout)
     */
    public Timeout getSoTimeout() {
        return soTimeout;
    }

    /**
     * @see Builder#setSoReuseAddress(boolean)
     */
    public boolean isSoReuseAddress() {
        return soReuseAddress;
    }

    /**
     * @see Builder#setSoLinger(TimeValue)
     */
    public TimeValue getSoLinger() {
        return soLinger;
    }

    /**
     * @see Builder#setSoKeepAlive(boolean)
     * @since 5.2
     */
    public boolean isSoKeepAlive() {
        return this.soKeepAlive;
    }

    /**
     * @see Builder#setSoKeepAlive(boolean)
     * @deprecated use {@link #isSoKeepAlive()}.
     */
    @Deprecated
    public boolean isSoKeepalive() {
        return this.soKeepAlive;
    }

    /**
     * @see Builder#setTcpNoDelay(boolean)
     */
    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    /**
     * @see Builder#setTrafficClass(int)
     *
     * @since 5.1
     */
    public int getTrafficClass() {
        return trafficClass;
    }

    /**
     * @see Builder#setSndBufSize(int)
     */
    public int getSndBufSize() {
        return sndBufSize;
    }

    /**
     * @see Builder#setRcvBufSize(int)
     */
    public int getRcvBufSize() {
        return rcvBufSize;
    }

    /**
     * @see Builder#setBacklogSize(int)
     */
    public int getBacklogSize() {
        return backlogSize;
    }

    /**
     * @see Builder#setTcpKeepIdle(int)
     *
     * @since 5.3
     */
    public int getTcpKeepIdle() {
        return this.tcpKeepIdle;
    }

    /**
     * @see Builder#setTcpKeepInterval(int)
     *
     * @since 5.3
     */
    public int getTcpKeepInterval() {
        return this.tcpKeepInterval;
    }

    /**
     * @see Builder#setTcpKeepCount(int)
     *
     * @since 5.3
     */
    public int getTcpKeepCount() {
        return this.tcpKeepCount;
    }

    /**
     * @see Builder#setSocksProxyAddress(SocketAddress)
     */
    public SocketAddress getSocksProxyAddress() {
        return this.socksProxyAddress;
    }

    /**
     * @see Builder#setSocksProxyUsername(String)
     */
    public String getSocksProxyUsername() {
        return this.socksProxyUsername;
    }

    /**
     * @see Builder#setSocksProxyAddress(SocketAddress)
     */
    public String getSocksProxyPassword() {
        return this.socksProxyPassword;
    }

    public static Builder custom() {
        return new Builder();
    }

    public static Builder copy(final IOReactorConfig config) {
        Args.notNull(config, "I/O reactor config");
        return new Builder()
            .setSelectInterval(config.getSelectInterval())
            .setIoThreadCount(config.getIoThreadCount())
            .setSoTimeout(config.getSoTimeout())
            .setSoReuseAddress(config.isSoReuseAddress())
            .setSoLinger(config.getSoLinger())
            .setSoKeepAlive(config.isSoKeepAlive())
            .setTcpNoDelay(config.isTcpNoDelay())
            .setSndBufSize(config.getSndBufSize())
            .setRcvBufSize(config.getRcvBufSize())
            .setBacklogSize(config.getBacklogSize())
            .setTcpKeepIdle(config.getTcpKeepIdle())
            .setTcpKeepInterval(config.getTcpKeepInterval())
            .setTcpKeepCount(config.getTcpKeepCount())
            .setSocksProxyAddress(config.getSocksProxyAddress())
            .setSocksProxyUsername(config.getSocksProxyUsername())
            .setSocksProxyPassword(config.getSocksProxyPassword());
    }

    public static class Builder {

        private static int defaultMaxIOThreadCount = -1;

        /**
         * Gets the default value for {@code ioThreadCount}. Returns
         * {@link Runtime#availableProcessors()} if
         * {@link #setDefaultMaxIOThreadCount(int)} was called with a value less &lt;= 0.
         *
         * @return the default value for ioThreadCount.
         * @since 4.4.10
         */
        public static int getDefaultMaxIOThreadCount() {
            return defaultMaxIOThreadCount > 0 ? defaultMaxIOThreadCount : Runtime.getRuntime().availableProcessors();
        }

        /**
         * Sets the default value for {@code ioThreadCount}. Use a value &lt;= 0 to
         * cause {@link #getDefaultMaxIOThreadCount()} to return
         * {@link Runtime#availableProcessors()}.
         *
         * @param defaultMaxIOThreadCount
         *            the default value for ioThreadCount.
         * @since 4.4.10
         */
        public static void setDefaultMaxIOThreadCount(final int defaultMaxIOThreadCount) {
            Builder.defaultMaxIOThreadCount = defaultMaxIOThreadCount;
        }

        private TimeValue selectInterval;
        private int ioThreadCount;
        private Timeout soTimeout;
        private boolean soReuseAddress;
        private TimeValue soLinger;
        private boolean soKeepAlive;
        private boolean tcpNoDelay;
        private int trafficClass;
        private int sndBufSize;
        private int rcvBufSize;
        private int backlogSize;
        private int tcpKeepIdle;
        private int tcpKeepInterval;
        private int tcpKeepCount;
        private SocketAddress socksProxyAddress;
        private String socksProxyUsername;
        private String socksProxyPassword;

        Builder() {
            this.selectInterval = TimeValue.ofSeconds(1);
            this.ioThreadCount = Builder.getDefaultMaxIOThreadCount();
            this.soTimeout = Timeout.ZERO_MILLISECONDS;
            this.soReuseAddress = false;
            this.soLinger = TimeValue.NEG_ONE_SECOND;
            this.soKeepAlive = false;
            this.tcpNoDelay = true;
            this.trafficClass = 0;
            this.sndBufSize = 0;
            this.rcvBufSize = 0;
            this.backlogSize = 0;
            this.tcpKeepIdle = -1;
            this.tcpKeepInterval = -1;
            this.tcpKeepCount = -1;
            this.socksProxyAddress = null;
            this.socksProxyUsername = null;
            this.socksProxyPassword = null;
        }

        /**
         * Sets time interval at which the I/O reactor wakes up to check for timed out sessions
         * and session requests.
         * <p>
         * Default: {@code 1000} milliseconds.
         * </p>
         *
         * @return this instance.
         */
        public Builder setSelectInterval(final TimeValue selectInterval) {
            this.selectInterval = selectInterval;
            return this;
        }

        /**
         * Sets the number of I/O dispatch threads to be used by the I/O reactor.
         * <p>
         * Default: {@code 2}
         * </p>
         *
         * @return this instance.
         */
        public Builder setIoThreadCount(final int ioThreadCount) {
            this.ioThreadCount = ioThreadCount;
            return this;
        }

        /**
         * Sets the default socket timeout value for non-blocking I/O operations.
         * <p>
         * Default: {@code 0} (no timeout)
         * </p>
         *
         * @return this instance.
         * @see SocketOptions#SO_TIMEOUT
         */
        public Builder setSoTimeout(final int soTimeout, final TimeUnit timeUnit) {
            this.soTimeout = Timeout.of(soTimeout, timeUnit);
            return this;
        }

        /**
         * Sets the default socket timeout value for non-blocking I/O operations.
         * <p>
         * Default: {@code 0} (no timeout)
         * </p>
         *
         * @return this instance.
         * @see SocketOptions#SO_TIMEOUT
         */
        public Builder setSoTimeout(final Timeout soTimeout) {
            this.soTimeout = soTimeout;
            return this;
        }

        /**
         * Sets the default value of the {@link SocketOptions#SO_REUSEADDR} parameter
         * for newly created sockets.
         * <p>
         * Default: {@code false}
         * </p>
         *
         * @return this instance.
         * @see SocketOptions#SO_REUSEADDR
         */
        public Builder setSoReuseAddress(final boolean soReuseAddress) {
            this.soReuseAddress = soReuseAddress;
            return this;
        }

        /**
         * Sets the default value of the {@link SocketOptions#SO_LINGER} parameter
         * for newly created sockets.
         * <p>
         * Default: {@code -1}
         * </p>
         *
         * @return this instance.
         * @see SocketOptions#SO_LINGER
         */
        public Builder setSoLinger(final int soLinger, final TimeUnit timeUnit) {
            this.soLinger = TimeValue.of(soLinger, timeUnit);
            return this;
        }

        /**
         * Sets the default value of the {@link SocketOptions#SO_LINGER} parameter
         * for newly created sockets.
         * <p>
         * Default: {@code -1}
         * </p>
         *
         * @return this instance.
         * @see SocketOptions#SO_LINGER
         */
        public Builder setSoLinger(final TimeValue soLinger) {
            this.soLinger = soLinger;
            return this;
        }

        /**
         * Sets the default value of the {@link SocketOptions#SO_KEEPALIVE} parameter
         * for newly created sockets.
         * <p>
         * Default: {@code -1}
         * </p>
         *
         * @return this instance.
         * @see SocketOptions#SO_KEEPALIVE
         */
        public Builder setSoKeepAlive(final boolean soKeepAlive) {
            this.soKeepAlive = soKeepAlive;
            return this;
        }

        /**
         * Sets the default value of the {@link SocketOptions#TCP_NODELAY} parameter
         * for newly created sockets.
         * <p>
         * Default: {@code false}
         * </p>
         *
         * @return this instance.
         * @see SocketOptions#TCP_NODELAY
         */
        public Builder setTcpNoDelay(final boolean tcpNoDelay) {
            this.tcpNoDelay = tcpNoDelay;
            return this;
        }

        /**
         * Sets the default value of the {@link SocketOptions#IP_TOS} parameter
         * for newly created sockets.
         * <p>
         * Default: {@code 0}
         * </p>
         *
         * @return this instance.
         * @see SocketOptions#IP_TOS
         *
         * @since 5.1
         */
        public Builder setTrafficClass(final int trafficClass) {
            this.trafficClass = trafficClass;
            return this;
        }

        /**
         * Sets the default value of the {@link SocketOptions#SO_SNDBUF} parameter
         * for newly created sockets.
         * <p>
         * Default: {@code 0} (system default)
         * </p>
         *
         * @return this instance.
         * @see SocketOptions#SO_SNDBUF
         */
        public Builder setSndBufSize(final int sndBufSize) {
            this.sndBufSize = sndBufSize;
            return this;
        }

        /**
         * Sets the default value of the {@link SocketOptions#SO_RCVBUF} parameter
         * for newly created sockets.
         * <p>
         * Default: {@code 0} (system default)
         * </p>
         *
         * @return this instance.
         * @see SocketOptions#SO_RCVBUF
         */
        public Builder setRcvBufSize(final int rcvBufSize) {
            this.rcvBufSize = rcvBufSize;
            return this;
        }

        /**
         * Sets the default backlog size value for server sockets binds.
         * <p>
         * Default: {@code 0} (system default)
         * </p>
         *
         * @return this instance.
         * @since 4.4
         */
        public Builder setBacklogSize(final int backlogSize) {
            this.backlogSize = backlogSize;
            return this;
        }

        /**
         * Sets the time (in seconds) the connection needs to remain idle before TCP starts
         * sending keepalive probes.
         *
         * @return this instance.
         * @since 5.3
         */
        public Builder setTcpKeepIdle(final int tcpKeepIdle) {
            this.tcpKeepIdle = tcpKeepIdle;
            return this;
        }

        /**
         * Sets the time (in seconds) between individual keepalive probes.
         *
         * @return this instance.
         * @since 5.3
         */
        public Builder setTcpKeepInterval(final int tcpKeepInterval) {
            this.tcpKeepInterval = tcpKeepInterval;
            return this;
        }

        /**
         * Sets the maximum number of keepalive probes TCP should send before dropping the connection.
         *
         * @return this instance.
         * @since 5.3
         */
        public Builder setTcpKeepCount(final int tcpKeepCount) {
            this.tcpKeepCount = tcpKeepCount;
            return this;
        }

        /**
         * Sets the address of the SOCKS proxy to use.
         *
         * @return this instance.
         */
        public Builder setSocksProxyAddress(final SocketAddress socksProxyAddress) {
            this.socksProxyAddress = socksProxyAddress;
            return this;
        }

        /**
         * Sets the username to provide to the SOCKS proxy for username/password authentication.
         *
         * @return this instance.
         */
        public Builder setSocksProxyUsername(final String socksProxyUsername) {
            this.socksProxyUsername = socksProxyUsername;
            return this;
        }

        /**
         * Sets the password to provide to the SOCKS proxy for username/password authentication.
         *
         * @return this instance.
         */
        public Builder setSocksProxyPassword(final String socksProxyPassword) {
            this.socksProxyPassword = socksProxyPassword;
            return this;
        }

        public IOReactorConfig build() {
            return new IOReactorConfig(
                    selectInterval != null ? selectInterval : TimeValue.ofSeconds(1),
                    ioThreadCount,
                    Timeout.defaultsToInfinite(soTimeout),
                    soReuseAddress,
                    TimeValue.defaultsToNegativeOneMillisecond(soLinger),
                    soKeepAlive,
                    tcpNoDelay,
                    trafficClass,
                    sndBufSize, rcvBufSize, backlogSize,
                    tcpKeepIdle, tcpKeepInterval, tcpKeepCount,
                    socksProxyAddress, socksProxyUsername, socksProxyPassword);
        }

    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("[selectInterval=").append(this.selectInterval)
                .append(", ioThreadCount=").append(this.ioThreadCount)
                .append(", soTimeout=").append(this.soTimeout)
                .append(", soReuseAddress=").append(this.soReuseAddress)
                .append(", soLinger=").append(this.soLinger)
                .append(", soKeepAlive=").append(this.soKeepAlive)
                .append(", tcpNoDelay=").append(this.tcpNoDelay)
                .append(", trafficClass=").append(this.trafficClass)
                .append(", sndBufSize=").append(this.sndBufSize)
                .append(", rcvBufSize=").append(this.rcvBufSize)
                .append(", backlogSize=").append(this.backlogSize)
                .append(", socksProxyAddress=").append(this.socksProxyAddress)
                .append("]");
        return builder.toString();
    }

}

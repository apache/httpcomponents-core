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

package org.apache.hc.core5.http.io;

import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * Classic I/O network socket configuration.
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class SocketConfig {

    private static final Timeout DEFAULT_SOCKET_TIMEOUT = Timeout.ofMinutes(3);

    public static final SocketConfig DEFAULT = new Builder().build();

    private final Timeout soTimeout;
    private final boolean soReuseAddress;
    private final TimeValue soLinger;
    private final boolean soKeepAlive;
    private final boolean tcpNoDelay;
    private final int sndBufSize;
    private final int rcvBufSize;
    private final int backlogSize;
    private final SocketAddress socksProxyAddress;

    SocketConfig(
            final Timeout soTimeout,
            final boolean soReuseAddress,
            final TimeValue soLinger,
            final boolean soKeepAlive,
            final boolean tcpNoDelay,
            final int sndBufSize,
            final int rcvBufSize,
            final int backlogSize,
            final SocketAddress socksProxyAddress) {
        super();
        this.soTimeout = soTimeout;
        this.soReuseAddress = soReuseAddress;
        this.soLinger = soLinger;
        this.soKeepAlive = soKeepAlive;
        this.tcpNoDelay = tcpNoDelay;
        this.sndBufSize = sndBufSize;
        this.rcvBufSize = rcvBufSize;
        this.backlogSize = backlogSize;
        this.socksProxyAddress = socksProxyAddress;
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
     */
    public boolean isSoKeepAlive() {
        return soKeepAlive;
    }

    /**
     * @see Builder#setTcpNoDelay(boolean)
     */
    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    /**
     * @see Builder#setSndBufSize(int)
     * @since 4.4
     */
    public int getSndBufSize() {
        return sndBufSize;
    }

    /**
     * @see Builder#setRcvBufSize(int)
     * @since 4.4
     */
    public int getRcvBufSize() {
        return rcvBufSize;
    }

    /**
     * @see Builder#setBacklogSize(int)
     * @since 4.4
     */
    public int getBacklogSize() {
        return backlogSize;
    }

    /**
     * @see Builder#setSocksProxyAddress(SocketAddress)
     */
    public SocketAddress getSocksProxyAddress() {
        return this.socksProxyAddress;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("[soTimeout=").append(this.soTimeout)
                .append(", soReuseAddress=").append(this.soReuseAddress)
                .append(", soLinger=").append(this.soLinger)
                .append(", soKeepAlive=").append(this.soKeepAlive)
                .append(", tcpNoDelay=").append(this.tcpNoDelay)
                .append(", sndBufSize=").append(this.sndBufSize)
                .append(", rcvBufSize=").append(this.rcvBufSize)
                .append(", backlogSize=").append(this.backlogSize)
                .append(", socksProxyAddress=").append(this.socksProxyAddress)
                .append("]");
        return builder.toString();
    }

    public static SocketConfig.Builder custom() {
        return new Builder();
    }

    public static SocketConfig.Builder copy(final SocketConfig config) {
        Args.notNull(config, "Socket config");
        return new Builder()
            .setSoTimeout(config.getSoTimeout())
            .setSoReuseAddress(config.isSoReuseAddress())
            .setSoLinger(config.getSoLinger())
            .setSoKeepAlive(config.isSoKeepAlive())
            .setTcpNoDelay(config.isTcpNoDelay())
            .setSndBufSize(config.getSndBufSize())
            .setRcvBufSize(config.getRcvBufSize())
            .setBacklogSize(config.getBacklogSize())
            .setSocksProxyAddress(config.getSocksProxyAddress());
    }

    public static class Builder {

        private Timeout soTimeout;
        private boolean soReuseAddress;
        private TimeValue soLinger;
        private boolean soKeepAlive;
        private boolean tcpNoDelay;
        private int sndBufSize;
        private int rcvBufSize;
        private int backlogSize;
        private SocketAddress socksProxyAddress;

        Builder() {
            this.soTimeout = DEFAULT_SOCKET_TIMEOUT;
            this.soReuseAddress = false;
            this.soLinger = TimeValue.NEG_ONE_SECOND;
            this.soKeepAlive = false;
            this.tcpNoDelay = true;
            this.sndBufSize = 0;
            this.rcvBufSize = 0;
            this.backlogSize = 0;
            this.socksProxyAddress = null;
        }

        /**
         * @see #setSoTimeout(Timeout)
         */
        public Builder setSoTimeout(final int soTimeout, final TimeUnit timeUnit) {
            this.soTimeout = Timeout.of(soTimeout, timeUnit);
            return this;
        }

        /**
         * Determines the default socket timeout value for blocking I/O operations.
         * <p>
         * Default: 3 minutes
         * </p>
         *
         * @return the default socket timeout value for blocking I/O operations.
         * @see java.net.SocketOptions#SO_TIMEOUT
         */
        public Builder setSoTimeout(final Timeout soTimeout) {
            this.soTimeout = soTimeout;
            return this;
        }

        /**
         * Determines the default value of the {@link java.net.SocketOptions#SO_REUSEADDR} parameter
         * for newly created sockets.
         * <p>
         * Default: {@code false}
         * </p>
         *
         * @return the default value of the {@link java.net.SocketOptions#SO_REUSEADDR} parameter.
         * @see java.net.SocketOptions#SO_REUSEADDR
         */
        public Builder setSoReuseAddress(final boolean soReuseAddress) {
            this.soReuseAddress = soReuseAddress;
            return this;
        }

        /**
         * @see #setSoLinger(TimeValue)
         */
        public Builder setSoLinger(final int soLinger, final TimeUnit timeUnit) {
            this.soLinger = Timeout.of(soLinger, timeUnit);
            return this;
        }

        /**
         * Determines the default value of the {@link java.net.SocketOptions#SO_LINGER} parameter
         * for newly created sockets.
         * <p>
         * Default: {@code -1}
         * </p>
         *
         * @return the default value of the {@link java.net.SocketOptions#SO_LINGER} parameter.
         * @see java.net.SocketOptions#SO_LINGER
         */
        public Builder setSoLinger(final TimeValue soLinger) {
            this.soLinger = soLinger;
            return this;
        }

        /**
         * Determines the default value of the {@link java.net.SocketOptions#SO_KEEPALIVE} parameter
         * for newly created sockets.
         * <p>
         * Default: {@code false}
         * </p>
         *
         * @return the default value of the {@link java.net.SocketOptions#SO_KEEPALIVE} parameter.
         * @see java.net.SocketOptions#SO_KEEPALIVE
         */
        public Builder setSoKeepAlive(final boolean soKeepAlive) {
            this.soKeepAlive = soKeepAlive;
            return this;
        }

        /**
         * Determines the default value of the {@link java.net.SocketOptions#TCP_NODELAY} parameter
         * for newly created sockets.
         * <p>
         * Default: {@code false}
         * </p>
         *
         * @return the default value of the {@link java.net.SocketOptions#TCP_NODELAY} parameter.
         * @see java.net.SocketOptions#TCP_NODELAY
         */
        public Builder setTcpNoDelay(final boolean tcpNoDelay) {
            this.tcpNoDelay = tcpNoDelay;
            return this;
        }

        /**
         * Determines the default value of the {@link java.net.SocketOptions#SO_SNDBUF} parameter
         * for newly created sockets.
         * <p>
         * Default: {@code 0} (system default)
         * </p>
         *
         * @return the default value of the {@link java.net.SocketOptions#SO_SNDBUF} parameter.
         * @see java.net.SocketOptions#SO_SNDBUF
         * @since 4.4
         */
        public Builder setSndBufSize(final int sndBufSize) {
            this.sndBufSize = sndBufSize;
            return this;
        }

        /**
         * Determines the default value of the {@link java.net.SocketOptions#SO_RCVBUF} parameter
         * for newly created sockets.
         * <p>
         * Default: {@code 0} (system default)
         * </p>
         *
         * @return the default value of the {@link java.net.SocketOptions#SO_RCVBUF} parameter.
         * @see java.net.SocketOptions#SO_RCVBUF
         * @since 4.4
         */
        public Builder setRcvBufSize(final int rcvBufSize) {
            this.rcvBufSize = rcvBufSize;
            return this;
        }

        /**
         * Determines the maximum queue length for incoming connection indications
         * (a request to connect) also known as server socket backlog.
         * <p>
         * Default: {@code 0} (system default)
         * </p>
         * @return the maximum queue length for incoming connection indications
         * @since 4.4
         */
        public Builder setBacklogSize(final int backlogSize) {
            this.backlogSize = backlogSize;
            return this;
        }

        /**
         * The address of the SOCKS proxy to use.
         */
        public Builder setSocksProxyAddress(final SocketAddress socksProxyAddress) {
            this.socksProxyAddress = socksProxyAddress;
            return this;
        }

        public SocketConfig build() {
            return new SocketConfig(
                    Timeout.defaultsToDisabled(soTimeout),
                    soReuseAddress,
                    soLinger != null ? soLinger : TimeValue.NEG_ONE_SECOND,
                    soKeepAlive, tcpNoDelay, sndBufSize, rcvBufSize, backlogSize,
                    socksProxyAddress);
        }

    }

}

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

import java.net.SocketOptions;
import java.nio.channels.SelectionKey;

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
    }

    IOReactorConfig(
            long selectInterval,
            long shutdownGracePeriod,
            boolean interestOpQueued,
            int ioThreadCount,
            int soTimeout,
            boolean soReuseAddress,
            int soLinger,
            boolean soKeepAlive,
            boolean tcpNoDelay,
            int connectTimeout) {
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
    }

    /**
     * Determines time interval in milliseconds at which the I/O reactor wakes up to check for
     * timed out sessions and session requests.
     * <p/>
     * Default: <code>1000</code> milliseconds.
     */
    public long getSelectInterval() {
        return this.selectInterval;
    }

    @Deprecated
    public void setSelectInterval(long selectInterval) {
        Args.positive(selectInterval, "Select internal");
        this.selectInterval = selectInterval;
    }

    /**
     * Determines grace period in milliseconds the I/O reactors are expected to block waiting
     * for individual worker threads to terminate cleanly.
     * <p/>
     * Default: <code>500</code> milliseconds.
     */
    public long getShutdownGracePeriod() {
        return this.shutdownGracePeriod;
    }

    @Deprecated
    public void setShutdownGracePeriod(long gracePeriod) {
        Args.positive(gracePeriod, "Shutdown grace period");
        this.shutdownGracePeriod = gracePeriod;
    }

    /**
     * Determines whether or not I/O interest operations are to be queued and executed
     * asynchronously by the I/O reactor thread or to be applied to the underlying
     * {@link SelectionKey} immediately.
     * <p/>
     * Default: <code>false</code>
     *
     * @see SelectionKey
     * @see SelectionKey#interestOps()
     * @see SelectionKey#interestOps(int)
     */
    public boolean isInterestOpQueued() {
        return this.interestOpQueued;
    }

    @Deprecated
    public void setInterestOpQueued(boolean interestOpQueued) {
        this.interestOpQueued = interestOpQueued;
    }

    /**
     * Determines the number of I/O dispatch threads to be used by the I/O reactor.
     * <p/>
     * Default: <code>2</code>
     */
    public int getIoThreadCount() {
        return this.ioThreadCount;
    }

    @Deprecated
    public void setIoThreadCount(int ioThreadCount) {
        Args.positive(ioThreadCount, "I/O thread count");
        this.ioThreadCount = ioThreadCount;
    }

    /**
     * Determines the default socket timeout value for non-blocking I/O operations.
     * <p/>
     * Default: <code>0</code> (no timeout)
     *
     * @see SocketOptions#SO_TIMEOUT
     */
    public int getSoTimeout() {
        return soTimeout;
    }

    @Deprecated
    public void setSoTimeout(int soTimeout) {
        this.soTimeout = soTimeout;
    }

    /**
     * Determines the default value of the {@link SocketOptions#SO_REUSEADDR} parameter
     * for newly created sockets.
     * <p/>
     * Default: <code>false</code>
     *
     * @see SocketOptions#SO_REUSEADDR
     */
    public boolean isSoReuseAddress() {
        return soReuseAddress;
    }

    @Deprecated
    public void setSoReuseAddress(boolean soReuseAddress) {
        this.soReuseAddress = soReuseAddress;
    }

    /**
     * Determines the default value of the {@link SocketOptions#SO_LINGER} parameter
     * for newly created sockets.
     * <p/>
     * Default: <code>-1</code>
     *
     * @see SocketOptions#SO_LINGER
     */
    public int getSoLinger() {
        return soLinger;
    }

    @Deprecated
    public void setSoLinger(int soLinger) {
        this.soLinger = soLinger;
    }

    /**
     * Determines the default value of the {@link SocketOptions#SO_KEEPALIVE} parameter
     * for newly created sockets.
     * <p/>
     * Default: <code>-1</code>
     *
     * @see SocketOptions#SO_KEEPALIVE
     */
    public boolean isSoKeepalive() {
        return this.soKeepAlive;
    }

    @Deprecated
    public void setSoKeepalive(boolean soKeepAlive) {
        this.soKeepAlive = soKeepAlive;
    }

    /**
     * Determines the default value of the {@link SocketOptions#TCP_NODELAY} parameter
     * for newly created sockets.
     * <p/>
     * Default: <code>false</code>
     *
     * @see SocketOptions#TCP_NODELAY
     */
    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    @Deprecated
    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    /**
     * Determines the default connect timeout value for non-blocking connection requests.
     * <p/>
     * Default: <code>0</code> (no timeout)
     */
    public int getConnectTimeout() {
        return connectTimeout;
    }

    @Deprecated
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
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
            .setConnectTimeout(config.getConnectTimeout());
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
        }

        public Builder setSelectInterval(long selectInterval) {
            this.selectInterval = selectInterval;
            return this;
        }

        public Builder setShutdownGracePeriod(long shutdownGracePeriod) {
            this.shutdownGracePeriod = shutdownGracePeriod;
            return this;
        }

        public Builder setInterestOpQueued(boolean interestOpQueued) {
            this.interestOpQueued = interestOpQueued;
            return this;
        }

        public Builder setIoThreadCount(int ioThreadCount) {
            this.ioThreadCount = ioThreadCount;
            return this;
        }

        public Builder setSoTimeout(int soTimeout) {
            this.soTimeout = soTimeout;
            return this;
        }

        public Builder setSoReuseAddress(boolean soReuseAddress) {
            this.soReuseAddress = soReuseAddress;
            return this;
        }

        public Builder setSoLinger(int soLinger) {
            this.soLinger = soLinger;
            return this;
        }

        public Builder setSoKeepAlive(boolean soKeepAlive) {
            this.soKeepAlive = soKeepAlive;
            return this;
        }

        public Builder setTcpNoDelay(boolean tcpNoDelay) {
            this.tcpNoDelay = tcpNoDelay;
            return this;
        }

        public Builder setConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public IOReactorConfig build() {
            return new IOReactorConfig(
                    selectInterval, shutdownGracePeriod, interestOpQueued, ioThreadCount,
                    soTimeout, soReuseAddress, soLinger, soKeepAlive, tcpNoDelay, connectTimeout);
        }

    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("[selectInterval=").append(this.selectInterval)
                .append(", shutdownGracePeriod=").append(this.shutdownGracePeriod)
                .append(", interestOpQueued=").append(this.interestOpQueued)
                .append(", ioThreadCount=").append(this.ioThreadCount)
                .append(", soTimeout=").append(this.soTimeout)
                .append(", soReuseAddress=").append(this.soReuseAddress)
                .append(", soLinger=").append(this.soLinger)
                .append(", soKeepAlive=").append(this.soKeepAlive)
                .append(", tcpNoDelay=").append(this.tcpNoDelay)
                .append(", connectTimeout=").append(this.connectTimeout).append("]");
        return builder.toString();
    }

}

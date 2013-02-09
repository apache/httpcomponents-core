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

/**
 * I/O reactor configuration parameters.
 *
 * @since 4.2
 */
@NotThreadSafe
public final class IOReactorConfig implements Cloneable {

    private static final int AVAIL_PROCS = Runtime.getRuntime().availableProcessors();

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

    /**
     * Determines time interval in milliseconds at which the I/O reactor wakes up to check for
     * timed out sessions and session requests.
     * <p/>
     * Default: <code>1000</code> milliseconds.
     */
    public long getSelectInterval() {
        return this.selectInterval;
    }

    /**
     * Defines time interval in milliseconds at which the I/O reactor wakes up to check for
     * timed out sessions and session requests. May not be negative or zero.
     */
    public void setSelectInterval(long selectInterval) {
        if (selectInterval <= 0) {
            throw new IllegalArgumentException("Select internal may not be negative or zero");
        }
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

    /**
     * Defines grace period in milliseconds the I/O reactors are expected to block waiting
     * for individual worker threads to terminate cleanly. May not be negative or zero.
     */
    public void setShutdownGracePeriod(long gracePeriod) {
        if (gracePeriod <= 0) {
            throw new IllegalArgumentException("Shutdown grace period may not be negative or zero");
        }
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

    /**
     * Defines whether or not I/O interest operations are to be queued and executed
     * asynchronously by the I/O reactor thread or to be applied to the underlying
     * {@link SelectionKey} immediately.
     *
     * @see SelectionKey
     * @see SelectionKey#interestOps()
     * @see SelectionKey#interestOps(int)
     */
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

    /**
     * Defines the number of I/O dispatch threads to be used by the I/O reactor.
     * May not be negative or zero.
     */
    public void setIoThreadCount(int ioThreadCount) {
        if (ioThreadCount <= 0) {
            throw new IllegalArgumentException("I/O thread count may not be negative or zero");
        }
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

    /**
     * Defines the default socket timeout value for non-blocking I/O operations.
     * <p/>
     * Default: <code>0</code> (no timeout)
     *
     * @see SocketOptions#SO_TIMEOUT
     */
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

    /**
     * Defines the default value of the {@link SocketOptions#SO_REUSEADDR} parameter
     * for newly created sockets.
     *
     * @see SocketOptions#SO_REUSEADDR
     */
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

    /**
     * Defines the default value of the {@link SocketOptions#SO_LINGER} parameter
     * for newly created sockets.
     *
     * @see SocketOptions#SO_LINGER
     */
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

    /**
     * Defines the default value of the {@link SocketOptions#SO_KEEPALIVE} parameter
     * for newly created sockets.
     * <p/>
     * Default: <code>-1</code>
     *
     * @see SocketOptions#SO_KEEPALIVE
     */
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

    /**
     * Defines the default value of the {@link SocketOptions#TCP_NODELAY} parameter
     * for newly created sockets.
     *
     * @see SocketOptions#TCP_NODELAY
     */
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

    /**
     * Defines the default connect timeout value for non-blocking connection requests.
     */
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /**
     * Determines the default value of the {@link SocketOptions#SO_SNDBUF} parameter
     * for newly created sockets.
     * <p/>
     * Default: <code>0</code> (system default)
     *
     * @see SocketOptions#SO_SNDBUF
     */
    public int getSndBufSize() {
        return sndBufSize;
    }

    /**
     * Defines the default value of the {@link SocketOptions#SO_SNDBUF} parameter
     * for newly created sockets.
     *
     * @see SocketOptions#SO_SNDBUF
     */
    public void setSndBufSize(int sndBufSize) {
        this.sndBufSize = sndBufSize;
    }

    /**
     * Determines the default value of the {@link SocketOptions#SO_RCVBUF} parameter
     * for newly created sockets.
     * <p/>
     * Default: <code>0</code> (system default)
     *
     * @see SocketOptions#SO_RCVBUF
     */
    public int getRcvBufSize() {
        return rcvBufSize;
    }

    /**
     * Defines the default value of the {@link SocketOptions#SO_RCVBUF} parameter
     * for newly created sockets.
     *
     * @see SocketOptions#SO_RCVBUF
     */
    public void setRcvBufSize(int rcvBufSize) {
        this.rcvBufSize = rcvBufSize;
    }

    @Override
    protected IOReactorConfig clone() throws CloneNotSupportedException {
        return (IOReactorConfig) super.clone();
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
                .append(", connectTimeout=").append(this.connectTimeout)
                .append(", sndBufSize=").append(this.sndBufSize)
                .append(", rcvBufSize=").append(this.rcvBufSize)
                .append("]");
        return builder.toString();
    }

}

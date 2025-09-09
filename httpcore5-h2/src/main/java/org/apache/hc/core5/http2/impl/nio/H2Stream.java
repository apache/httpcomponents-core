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

package org.apache.hc.core5.http2.impl.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.StreamClosedException;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.H2StreamResetException;

class H2Stream {

    private static final long LINGER_TIME = 1000; // 1 second

    private final H2StreamChannel channel;
    private final H2StreamHandler handler;
    private final AtomicBoolean released;

    private volatile boolean reserved;
    private volatile boolean remoteClosed;

    H2Stream(final H2StreamChannel channel, final H2StreamHandler handler, final boolean reserved) {
        this.channel = channel;
        this.handler = handler;
        this.reserved = reserved;
        this.released = new AtomicBoolean();
    }

    H2Stream(final H2StreamChannel channel, final H2StreamHandler handler) {
        this(channel, handler, false);
    }

    int getId() {
        return channel.getId();
    }

    boolean isReserved() {
        return reserved;
    }

    void activate() {
        reserved = false;
    }

    AtomicInteger getOutputWindow() {
        return channel.getOutputWindow();
    }

    AtomicInteger getInputWindow() {
        return channel.getInputWindow();
    }

    private boolean isPastResetDeadline() {
        final long localResetTime = channel.getLocalResetTime();
        return localResetTime > 0 && localResetTime + LINGER_TIME < System.currentTimeMillis();
    }

    boolean isTerminated() {
        return channel.isLocalClosed() && (remoteClosed || isPastResetDeadline());
    }

    boolean isRemoteClosed() {
        return remoteClosed;
    }

    boolean isLocalClosed() {
        return channel.isLocalClosed();
    }

    void consumePromise(final List<Header> headers, final boolean endOfStream) throws HttpException, IOException {
        try {
            if (endOfStream) {
                remoteClosed = true;
            }
            if (channel.isLocalReset()) {
                return;
            }
            handler.consumePromise(headers);
            channel.markLocalClosed();
        } catch (final ProtocolException ex) {
            localReset(ex, H2Error.PROTOCOL_ERROR);
        }
    }

    void consumeHeader(final List<Header> headers, final boolean endOfStream) throws HttpException, IOException {
        try {
            if (endOfStream) {
                remoteClosed = true;
            }
            if (channel.isLocalReset()) {
                return;
            }
            handler.consumeHeader(headers, remoteClosed);
        } catch (final ProtocolException ex) {
            localReset(ex, H2Error.PROTOCOL_ERROR);
        }
    }

    void consumeData(final ByteBuffer src, final boolean endOfStream) throws HttpException, IOException {
        try {
            if (endOfStream) {
                remoteClosed = true;
            }
            if (channel.isLocalReset()) {
                return;
            }
            handler.consumeData(src, remoteClosed);
        } catch (final CharacterCodingException ex) {
            localReset(ex, H2Error.INTERNAL_ERROR);
        } catch (final ProtocolException ex) {
            localReset(ex, H2Error.PROTOCOL_ERROR);
        }
    }

    boolean isOutputReady() {
        return !reserved && !channel.isLocalClosed() && handler.isOutputReady();
    }

    void produceOutput() throws HttpException, IOException {
        try {
            handler.produceOutput();
        } catch (final ProtocolException ex) {
            localReset(ex, H2Error.PROTOCOL_ERROR);
        }
    }

    void produceInputCapacityUpdate() throws IOException {
        handler.updateInputCapacity();
    }

    void fail(final Exception cause) {
        remoteClosed = true;
        channel.markLocalClosed();
        if (released.compareAndSet(false, true)) {
            handler.failed(cause);
            handler.releaseResources();
        }
    }

    void localReset(final Exception cause, final int code) throws IOException {
        channel.localReset(code);
        if (released.compareAndSet(false, true)) {
            handler.failed(cause);
            handler.releaseResources();
        }
    }

    void localReset(final Exception cause, final H2Error error) throws IOException {
        localReset(cause, error != null ? error.getCode() : H2Error.INTERNAL_ERROR.getCode());
    }

    void localReset(final H2StreamResetException ex) throws IOException {
        localReset(ex, ex.getCode());
    }

    void handle(final HttpException ex) throws IOException, HttpException {
        handler.handle(ex, remoteClosed);
    }

    HandlerFactory<AsyncPushConsumer> getPushHandlerFactory() {
        return handler.getPushHandlerFactory();
    }

    boolean abort() {
        final boolean cancelled = channel.cancel();
        if (released.compareAndSet(false, true)) {
            handler.failed(new StreamClosedException());
            handler.releaseResources();
        }
        return cancelled;
    }

    boolean abortGracefully() throws IOException {
        if (!isLocalClosed() && isRemoteClosed()) {
            channel.endStream();
            handler.releaseResources();
            return true;
        } else {
            return abort();
        }
    }

    void releaseResources() {
        if (released.compareAndSet(false, true)) {
            handler.releaseResources();
        }
    }

    @Override
    public String toString() {
        return channel.toString();
    }

}

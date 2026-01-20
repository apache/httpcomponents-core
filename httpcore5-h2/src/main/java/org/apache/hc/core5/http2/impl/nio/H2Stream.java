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
import java.nio.channels.CancelledKeyException;
import java.nio.charset.CharacterCodingException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.StreamControl;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.H2StreamResetException;
import org.apache.hc.core5.util.Timeout;

class H2Stream implements StreamControl {

    private static final long LINGER_TIME = 1000; // 1 second

    private final H2StreamChannel channel;
    private final H2StreamHandler handler;
    private final Consumer<State> stateChangeCallback;
    private final AtomicReference<State> transitionRef;
    private final AtomicBoolean released;
    private final AtomicBoolean cancelled;

    private volatile boolean reserved;
    private volatile boolean remoteClosed;

    H2Stream(final H2StreamChannel channel, final H2StreamHandler handler, final Consumer<State> stateChangeCallback) {
        this.channel = channel;
        this.handler = handler;
        this.stateChangeCallback = stateChangeCallback;
        this.reserved = true;
        this.transitionRef = new AtomicReference<>(State.RESERVED);
        this.released = new AtomicBoolean();
        this.cancelled = new AtomicBoolean();
    }

    @Override
    public int getId() {
        return channel.getId();
    }

    @Override
    public State getState() {
        return transitionRef.get();
    }

    @Override
    public void setTimeout(final Timeout timeout) {
        // not supported
    }

    boolean isReserved() {
        return reserved;
    }

    private void triggerOpen() {
        if (transitionRef.compareAndSet(State.RESERVED, State.OPEN) && stateChangeCallback != null) {
            stateChangeCallback.accept(State.OPEN);
        }
    }

    private void triggerClosed() {
        if (transitionRef.compareAndSet(State.OPEN, State.CLOSED) && stateChangeCallback != null) {
            stateChangeCallback.accept(State.CLOSED);
        }
    }

    void activate() {
        reserved = false;
        triggerOpen();
    }

    AtomicInteger getOutputWindow() {
        return channel.getOutputWindow();
    }

    AtomicInteger getInputWindow() {
        return channel.getInputWindow();
    }

    private boolean isPastLingerDeadline() {
        final long localResetTime = channel.getLocalResetTime();
        return localResetTime > 0 && localResetTime + LINGER_TIME < System.currentTimeMillis();
    }

    boolean isClosedPastLingerDeadline() {
        return channel.isLocalClosed() && (remoteClosed || isPastLingerDeadline());
    }

    boolean isClosed() {
        return channel.isLocalClosed() && (remoteClosed || channel.isLocalReset());
    }

    boolean isActive() {
        return !reserved && !isClosed();
    }

    boolean isRemoteClosed() {
        return remoteClosed;
    }

    void markRemoteClosed() {
        remoteClosed = true;
    }

    boolean isLocalClosed() {
        return channel.isLocalClosed();
    }

    void consumePromise(final List<Header> headers) throws HttpException, IOException {
        try {
            if (channel.isLocalReset()) {
                return;
            }
            if (cancelled.get()) {
                localResetCancelled();
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
            if (cancelled.get()) {
                localResetCancelled();
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
            if (cancelled.get()) {
                localResetCancelled();
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
            try {
                handler.failed(cause);
                handler.releaseResources();
            } finally {
                triggerClosed();
            }
        }
    }

    void localReset(final Exception cause, final int code) throws IOException {
        channel.localReset(code);
        if (released.compareAndSet(false, true)) {
            try {
                handler.failed(cause);
                handler.releaseResources();
            } finally {
                triggerClosed();
            }
        }
    }

    void localReset(final Exception cause, final H2Error error) throws IOException {
        localReset(cause, error != null ? error.getCode() : H2Error.INTERNAL_ERROR.getCode());
    }

    void localReset(final H2StreamResetException ex) throws IOException {
        localReset(ex, ex.getCode());
    }

    void localResetCancelled() throws IOException {
        localReset(new H2StreamResetException(H2Error.CANCEL, "Cancelled"));
    }

    void handle(final HttpException ex) throws IOException, HttpException {
        handler.handle(ex, remoteClosed);
    }

    HandlerFactory<AsyncPushConsumer> getPushHandlerFactory() {
        return handler.getPushHandlerFactory();
    }

    boolean abort() {
        if (cancelled.compareAndSet(false, true)) {
            try {
                channel.requestOutput();
                return true;
            } catch (final CancelledKeyException ignore) {
            }
        }
        return false;
    }

    boolean abortGracefully() throws IOException {
        if (!isLocalClosed() && isRemoteClosed()) {
            channel.endStream();
            releaseResources();
            return true;
        } else {
            return abort();
        }
    }

    void releaseResources() {
        if (released.compareAndSet(false, true)) {
            try {
                handler.releaseResources();
            } finally {
                triggerClosed();
            }
        }
    }

    @Override
    public boolean cancel() {
        return abort();
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("[")
                .append("id=").append(channel.getId())
                .append(", reserved=").append(reserved)
                .append(", removeClosed=").append(remoteClosed)
                .append(", localClosed=").append(channel.isLocalClosed())
                .append(", localReset=").append(channel.isLocalReset())
                .append("]");
        return buf.toString();
    }

}

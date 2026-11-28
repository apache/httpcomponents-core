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
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.H2StreamResetException;
import org.apache.hc.core5.http2.priority.PriorityValue;
import org.apache.hc.core5.util.Timeout;

class H2Stream implements StreamControl {

    private static final long LINGER_TIME = 1000; // 1 second

    private final H2StreamChannel channel;
    private final H2StreamHandler handler;
    private final Consumer<State> stateChangeCallback;
    private final AtomicReference<State> transitionRef;
    private final AtomicBoolean released;
    private final AtomicBoolean cancelled;
    private final boolean remoteStream;
    private final FrameLimitController frameLimitController;

    private volatile boolean reserved;
    private volatile boolean remoteClosed;

    private volatile long lastActivityNanos;
    private volatile long activatedAtNanos;

    private volatile Timeout idleTimeout;
    private volatile PriorityValue priorityValue;

    H2Stream(
            final H2StreamChannel channel,
            final H2StreamHandler handler,
            final boolean remoteStream,
            final FrameLimitController frameLimitController,
            final Consumer<State> stateChangeCallback) {
        this.channel = channel;
        this.handler = handler;
        this.remoteStream = remoteStream;
        this.frameLimitController = frameLimitController;
        this.stateChangeCallback = stateChangeCallback;
        this.reserved = true;
        this.transitionRef = new AtomicReference<>(State.RESERVED);
        this.released = new AtomicBoolean();
        this.cancelled = new AtomicBoolean();
        this.lastActivityNanos = 0L;
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
        this.idleTimeout = timeout;
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
        touch();
        activatedAtNanos = lastActivityNanos;
        triggerOpen();
    }

    long getActivatedAtNanos() {
        return activatedAtNanos;
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

    boolean isLocalReset() {
        return channel.isLocalReset();
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
            touch();

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
            touch();

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
            touch();

            if (channel.isLocalReset()) {
                return;
            }
            if (cancelled.get()) {
                localResetCancelled();
                return;
            }
            handler.consumeData(src, remoteClosed);
        } catch (final CharacterCodingException ex) {
            // Undecodable request content: peer-supplied bytes drive this reset just as
            // much as a frame-level protocol fault does, even though the code itself
            // (INTERNAL_ERROR) is not in the general peer-fault list.
            localReset(ex, H2Error.INTERNAL_ERROR.getCode(), true);
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
        localReset(cause, code, isChargeablePeerFault(code));
    }

    /**
     * As {@link #localReset(Exception, int)}, but lets the caller override whether this reset
     * is charged against the rapid-reset budget, for a cause whose code alone does not say so
     * (for example a malformed request body: the decoder reports it as {@code INTERNAL_ERROR},
     * but it is peer-supplied bytes driving the reset, same as a frame-level protocol fault).
     */
    void localReset(final Exception cause, final int code, final boolean chargeablePeerFault) throws IOException {
        // A peer-initiated, not yet closed stream that the peer itself makes us locally reset
        // over a fault it caused (for example a zero-increment WINDOW_UPDATE, DATA past the end
        // of the message, or undecodable request content) is the same rapid-cancellation
        // resource drain RST_STREAM rate-limiting defends against, just triggered without an
        // actual inbound RST_STREAM frame. Charge it the same way, regardless of the stream's
        // age: unlike an inbound RST_STREAM, none of these has a legitimate long-lived-stream
        // case to exempt.
        if (remoteStream && !reserved && !isClosed() && chargeablePeerFault
                && !frameLimitController.acceptLocalReset(System.nanoTime())) {
            throw new H2ConnectionException(H2Error.ENHANCE_YOUR_CALM, "Excessive rapid HTTP/2 stream resets");
        }
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

    /**
     * Error codes that, on a locally reset peer-initiated stream, are used for a fault
     * attributable to the peer (a frame-level protocol violation, or content the peer sent that
     * the application rejected) rather than a local or application decision (cancellation, the
     * local SETTINGS_MAX_CONCURRENT_STREAMS limit). Keeping this narrow avoids charging
     * legitimate activity, such as an application cancelling its own exchange, against the
     * rapid-reset budget. A cause that is peer-attributable despite its code not being in this
     * list (see {@link #localReset(Exception, int, boolean)}) is charged explicitly by its caller.
     */
    private static boolean isChargeablePeerFault(final int code) {
        return code == H2Error.PROTOCOL_ERROR.getCode()
                || code == H2Error.FLOW_CONTROL_ERROR.getCode()
                || code == H2Error.STREAM_CLOSED.getCode();
    }

    void localReset(final H2StreamResetException ex) throws IOException {
        localReset(ex, ex.getCode());
    }

    void localResetCancelled() throws IOException {
        localReset(new H2StreamResetException(H2Error.CANCEL, "Cancelled"));
    }

    /**
     * Resets a cancelled stream that has already sent END_STREAM. Such a stream
     * produces no more output, so it would otherwise wait for a peer frame.
     */
    void resetIfCancelled() throws IOException {
        if (cancelled.get() && channel.isLocalClosed() && !isClosed()) {
            localResetCancelled();
        }
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
            handler.outputAborted();
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
                .append(", remoteClosed=").append(remoteClosed)
                .append(", localClosed=").append(channel.isLocalClosed())
                .append(", localReset=").append(channel.isLocalReset())
                .append("]");
        return buf.toString();
    }

    private void touch() {
        this.lastActivityNanos = System.nanoTime();
    }

    long getLastActivityNanos() {
        return lastActivityNanos;
    }

    Timeout getIdleTimeout() {
        return idleTimeout;
    }

    PriorityValue getPriorityValue() {
        return priorityValue;
    }

    void setPriorityValue(final PriorityValue priorityValue) {
        this.priorityValue = priorityValue;
    }

}
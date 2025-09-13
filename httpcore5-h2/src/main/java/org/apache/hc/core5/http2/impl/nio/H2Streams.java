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
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.apache.hc.core5.http2.H2StreamResetException;
import org.apache.hc.core5.http2.frame.StreamIdGenerator;
import org.apache.hc.core5.util.Args;

class H2Streams {

    private final StreamIdGenerator idGenerator;
    private final Map<Integer, H2Stream> streamMap;
    private final Queue<H2Stream> streams;
    private final AtomicInteger lastLocalId;
    private final AtomicInteger lastRemoteId;
    private final AtomicInteger localCount;
    private final AtomicInteger remoteCount;

    public H2Streams(final StreamIdGenerator idGenerator) {
        this.idGenerator = Args.notNull(idGenerator, "Stream id generator");
        this.streamMap = new ConcurrentHashMap<>();
        this.streams = new ConcurrentLinkedQueue<>();
        this.lastLocalId = new AtomicInteger(0);
        this.lastRemoteId = new AtomicInteger(0);
        this.localCount = new AtomicInteger(0);
        this.remoteCount = new AtomicInteger(0);
    }

    public boolean isEmpty() {
        return streams.isEmpty();
    }

    public Iterator<H2Stream> iterator() {
        return streams.iterator();
    }

    public int getLastLocalId() {
        return lastLocalId.get();
    }

    public int getLastRemoteId() {
        return lastRemoteId.get();
    }

    public int getLocalCount() {
        return localCount.get();
    }

    public int getRemoteCount() {
        return remoteCount.get();
    }

    private H2Stream createStream(final H2StreamChannel channel, final H2StreamHandler streamHandler) {
        final int streamId = channel.getId();
        final boolean remoteStream = isOtherSide(streamId);
        final H2Stream stream = new H2Stream(channel, streamHandler, state -> {
            final AtomicInteger count = remoteStream ? remoteCount : localCount;
            switch (state) {
                case OPEN:
                    count.incrementAndGet();
                    break;
                case CLOSED:
                    count.decrementAndGet();
            }
        });
        if (remoteStream) {
            final int currentId = lastRemoteId.get();
            if (streamId > currentId) {
                lastRemoteId.compareAndSet(currentId, streamId);
            }
        }
        streamMap.put(streamId, stream);
        streams.add(stream);
        return stream;
    }

    public H2Stream createActive(final H2StreamChannel channel, final H2StreamHandler streamHandler) {
        final H2Stream stream = createStream(channel, streamHandler);
        if (!stream.isClosed()) {
            stream.activate();
        }
        return stream;
    }

    public H2Stream createReserved(final H2StreamChannel channel, final H2StreamHandler streamHandler) {
        return createStream(channel, streamHandler);
    }

    public void resetIfExceedsMaxConcurrentLimit(final H2Stream stream, final int max) throws IOException {
        if (stream.isActive() && getRemoteCount() > max) {
            stream.localReset(new H2StreamResetException(H2Error.REFUSED_STREAM, "Local SETTINGS_MAX_CONCURRENT_STREAMS exceeded"));
        }
    }

    public void dropStreamId(final int streamId) {
        streamMap.remove(streamId);
    }

    public void shutdownAndReleaseAll() {
        for (final Iterator<H2Stream> it = streams.iterator(); it.hasNext(); ) {
            final H2Stream stream = it.next();
            if (stream.isClosed()) {
                stream.releaseResources();
            } else {
                stream.fail(new ConnectionClosedException());
            }
        }
        streams.clear();
        streamMap.clear();
    }

    public H2Stream lookup(final int streamId) {
        return streamMap.get(streamId);
    }

    boolean hasBeenSeen(final int streamId) {
        return streamId <= (isSameSide(streamId) ? lastLocalId : lastRemoteId).get();
    }

    boolean isClosed(final H2Stream stream, final int streamId) {
        return stream != null ? stream.isLocalClosed() && stream.isRemoteClosed() : hasBeenSeen(streamId);
    }

    public H2Stream lookupValidOrNull(final int streamId) throws H2ConnectionException {
        final H2Stream stream = streamMap.get(streamId);
        if (isClosed(stream, streamId)) {
            throw new H2ConnectionException(H2Error.STREAM_CLOSED, "Stream closed");
        }
        return stream;
    }

    public H2Stream lookupSeen(final int streamId) throws H2ConnectionException {
        final H2Stream stream = streamMap.get(streamId);
        if (stream == null && !hasBeenSeen(streamId)) {
            throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Unexpected stream id: " + streamId);
        }
        return stream;
    }

    public H2Stream lookupValid(final int streamId) throws H2ConnectionException {
        final H2Stream stream = lookupValidOrNull(streamId);
        if (stream == null) {
            throw new H2ConnectionException(H2Error.PROTOCOL_ERROR, "Unexpected stream id: " + streamId);
        }
        return stream;
    }

    public boolean isSameSide(final int streamId) {
        return idGenerator.isSameSide(streamId);
    }

    public boolean isOtherSide(final int streamId) {
        return !idGenerator.isSameSide(streamId);
    }

    public int generateStreamId() {
        for (;;) {
            final int currentId = lastLocalId.get();
            final int newStreamId = idGenerator.generate(currentId);
            if (lastLocalId.compareAndSet(currentId, newStreamId)) {
                return newStreamId;
            }
        }
    }

}

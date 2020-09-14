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

package org.apache.hc.core5.http.impl.io;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.ResponseOutOfOrderStrategy;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.io.InputStream;

/**
 * A {@link ResponseOutOfOrderStrategy} implementation which checks for premature responses every {@link #chunkSize}
 * bytes. An 8 KiB chunk size is used by default based on testing using values between 4 KiB and 128 KiB. This is
 * optimized for correctness and results in a maximum upload speed of 8 MiB/s until {@link #maxChunksToCheck} is
 * reached.
 *
 * @since 5.1
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class MonitoringResponseOutOfOrderStrategy implements ResponseOutOfOrderStrategy {

    private static final int DEFAULT_CHUNK_SIZE = 8 * 1024;

    public static final MonitoringResponseOutOfOrderStrategy INSTANCE = new MonitoringResponseOutOfOrderStrategy();

    private final long chunkSize;
    private final long maxChunksToCheck;

    /**
     * Instantiates a default {@link MonitoringResponseOutOfOrderStrategy}. {@link #INSTANCE} may be used instead.
     */
    public MonitoringResponseOutOfOrderStrategy() {
        this(DEFAULT_CHUNK_SIZE);
    }

    /**
     * Instantiates a {@link MonitoringResponseOutOfOrderStrategy} with unlimited {@link #maxChunksToCheck}.
     *
     * @param chunkSize The chunk size after which a response check is executed.
     */
    public MonitoringResponseOutOfOrderStrategy(final long chunkSize) {
        this(chunkSize, Long.MAX_VALUE);
    }

    /**
     * Instantiates a {@link MonitoringResponseOutOfOrderStrategy}.
     *
     * @param chunkSize The chunk size after which a response check is executed.
     * @param maxChunksToCheck The maximum number of chunks to check, allowing expensive checks to be avoided
     *                         after a sufficient portion of the request entity has been transferred.
     */
    public MonitoringResponseOutOfOrderStrategy(final long chunkSize, final long maxChunksToCheck) {
        this.chunkSize = Args.positive(chunkSize, "chunkSize");
        this.maxChunksToCheck = Args.positive(maxChunksToCheck, "maxChunksToCheck");
    }

    @Override
    public boolean isEarlyResponseDetected(
            final ClassicHttpRequest request,
            final HttpClientConnection connection,
            final InputStream inputStream,
            final long totalBytesSent,
            final long nextWriteSize) throws IOException {
        if (nextWriteStartsNewChunk(totalBytesSent, nextWriteSize)) {
            final boolean ssl = connection.getSSLSession() != null;
            return ssl ? connection.isDataAvailable(Timeout.ONE_MILLISECOND) : (inputStream.available() > 0);
        }
        return false;
    }

    private boolean nextWriteStartsNewChunk(final long totalBytesSent, final long nextWriteSize) {
        final long currentChunkIndex = Math.min(totalBytesSent / chunkSize, maxChunksToCheck);
        final long newChunkIndex = Math.min((totalBytesSent + nextWriteSize) / chunkSize, maxChunksToCheck);
        return currentChunkIndex < newChunkIndex;
    }

    @Override
    public String toString() {
        return "DefaultResponseOutOfOrderStrategy{chunkSize=" + chunkSize + ", maxChunksToCheck=" + maxChunksToCheck + '}';
    }
}

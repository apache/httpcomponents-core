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

package org.apache.hc.core5.http.config;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;

/**
 * HTTP/1.1 protocol parameters.
 * <p>
 * Please note that line length is defined in bytes and not characters.
 * This is only relevant however when using non-standard HTTP charsets
 * for protocol elements such as UTF-8.
 * </p>
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class Http1Config {

    public static final Http1Config DEFAULT = new Builder().build();

    private final HttpVersion version;
    private final int bufferSize;
    private final int chunkSizeHint;
    private final Timeout waitForContinueTimeout;
    private final int maxLineLength;
    private final int maxHeaderCount;
    private final int maxEmptyLineCount;
    private final int initialWindowSize;

    Http1Config(final HttpVersion version, final int bufferSize, final int chunkSizeHint,
                final Timeout waitForContinueTimeout, final int maxLineLength, final int maxHeaderCount,
                final int maxEmptyLineCount, final int initialWindowSize) {
        super();
        this.version = version;
        this.bufferSize = bufferSize;
        this.chunkSizeHint = chunkSizeHint;
        this.waitForContinueTimeout = waitForContinueTimeout;
        this.maxLineLength = maxLineLength;
        this.maxHeaderCount = maxHeaderCount;
        this.maxEmptyLineCount = maxEmptyLineCount;
        this.initialWindowSize = initialWindowSize;
    }

    /**
     * The effective protocol level expressed by the minor version of HTTP/1.x.
     *
     * @since 5.3
     */
    public HttpVersion getVersion() {
        return version;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public int getChunkSizeHint() {
        return chunkSizeHint;
    }

    public Timeout getWaitForContinueTimeout() {
        return waitForContinueTimeout;
    }

    public int getMaxLineLength() {
        return maxLineLength;
    }

    public int getMaxHeaderCount() {
        return maxHeaderCount;
    }

    public int getMaxEmptyLineCount() {
        return this.maxEmptyLineCount;
    }

    public int getInitialWindowSize() {
        return initialWindowSize;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("[version=").append(version)
                .append(", bufferSize=").append(bufferSize)
                .append(", chunkSizeHint=").append(chunkSizeHint)
                .append(", waitForContinueTimeout=").append(waitForContinueTimeout)
                .append(", maxLineLength=").append(maxLineLength)
                .append(", maxHeaderCount=").append(maxHeaderCount)
                .append(", maxEmptyLineCount=").append(maxEmptyLineCount)
                .append(", initialWindowSize=").append(initialWindowSize)
                .append("]");
        return builder.toString();
    }

    public static Http1Config.Builder custom() {
        return new Builder();
    }
    public static Http1Config.Builder copy(final Http1Config config) {
        Args.notNull(config, "Config");
        return new Builder()
                .setVersion(config.getVersion())
                .setBufferSize(config.getBufferSize())
                .setChunkSizeHint(config.getChunkSizeHint())
                .setWaitForContinueTimeout(config.getWaitForContinueTimeout())
                .setMaxHeaderCount(config.getMaxHeaderCount())
                .setMaxLineLength(config.getMaxLineLength())
                .setMaxEmptyLineCount(config.getMaxEmptyLineCount())
                .setInitialWindowSize(config.getInitialWindowSize());
    }

    private static final int INIT_WINDOW_SIZE = 65535;
    private static final int INIT_BUF_SIZE = 8192;
    private static final Timeout INIT_WAIT_FOR_CONTINUE = Timeout.ofSeconds(3);
    private static final int INIT_BUF_CHUNK = -1;
    private static final int INIT_MAX_HEADER_COUNT = -1;
    private static final int INIT_MAX_LINE_LENGTH = -1;
    private static final int INIT_MAX_EMPTY_LINE_COUNT = 10;

    public static class Builder {

        private HttpVersion version;
        private int bufferSize;
        private int chunkSizeHint;
        private Timeout waitForContinueTimeout;
        private int maxLineLength;
        private int maxHeaderCount;
        private int maxEmptyLineCount;
        private int initialWindowSize;

        Builder() {
            this.version = HttpVersion.HTTP_1_1;
            this.bufferSize = INIT_BUF_SIZE;
            this.chunkSizeHint = INIT_BUF_CHUNK;
            this.waitForContinueTimeout = INIT_WAIT_FOR_CONTINUE;
            this.maxLineLength = INIT_MAX_LINE_LENGTH;
            this.maxHeaderCount = INIT_MAX_HEADER_COUNT;
            this.maxEmptyLineCount = INIT_MAX_EMPTY_LINE_COUNT;
            this.initialWindowSize = INIT_WINDOW_SIZE;
        }

        /**
         * Sets the effective HTTP/1.x protocol level (as expressed by the minor version).
         * Presently only {@link HttpVersion#HTTP_1_0} and {@link HttpVersion#HTTP_1_1} are
         * supported.
         *
         * @since 5.3
         */
        public Builder setVersion(final HttpVersion version) {
            Args.notNull(version, "HTTP/1 protocol version");
            Args.check(version.getMajor() == 1, "HTTP/1 protocol version is required");
            this.version = version;
            return this;
        }

        public Builder setBufferSize(final int bufferSize) {
            this.bufferSize = bufferSize;
            return this;
        }

        public Builder setChunkSizeHint(final int chunkSizeHint) {
            this.chunkSizeHint = chunkSizeHint;
            return this;
        }

        public Builder setWaitForContinueTimeout(final Timeout waitForContinueTimeout) {
            this.waitForContinueTimeout = waitForContinueTimeout;
            return this;
        }

        public Builder setMaxLineLength(final int maxLineLength) {
            this.maxLineLength = maxLineLength;
            return this;
        }

        public Builder setMaxHeaderCount(final int maxHeaderCount) {
            this.maxHeaderCount = maxHeaderCount;
            return this;
        }

        public Builder setMaxEmptyLineCount(final int maxEmptyLineCount) {
            this.maxEmptyLineCount = maxEmptyLineCount;
            return this;
        }

        public Builder setInitialWindowSize(final int initialWindowSize) {
            Args.positive(initialWindowSize, "Initial window size");
            this.initialWindowSize = initialWindowSize;
            return this;
        }

        public Http1Config build() {
            return new Http1Config(
                    version,
                    bufferSize,
                    chunkSizeHint,
                    waitForContinueTimeout,
                    maxLineLength,
                    maxHeaderCount,
                    maxEmptyLineCount,
                    initialWindowSize);
        }

    }

}

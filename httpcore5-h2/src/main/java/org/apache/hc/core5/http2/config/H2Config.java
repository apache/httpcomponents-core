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

package org.apache.hc.core5.http2.config;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http2.frame.FrameConsts;
import org.apache.hc.core5.util.Args;

/**
 * HTTP/2 protocol configuration.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class H2Config {

    public static final H2Config DEFAULT = custom().build();
    public static final H2Config INIT = initial().build();

    private final int headerTableSize;
    private final boolean pushEnabled;
    private final int maxConcurrentStreams;
    private final int initialWindowSize;
    private final int maxFrameSize;
    private final int maxHeaderListSize;
    private final boolean compressionEnabled;

    H2Config(final int headerTableSize, final boolean pushEnabled, final int maxConcurrentStreams,
             final int initialWindowSize, final int maxFrameSize, final int maxHeaderListSize,
             final boolean compressionEnabled) {
        super();
        this.headerTableSize = headerTableSize;
        this.pushEnabled = pushEnabled;
        this.maxConcurrentStreams = maxConcurrentStreams;
        this.initialWindowSize = initialWindowSize;
        this.maxFrameSize = maxFrameSize;
        this.maxHeaderListSize = maxHeaderListSize;
        this.compressionEnabled = compressionEnabled;
    }

    public int getHeaderTableSize() {
        return headerTableSize;
    }

    public boolean isPushEnabled() {
        return pushEnabled;
    }

    public int getMaxConcurrentStreams() {
        return maxConcurrentStreams;
    }

    public int getInitialWindowSize() {
        return initialWindowSize;
    }

    public int getMaxFrameSize() {
        return maxFrameSize;
    }

    public int getMaxHeaderListSize() {
        return maxHeaderListSize;
    }

    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("[headerTableSize=").append(this.headerTableSize)
                .append(", pushEnabled=").append(this.pushEnabled)
                .append(", maxConcurrentStreams=").append(this.maxConcurrentStreams)
                .append(", initialWindowSize=").append(this.initialWindowSize)
                .append(", maxFrameSize=").append(this.maxFrameSize)
                .append(", maxHeaderListSize=").append(this.maxHeaderListSize)
                .append(", compressionEnabled=").append(this.compressionEnabled)
                .append("]");
        return builder.toString();
    }

    public static H2Config.Builder custom() {
        return new Builder();
    }

    private static final int      INIT_HEADER_TABLE_SIZE   = 4096;
    private static final boolean  INIT_ENABLE_PUSH         = true;
    private static final int      INIT_MAX_FRAME_SIZE      = FrameConsts.MIN_FRAME_SIZE;
    private static final int      INIT_WINDOW_SIZE         = 65535;

    public static H2Config.Builder initial() {
        return new Builder()
                .setHeaderTableSize(INIT_HEADER_TABLE_SIZE)
                .setPushEnabled(INIT_ENABLE_PUSH)
                .setMaxConcurrentStreams(Integer.MAX_VALUE) // no limit
                .setMaxFrameSize(INIT_MAX_FRAME_SIZE)
                .setInitialWindowSize(INIT_WINDOW_SIZE)
                .setMaxHeaderListSize(Integer.MAX_VALUE); // unlimited
    }

    public static H2Config.Builder copy(final H2Config config) {
        Args.notNull(config, "Connection config");
        return new Builder()
                .setHeaderTableSize(config.getHeaderTableSize())
                .setPushEnabled(config.isPushEnabled())
                .setMaxConcurrentStreams(config.getMaxConcurrentStreams())
                .setInitialWindowSize(config.getInitialWindowSize())
                .setMaxFrameSize(config.getMaxFrameSize())
                .setMaxHeaderListSize(config.getMaxHeaderListSize())
                .setCompressionEnabled(config.isCompressionEnabled());
    }

    public static class Builder {

        private int headerTableSize;
        private boolean pushEnabled;
        private int maxConcurrentStreams;
        private int initialWindowSize;
        private int maxFrameSize;
        private int maxHeaderListSize;
        private boolean compressionEnabled;

        Builder() {
            this.headerTableSize = INIT_HEADER_TABLE_SIZE * 2;
            this.pushEnabled = INIT_ENABLE_PUSH;
            this.maxConcurrentStreams = 250;
            this.initialWindowSize = INIT_WINDOW_SIZE;
            this.maxFrameSize  = FrameConsts.MIN_FRAME_SIZE * 4;
            this.maxHeaderListSize = FrameConsts.MAX_FRAME_SIZE;
            this.compressionEnabled = true;
        }

        public Builder setHeaderTableSize(final int headerTableSize) {
            Args.notNegative(headerTableSize, "Header table size");
            this.headerTableSize = headerTableSize;
            return this;
        }

        public Builder setPushEnabled(final boolean pushEnabled) {
            this.pushEnabled = pushEnabled;
            return this;
        }

        public Builder setMaxConcurrentStreams(final int maxConcurrentStreams) {
            Args.positive(maxConcurrentStreams, "Max concurrent streams");
            this.maxConcurrentStreams = maxConcurrentStreams;
            return this;
        }

        public Builder setInitialWindowSize(final int initialWindowSize) {
            Args.positive(initialWindowSize, "Initial window size");
            this.initialWindowSize = initialWindowSize;
            return this;
        }

        public Builder setMaxFrameSize(final int maxFrameSize) {
            this.maxFrameSize = Args.checkRange(maxFrameSize, FrameConsts.MIN_FRAME_SIZE, FrameConsts.MAX_FRAME_SIZE,
                    "Invalid max frame size");
            return this;
        }

        public Builder setMaxHeaderListSize(final int maxHeaderListSize) {
            Args.positive(maxHeaderListSize, "Max header list size");
            this.maxHeaderListSize = maxHeaderListSize;
            return this;
        }

        public Builder setCompressionEnabled(final boolean compressionEnabled) {
            this.compressionEnabled = compressionEnabled;
            return this;
        }

        public H2Config build() {
            return new H2Config(
                    headerTableSize,
                    pushEnabled,
                    maxConcurrentStreams,
                    initialWindowSize,
                    maxFrameSize,
                    maxHeaderListSize,
                    compressionEnabled);
        }

    }

}

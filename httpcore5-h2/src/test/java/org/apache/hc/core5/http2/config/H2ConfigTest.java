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

import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class H2ConfigTest {

    @Test
    void builder() {
        // Create and start requester
        final H2Config h2Config = H2Config.custom()
                .setPushEnabled(false)
                .build();
        Assertions.assertThat(h2Config).isNotNull();
    }

    @Test
    void checkValues() {
        // Create and start requester
        final H2Config h2Config = H2Config.custom()
                .setHeaderTableSize(1)
                .setMaxConcurrentStreams(1)
                .setMaxFrameSize(16384)
                .setPushEnabled(true)
                .setCompressionEnabled(true)
                .build();

        Assertions.assertThat(h2Config.getHeaderTableSize()).isEqualTo(1);
        Assertions.assertThat(h2Config.getMaxConcurrentStreams()).isEqualTo(1);
        Assertions.assertThat(h2Config.getMaxFrameSize()).isEqualTo(16384);
        Assertions.assertThat(h2Config.isPushEnabled()).isTrue();
        Assertions.assertThat(h2Config.isCompressionEnabled()).isTrue();
    }

    @Test
    void copy() {
        // Create and start requester
        final H2Config h2Config = H2Config.custom()
                .setHeaderTableSize(1)
                .setMaxConcurrentStreams(1)
                .setMaxFrameSize(16384)
                .setPushEnabled(true)
                .setCompressionEnabled(true)
                .build();

        final H2Config.Builder builder = H2Config.copy(h2Config);
        final H2Config h2Config2 = builder.build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(h2Config2.getHeaderTableSize()).isEqualTo(h2Config.getHeaderTableSize());
            softly.assertThat(h2Config2.getInitialWindowSize()).isEqualTo(h2Config.getInitialWindowSize());
            softly.assertThat(h2Config2.getMaxConcurrentStreams()).isEqualTo(h2Config.getMaxConcurrentStreams());
            softly.assertThat(h2Config2.getMaxFrameSize()).isEqualTo(h2Config.getMaxFrameSize());
            softly.assertThat(h2Config2.getMaxHeaderListSize()).isEqualTo(h2Config.getMaxHeaderListSize());
        });

    }

}
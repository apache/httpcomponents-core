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

package org.apache.hc.core5.http2.hpack;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestFifoBuffer {

    @Test
    void testAddRemoveCycle() {

        final FifoBuffer fifoBuffer = new FifoBuffer(5);

        final HPackHeader h1 = new HPackHeader("h", "1");
        final HPackHeader h2 = new HPackHeader("h", "2");
        final HPackHeader h3 = new HPackHeader("h", "3");
        final HPackHeader h4 = new HPackHeader("h", "4");

        for (int i = 0; i < 20; i++) {
            Assertions.assertThat(fifoBuffer.size()).isEqualTo(0);
            Assertions.assertThat(fifoBuffer.getFirst()).isSameAs(null);
            Assertions.assertThat(fifoBuffer.getLast()).isSameAs(null);
            fifoBuffer.addFirst(h1);
            Assertions.assertThat(fifoBuffer.getFirst()).isSameAs(h1);
            Assertions.assertThat(fifoBuffer.getLast()).isSameAs(h1);
            Assertions.assertThat(fifoBuffer.size()).isEqualTo(1);
            fifoBuffer.addFirst(h2);
            Assertions.assertThat(fifoBuffer.size()).isEqualTo(2);
            Assertions.assertThat(fifoBuffer.getFirst()).isSameAs(h2);
            Assertions.assertThat(fifoBuffer.getLast()).isSameAs(h1);
            fifoBuffer.addFirst(h3);
            Assertions.assertThat(fifoBuffer.size()).isEqualTo(3);
            Assertions.assertThat(fifoBuffer.getFirst()).isSameAs(h3);
            Assertions.assertThat(fifoBuffer.getLast()).isSameAs(h1);
            fifoBuffer.addFirst(h4);
            Assertions.assertThat(fifoBuffer.size()).isEqualTo(4);
            Assertions.assertThat(fifoBuffer.getFirst()).isSameAs(h4);
            Assertions.assertThat(fifoBuffer.getLast()).isSameAs(h1);

            Assertions.assertThat(fifoBuffer.get(0)).isSameAs(h4);
            Assertions.assertThat(fifoBuffer.get(1)).isSameAs(h3);
            Assertions.assertThat(fifoBuffer.get(2)).isSameAs(h2);
            Assertions.assertThat(fifoBuffer.get(3)).isSameAs(h1);

            fifoBuffer.removeLast();
            Assertions.assertThat(fifoBuffer.size()).isEqualTo(3);
            Assertions.assertThat(fifoBuffer.getFirst()).isSameAs(h4);
            Assertions.assertThat(fifoBuffer.getLast()).isSameAs(h2);
            fifoBuffer.removeLast();
            Assertions.assertThat(fifoBuffer.size()).isEqualTo(2);
            Assertions.assertThat(fifoBuffer.getFirst()).isSameAs(h4);
            Assertions.assertThat(fifoBuffer.getLast()).isSameAs(h3);
            fifoBuffer.removeLast();
            Assertions.assertThat(fifoBuffer.size()).isEqualTo(1);
            Assertions.assertThat(fifoBuffer.getFirst()).isSameAs(h4);
            Assertions.assertThat(fifoBuffer.getLast()).isSameAs(h4);
            fifoBuffer.removeLast();
            Assertions.assertThat(fifoBuffer.size()).isEqualTo(0);
            Assertions.assertThat(fifoBuffer.getFirst()).isSameAs(null);
            Assertions.assertThat(fifoBuffer.getLast()).isSameAs(null);
        }
    }

    @Test
    void testExpand() {

        final HPackHeader h1 = new HPackHeader("h", "1");
        final HPackHeader h2 = new HPackHeader("h", "2");
        final HPackHeader h3 = new HPackHeader("h", "3");
        final HPackHeader h4 = new HPackHeader("h", "4");

        for (int i = 0; i < 10; i++) {

            final FifoBuffer fifoBuffer = new FifoBuffer(1);

            for (int n = 0; n < i; n++) {

                fifoBuffer.addFirst(h1);
                fifoBuffer.removeLast();
            }

            Assertions.assertThat(fifoBuffer.size()).isEqualTo(0);
            Assertions.assertThat(fifoBuffer.getFirst()).isSameAs(null);
            Assertions.assertThat(fifoBuffer.getLast()).isSameAs(null);
            fifoBuffer.addFirst(h1);
            Assertions.assertThat(fifoBuffer.getFirst()).isSameAs(h1);
            Assertions.assertThat(fifoBuffer.getLast()).isSameAs(h1);
            Assertions.assertThat(fifoBuffer.size()).isEqualTo(1);
            fifoBuffer.addFirst(h2);
            Assertions.assertThat(fifoBuffer.size()).isEqualTo(2);
            Assertions.assertThat(fifoBuffer.getFirst()).isSameAs(h2);
            Assertions.assertThat(fifoBuffer.getLast()).isSameAs(h1);
            fifoBuffer.addFirst(h3);
            Assertions.assertThat(fifoBuffer.size()).isEqualTo(3);
            Assertions.assertThat(fifoBuffer.getFirst()).isSameAs(h3);
            Assertions.assertThat(fifoBuffer.getLast()).isSameAs(h1);
            fifoBuffer.addFirst(h4);
            Assertions.assertThat(fifoBuffer.size()).isEqualTo(4);
            Assertions.assertThat(fifoBuffer.getFirst()).isSameAs(h4);
            Assertions.assertThat(fifoBuffer.getLast()).isSameAs(h1);

            Assertions.assertThat(fifoBuffer.get(0)).isSameAs(h4);
            Assertions.assertThat(fifoBuffer.get(1)).isSameAs(h3);
            Assertions.assertThat(fifoBuffer.get(2)).isSameAs(h2);
            Assertions.assertThat(fifoBuffer.get(3)).isSameAs(h1);
        }
    }

}


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

class TestFifoLinkedList {

    @Test
    void testAddRemoveCycle() {

        final FifoLinkedList fifoLinkedList = new FifoLinkedList();

        final HPackHeader h1 = new HPackHeader("h", "1");
        final HPackHeader h2 = new HPackHeader("h", "2");
        final HPackHeader h3 = new HPackHeader("h", "3");
        final HPackHeader h4 = new HPackHeader("h", "4");

        for (int i = 0; i < 5; i++) {
            Assertions.assertThat(fifoLinkedList.size()).isEqualTo(0);
            Assertions.assertThat(fifoLinkedList.getFirst()).isSameAs(null);
            Assertions.assertThat(fifoLinkedList.getLast()).isSameAs(null);

            fifoLinkedList.addFirst(h1);
            Assertions.assertThat(fifoLinkedList.size()).isEqualTo(1);
            Assertions.assertThat(fifoLinkedList.getFirst()).isSameAs(h1);
            Assertions.assertThat(fifoLinkedList.getLast()).isSameAs(h1);

            fifoLinkedList.addFirst(h2);
            Assertions.assertThat(fifoLinkedList.size()).isEqualTo(2);
            Assertions.assertThat(fifoLinkedList.getFirst()).isSameAs(h2);
            Assertions.assertThat(fifoLinkedList.getLast()).isSameAs(h1);

            fifoLinkedList.addFirst(h3);
            Assertions.assertThat(fifoLinkedList.size()).isEqualTo(3);
            Assertions.assertThat(fifoLinkedList.getFirst()).isSameAs(h3);
            Assertions.assertThat(fifoLinkedList.getLast()).isSameAs(h1);

            fifoLinkedList.addFirst(h4);
            Assertions.assertThat(fifoLinkedList.size()).isEqualTo(4);
            Assertions.assertThat(fifoLinkedList.getFirst()).isSameAs(h4);
            Assertions.assertThat(fifoLinkedList.getLast()).isSameAs(h1);

            fifoLinkedList.removeLast();
            Assertions.assertThat(fifoLinkedList.size()).isEqualTo(3);
            Assertions.assertThat(fifoLinkedList.getFirst()).isSameAs(h4);
            Assertions.assertThat(fifoLinkedList.getLast()).isSameAs(h2);

            fifoLinkedList.removeLast();
            Assertions.assertThat(fifoLinkedList.size()).isEqualTo(2);
            Assertions.assertThat(fifoLinkedList.getFirst()).isSameAs(h4);
            Assertions.assertThat(fifoLinkedList.getLast()).isSameAs(h3);

            fifoLinkedList.removeLast();
            Assertions.assertThat(fifoLinkedList.size()).isEqualTo(1);
            Assertions.assertThat(fifoLinkedList.getFirst()).isSameAs(h4);
            Assertions.assertThat(fifoLinkedList.getLast()).isSameAs(h4);

            fifoLinkedList.removeLast();
            Assertions.assertThat(fifoLinkedList.size()).isEqualTo(0);
            Assertions.assertThat(fifoLinkedList.getFirst()).isSameAs(null);
            Assertions.assertThat(fifoLinkedList.getLast()).isSameAs(null);
        }
    }

    @Test
    void testGetIndex() {

        final FifoLinkedList fifoLinkedList = new FifoLinkedList();

        final HPackHeader h1 = new HPackHeader("h", "1");
        final HPackHeader h2 = new HPackHeader("h", "2");
        final HPackHeader h3 = new HPackHeader("h", "3");
        final HPackHeader h4 = new HPackHeader("h", "4");

        final FifoLinkedList.InternalNode node1 = fifoLinkedList.addFirst(h1);
        final FifoLinkedList.InternalNode node2 = fifoLinkedList.addFirst(h2);
        final FifoLinkedList.InternalNode node3 = fifoLinkedList.addFirst(h3);
        final FifoLinkedList.InternalNode node4 = fifoLinkedList.addFirst(h4);

        Assertions.assertThat(fifoLinkedList.getIndex(node4)).isEqualTo(0);
        Assertions.assertThat(fifoLinkedList.getIndex(node3)).isEqualTo(1);
        Assertions.assertThat(fifoLinkedList.getIndex(node2)).isEqualTo(2);
        Assertions.assertThat(fifoLinkedList.getIndex(node1)).isEqualTo(3);

        Assertions.assertThat(fifoLinkedList.size()).isEqualTo(4);
        Assertions.assertThat(fifoLinkedList.get(0)).isSameAs(h4);
        Assertions.assertThat(fifoLinkedList.get(1)).isSameAs(h3);
        Assertions.assertThat(fifoLinkedList.get(2)).isSameAs(h2);
        Assertions.assertThat(fifoLinkedList.get(3)).isSameAs(h1);

        fifoLinkedList.removeLast();

        Assertions.assertThat(fifoLinkedList.getIndex(node4)).isEqualTo(0);
        Assertions.assertThat(fifoLinkedList.getIndex(node3)).isEqualTo(1);
        Assertions.assertThat(fifoLinkedList.getIndex(node2)).isEqualTo(2);
        Assertions.assertThat(fifoLinkedList.getIndex(node1)).isEqualTo(-1);

        Assertions.assertThat(fifoLinkedList.size()).isEqualTo(3);
        Assertions.assertThat(fifoLinkedList.get(0)).isSameAs(h4);
        Assertions.assertThat(fifoLinkedList.get(1)).isSameAs(h3);
        Assertions.assertThat(fifoLinkedList.get(2)).isSameAs(h2);

        fifoLinkedList.removeLast();

        Assertions.assertThat(fifoLinkedList.getIndex(node4)).isEqualTo(0);
        Assertions.assertThat(fifoLinkedList.getIndex(node3)).isEqualTo(1);
        Assertions.assertThat(fifoLinkedList.getIndex(node2)).isEqualTo(-1);
        Assertions.assertThat(fifoLinkedList.getIndex(node1)).isEqualTo(-1);

        Assertions.assertThat(fifoLinkedList.size()).isEqualTo(2);
        Assertions.assertThat(fifoLinkedList.get(0)).isSameAs(h4);
        Assertions.assertThat(fifoLinkedList.get(1)).isSameAs(h3);

        fifoLinkedList.removeLast();

        Assertions.assertThat(fifoLinkedList.getIndex(node4)).isEqualTo(0);
        Assertions.assertThat(fifoLinkedList.getIndex(node3)).isEqualTo(-1);
        Assertions.assertThat(fifoLinkedList.getIndex(node2)).isEqualTo(-1);
        Assertions.assertThat(fifoLinkedList.getIndex(node1)).isEqualTo(-1);

        Assertions.assertThat(fifoLinkedList.size()).isEqualTo(1);
        Assertions.assertThat(fifoLinkedList.get(0)).isSameAs(h4);

        fifoLinkedList.removeLast();

        Assertions.assertThat(fifoLinkedList.getIndex(node4)).isEqualTo(-1);
        Assertions.assertThat(fifoLinkedList.getIndex(node3)).isEqualTo(-1);
        Assertions.assertThat(fifoLinkedList.getIndex(node2)).isEqualTo(-1);
        Assertions.assertThat(fifoLinkedList.getIndex(node1)).isEqualTo(-1);

        Assertions.assertThat(fifoLinkedList.size()).isEqualTo(0);
    }
}


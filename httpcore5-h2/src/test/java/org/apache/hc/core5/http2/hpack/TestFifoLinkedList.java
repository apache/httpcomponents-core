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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestFifoLinkedList {

    @Test
    public void testAddRemoveCycle() throws Exception {

        final FifoLinkedList fifoLinkedList = new FifoLinkedList();

        final HPackHeader h1 = new HPackHeader("h", "1");
        final HPackHeader h2 = new HPackHeader("h", "2");
        final HPackHeader h3 = new HPackHeader("h", "3");
        final HPackHeader h4 = new HPackHeader("h", "4");

        for (int i = 0; i < 5; i++) {
            Assertions.assertEquals(0, fifoLinkedList.size());
            Assertions.assertSame(null, fifoLinkedList.getFirst());
            Assertions.assertSame(null, fifoLinkedList.getLast());

            fifoLinkedList.addFirst(h1);
            Assertions.assertEquals(1, fifoLinkedList.size());
            Assertions.assertSame(h1, fifoLinkedList.getFirst());
            Assertions.assertSame(h1, fifoLinkedList.getLast());

            fifoLinkedList.addFirst(h2);
            Assertions.assertEquals(2, fifoLinkedList.size());
            Assertions.assertSame(h2, fifoLinkedList.getFirst());
            Assertions.assertSame(h1, fifoLinkedList.getLast());

            fifoLinkedList.addFirst(h3);
            Assertions.assertEquals(3, fifoLinkedList.size());
            Assertions.assertSame(h3, fifoLinkedList.getFirst());
            Assertions.assertSame(h1, fifoLinkedList.getLast());

            fifoLinkedList.addFirst(h4);
            Assertions.assertEquals(4, fifoLinkedList.size());
            Assertions.assertSame(h4, fifoLinkedList.getFirst());
            Assertions.assertSame(h1, fifoLinkedList.getLast());

            fifoLinkedList.removeLast();
            Assertions.assertEquals(3, fifoLinkedList.size());
            Assertions.assertSame(h4, fifoLinkedList.getFirst());
            Assertions.assertSame(h2, fifoLinkedList.getLast());

            fifoLinkedList.removeLast();
            Assertions.assertEquals(2, fifoLinkedList.size());
            Assertions.assertSame(h4, fifoLinkedList.getFirst());
            Assertions.assertSame(h3, fifoLinkedList.getLast());

            fifoLinkedList.removeLast();
            Assertions.assertEquals(1, fifoLinkedList.size());
            Assertions.assertSame(h4, fifoLinkedList.getFirst());
            Assertions.assertSame(h4, fifoLinkedList.getLast());

            fifoLinkedList.removeLast();
            Assertions.assertEquals(0, fifoLinkedList.size());
            Assertions.assertSame(null, fifoLinkedList.getFirst());
            Assertions.assertSame(null, fifoLinkedList.getLast());
        }
    }

    @Test
    public void testGetIndex() throws Exception {

        final FifoLinkedList fifoLinkedList = new FifoLinkedList();

        final HPackHeader h1 = new HPackHeader("h", "1");
        final HPackHeader h2 = new HPackHeader("h", "2");
        final HPackHeader h3 = new HPackHeader("h", "3");
        final HPackHeader h4 = new HPackHeader("h", "4");

        final FifoLinkedList.InternalNode node1 = fifoLinkedList.addFirst(h1);
        final FifoLinkedList.InternalNode node2 = fifoLinkedList.addFirst(h2);
        final FifoLinkedList.InternalNode node3 = fifoLinkedList.addFirst(h3);
        final FifoLinkedList.InternalNode node4 = fifoLinkedList.addFirst(h4);

        Assertions.assertEquals(0, fifoLinkedList.getIndex(node4));
        Assertions.assertEquals(1, fifoLinkedList.getIndex(node3));
        Assertions.assertEquals(2, fifoLinkedList.getIndex(node2));
        Assertions.assertEquals(3, fifoLinkedList.getIndex(node1));

        Assertions.assertEquals(4, fifoLinkedList.size());
        Assertions.assertSame(h4, fifoLinkedList.get(0));
        Assertions.assertSame(h3, fifoLinkedList.get(1));
        Assertions.assertSame(h2, fifoLinkedList.get(2));
        Assertions.assertSame(h1, fifoLinkedList.get(3));

        fifoLinkedList.removeLast();

        Assertions.assertEquals(0, fifoLinkedList.getIndex(node4));
        Assertions.assertEquals(1, fifoLinkedList.getIndex(node3));
        Assertions.assertEquals(2, fifoLinkedList.getIndex(node2));
        Assertions.assertEquals(-1, fifoLinkedList.getIndex(node1));

        Assertions.assertEquals(3, fifoLinkedList.size());
        Assertions.assertSame(h4, fifoLinkedList.get(0));
        Assertions.assertSame(h3, fifoLinkedList.get(1));
        Assertions.assertSame(h2, fifoLinkedList.get(2));

        fifoLinkedList.removeLast();

        Assertions.assertEquals(0, fifoLinkedList.getIndex(node4));
        Assertions.assertEquals(1, fifoLinkedList.getIndex(node3));
        Assertions.assertEquals(-1, fifoLinkedList.getIndex(node2));
        Assertions.assertEquals(-1, fifoLinkedList.getIndex(node1));

        Assertions.assertEquals(2, fifoLinkedList.size());
        Assertions.assertSame(h4, fifoLinkedList.get(0));
        Assertions.assertSame(h3, fifoLinkedList.get(1));

        fifoLinkedList.removeLast();

        Assertions.assertEquals(0, fifoLinkedList.getIndex(node4));
        Assertions.assertEquals(-1, fifoLinkedList.getIndex(node3));
        Assertions.assertEquals(-1, fifoLinkedList.getIndex(node2));
        Assertions.assertEquals(-1, fifoLinkedList.getIndex(node1));

        Assertions.assertEquals(1, fifoLinkedList.size());
        Assertions.assertSame(h4, fifoLinkedList.get(0));

        fifoLinkedList.removeLast();

        Assertions.assertEquals(-1, fifoLinkedList.getIndex(node4));
        Assertions.assertEquals(-1, fifoLinkedList.getIndex(node3));
        Assertions.assertEquals(-1, fifoLinkedList.getIndex(node2));
        Assertions.assertEquals(-1, fifoLinkedList.getIndex(node1));

        Assertions.assertEquals(0, fifoLinkedList.size());
    }
}


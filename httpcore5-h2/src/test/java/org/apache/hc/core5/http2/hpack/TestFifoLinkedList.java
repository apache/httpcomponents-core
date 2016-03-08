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

import org.junit.Assert;
import org.junit.Test;

public class TestFifoLinkedList {

    @Test
    public void testAddRemoveCycle() throws Exception {

        final FifoLinkedList fifoLinkedList = new FifoLinkedList();

        final HPackHeader h1 = new HPackHeader("h", "1");
        final HPackHeader h2 = new HPackHeader("h", "2");
        final HPackHeader h3 = new HPackHeader("h", "3");
        final HPackHeader h4 = new HPackHeader("h", "4");

        for (int i = 0; i < 5; i++) {
            Assert.assertEquals(0, fifoLinkedList.size());
            Assert.assertSame(null, fifoLinkedList.getFirst());
            Assert.assertSame(null, fifoLinkedList.getLast());

            fifoLinkedList.addFirst(h1);
            Assert.assertEquals(1, fifoLinkedList.size());
            Assert.assertSame(h1, fifoLinkedList.getFirst());
            Assert.assertSame(h1, fifoLinkedList.getLast());

            fifoLinkedList.addFirst(h2);
            Assert.assertEquals(2, fifoLinkedList.size());
            Assert.assertSame(h2, fifoLinkedList.getFirst());
            Assert.assertSame(h1, fifoLinkedList.getLast());

            fifoLinkedList.addFirst(h3);
            Assert.assertEquals(3, fifoLinkedList.size());
            Assert.assertSame(h3, fifoLinkedList.getFirst());
            Assert.assertSame(h1, fifoLinkedList.getLast());

            fifoLinkedList.addFirst(h4);
            Assert.assertEquals(4, fifoLinkedList.size());
            Assert.assertSame(h4, fifoLinkedList.getFirst());
            Assert.assertSame(h1, fifoLinkedList.getLast());

            fifoLinkedList.removeLast();
            Assert.assertEquals(3, fifoLinkedList.size());
            Assert.assertSame(h4, fifoLinkedList.getFirst());
            Assert.assertSame(h2, fifoLinkedList.getLast());

            fifoLinkedList.removeLast();
            Assert.assertEquals(2, fifoLinkedList.size());
            Assert.assertSame(h4, fifoLinkedList.getFirst());
            Assert.assertSame(h3, fifoLinkedList.getLast());

            fifoLinkedList.removeLast();
            Assert.assertEquals(1, fifoLinkedList.size());
            Assert.assertSame(h4, fifoLinkedList.getFirst());
            Assert.assertSame(h4, fifoLinkedList.getLast());

            fifoLinkedList.removeLast();
            Assert.assertEquals(0, fifoLinkedList.size());
            Assert.assertSame(null, fifoLinkedList.getFirst());
            Assert.assertSame(null, fifoLinkedList.getLast());
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

        Assert.assertEquals(0, fifoLinkedList.getIndex(node4));
        Assert.assertEquals(1, fifoLinkedList.getIndex(node3));
        Assert.assertEquals(2, fifoLinkedList.getIndex(node2));
        Assert.assertEquals(3, fifoLinkedList.getIndex(node1));

        Assert.assertEquals(4, fifoLinkedList.size());
        Assert.assertSame(h4, fifoLinkedList.get(0));
        Assert.assertSame(h3, fifoLinkedList.get(1));
        Assert.assertSame(h2, fifoLinkedList.get(2));
        Assert.assertSame(h1, fifoLinkedList.get(3));

        fifoLinkedList.removeLast();

        Assert.assertEquals(0, fifoLinkedList.getIndex(node4));
        Assert.assertEquals(1, fifoLinkedList.getIndex(node3));
        Assert.assertEquals(2, fifoLinkedList.getIndex(node2));
        Assert.assertEquals(-1, fifoLinkedList.getIndex(node1));

        Assert.assertEquals(3, fifoLinkedList.size());
        Assert.assertSame(h4, fifoLinkedList.get(0));
        Assert.assertSame(h3, fifoLinkedList.get(1));
        Assert.assertSame(h2, fifoLinkedList.get(2));

        fifoLinkedList.removeLast();

        Assert.assertEquals(0, fifoLinkedList.getIndex(node4));
        Assert.assertEquals(1, fifoLinkedList.getIndex(node3));
        Assert.assertEquals(-1, fifoLinkedList.getIndex(node2));
        Assert.assertEquals(-1, fifoLinkedList.getIndex(node1));

        Assert.assertEquals(2, fifoLinkedList.size());
        Assert.assertSame(h4, fifoLinkedList.get(0));
        Assert.assertSame(h3, fifoLinkedList.get(1));

        fifoLinkedList.removeLast();

        Assert.assertEquals(0, fifoLinkedList.getIndex(node4));
        Assert.assertEquals(-1, fifoLinkedList.getIndex(node3));
        Assert.assertEquals(-1, fifoLinkedList.getIndex(node2));
        Assert.assertEquals(-1, fifoLinkedList.getIndex(node1));

        Assert.assertEquals(1, fifoLinkedList.size());
        Assert.assertSame(h4, fifoLinkedList.get(0));

        fifoLinkedList.removeLast();

        Assert.assertEquals(-1, fifoLinkedList.getIndex(node4));
        Assert.assertEquals(-1, fifoLinkedList.getIndex(node3));
        Assert.assertEquals(-1, fifoLinkedList.getIndex(node2));
        Assert.assertEquals(-1, fifoLinkedList.getIndex(node1));

        Assert.assertEquals(0, fifoLinkedList.size());
    }
}


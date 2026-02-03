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


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link NamedElementChain}.
 */
class TestNamedElementChain {

    @Test
    void testBasics() {
        final NamedElementChain<Character> list = new NamedElementChain<>();
        Assertions.assertNull(list.getFirst());
        Assertions.assertNull(list.getLast());

        final NamedElementChain<Character>.Node nodeA = list.addFirst('a', "a");

        Assertions.assertSame(nodeA, list.getFirst());
        Assertions.assertSame(nodeA, list.getLast());

        final NamedElementChain<Character>.Node nodeB = list.addLast('b', "b");

        Assertions.assertSame(nodeA, list.getFirst());
        Assertions.assertSame(nodeB, list.getLast());

        final NamedElementChain<Character>.Node nodeZ = list.addLast('z', "z");

        Assertions.assertSame(nodeA, list.getFirst());
        Assertions.assertSame(nodeZ, list.getLast());

        Assertions.assertNull(nodeA.getPrevious());
        Assertions.assertSame(nodeB, nodeA.getNext());
        Assertions.assertSame(nodeA, nodeB.getPrevious());
        Assertions.assertSame(nodeZ, nodeB.getNext());
        Assertions.assertSame(nodeB, nodeZ.getPrevious());
        Assertions.assertNull(nodeZ.getNext());

        final NamedElementChain<Character>.Node nodeD = list.addAfter("b", 'd', "d");
        Assertions.assertSame(nodeB, nodeD.getPrevious());
        Assertions.assertSame(nodeZ, nodeD.getNext());
        Assertions.assertSame(nodeD, nodeB.getNext());
        Assertions.assertSame(nodeD, nodeZ.getPrevious());

        final NamedElementChain<Character>.Node nodeC = list.addBefore("d", 'c', "c");
        Assertions.assertSame(nodeB, nodeC.getPrevious());
        Assertions.assertSame(nodeD, nodeC.getNext());
        Assertions.assertSame(nodeC, nodeB.getNext());
        Assertions.assertSame(nodeC, nodeD.getPrevious());
        Assertions.assertEquals(5, list.getSize());

        Assertions.assertTrue(list.remove("a"));
        Assertions.assertTrue(list.remove("z"));
        Assertions.assertTrue(list.remove("c"));
        Assertions.assertFalse(list.remove("c"));
        Assertions.assertFalse(list.remove("blah"));

        Assertions.assertSame(nodeB, list.getFirst());
        Assertions.assertSame(nodeD, list.getLast());

        Assertions.assertEquals(2, list.getSize());
        Assertions.assertNull(list.addBefore("blah", 'e', "e"));
        Assertions.assertEquals(2, list.getSize());

        Assertions.assertNull(list.addAfter("yada", 'e', "e"));
        Assertions.assertEquals(2, list.getSize());
    }

    @Test
    void testFind() {
        final NamedElementChain<Character> list = new NamedElementChain<>();
        list.addLast('c', "c");
        Assertions.assertNotNull(list.find("c"));
        Assertions.assertNull(list.find("a"));
    }

    @Test
    void testReplace() {
        final NamedElementChain<Character> list = new NamedElementChain<>();
        list.addLast('c', "c");
        final boolean found = list.replace("c",'z' );
        Assertions.assertTrue(found);
        Assertions.assertEquals('z', list.find("c").getValue());
        Assertions.assertEquals("c", list.find("c").getName());
        final boolean notFound = list.replace("X",'z' );
        Assertions.assertFalse(notFound);

    }
}

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


import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link NamedElementChain}.
 */
class TestNamedElementChain {

    @Test
    void testBasics() {
        final NamedElementChain<Character> list = new NamedElementChain<>();
        Assertions.assertThat(list.getFirst()).isNull();
        Assertions.assertThat(list.getLast()).isNull();

        final NamedElementChain<Character>.Node nodeA = list.addFirst('a', "a");

        Assertions.assertThat(list.getFirst()).isSameAs(nodeA);
        Assertions.assertThat(list.getLast()).isSameAs(nodeA);

        final NamedElementChain<Character>.Node nodeB = list.addLast('b', "b");

        Assertions.assertThat(list.getFirst()).isSameAs(nodeA);
        Assertions.assertThat(list.getLast()).isSameAs(nodeB);

        final NamedElementChain<Character>.Node nodeZ = list.addLast('z', "z");

        Assertions.assertThat(list.getFirst()).isSameAs(nodeA);
        Assertions.assertThat(list.getLast()).isSameAs(nodeZ);

        Assertions.assertThat(nodeA.getPrevious()).isNull();
        Assertions.assertThat(nodeA.getNext()).isSameAs(nodeB);
        Assertions.assertThat(nodeB.getPrevious()).isSameAs(nodeA);
        Assertions.assertThat(nodeB.getNext()).isSameAs(nodeZ);
        Assertions.assertThat(nodeZ.getPrevious()).isSameAs(nodeB);
        Assertions.assertThat(nodeZ.getNext()).isNull();

        final NamedElementChain<Character>.Node nodeD = list.addAfter("b", 'd', "d");
        Assertions.assertThat(nodeD.getPrevious()).isSameAs(nodeB);
        Assertions.assertThat(nodeD.getNext()).isSameAs(nodeZ);
        Assertions.assertThat(nodeB.getNext()).isSameAs(nodeD);
        Assertions.assertThat(nodeZ.getPrevious()).isSameAs(nodeD);

        final NamedElementChain<Character>.Node nodeC = list.addBefore("d", 'c', "c");
        Assertions.assertThat(nodeC.getPrevious()).isSameAs(nodeB);
        Assertions.assertThat(nodeC.getNext()).isSameAs(nodeD);
        Assertions.assertThat(nodeB.getNext()).isSameAs(nodeC);
        Assertions.assertThat(nodeD.getPrevious()).isSameAs(nodeC);
        Assertions.assertThat(list.getSize()).isEqualTo(5);

        Assertions.assertThat(list.remove("a")).isTrue();
        Assertions.assertThat(list.remove("z")).isTrue();
        Assertions.assertThat(list.remove("c")).isTrue();
        Assertions.assertThat(list.remove("c")).isFalse();
        Assertions.assertThat(list.remove("blah")).isFalse();

        Assertions.assertThat(list.getFirst()).isSameAs(nodeB);
        Assertions.assertThat(list.getLast()).isSameAs(nodeD);

        Assertions.assertThat(list.getSize()).isEqualTo(2);
        Assertions.assertThat(list.addBefore("blah", 'e', "e")).isNull();
        Assertions.assertThat(list.getSize()).isEqualTo(2);

        Assertions.assertThat(list.addAfter("yada", 'e', "e")).isNull();
        Assertions.assertThat(list.getSize()).isEqualTo(2);
    }

    @Test
    void testFind() {
        final NamedElementChain<Character> list = new NamedElementChain<>();
        list.addLast('c', "c");
        Assertions.assertThat(list.find("c")).isNotNull();
        Assertions.assertThat(list.find("a")).isNull();
    }

    @Test
    void testReplace() {
        final NamedElementChain<Character> list = new NamedElementChain<>();
        list.addLast('c', "c");
        final boolean found = list.replace("c",'z' );
        Assertions.assertThat(found).isTrue();
        Assertions.assertThat(list.find("c").getValue()).isEqualTo('z');
        Assertions.assertThat(list.find("c").getName()).isEqualTo("c");
        final boolean notFound = list.replace("X",'z' );
        Assertions.assertThat(notFound).isFalse();

    }
}

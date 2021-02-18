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

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;

/**
 * Tests for {@link NamedElementChain}.
 */
public class TestNamedElementChain {

    @Test
    public void testBasics() {
        final NamedElementChain<Character> list = new NamedElementChain<>();
        MatcherAssert.assertThat(list.getFirst(), CoreMatchers.nullValue());
        MatcherAssert.assertThat(list.getLast(), CoreMatchers.nullValue());

        final NamedElementChain<Character>.Node nodeA = list.addFirst('a', "a");

        MatcherAssert.assertThat(list.getFirst(), CoreMatchers.sameInstance(nodeA));
        MatcherAssert.assertThat(list.getLast(), CoreMatchers.sameInstance(nodeA));

        final NamedElementChain<Character>.Node nodeB = list.addLast('b', "b");

        MatcherAssert.assertThat(list.getFirst(), CoreMatchers.sameInstance(nodeA));
        MatcherAssert.assertThat(list.getLast(), CoreMatchers.sameInstance(nodeB));

        final NamedElementChain<Character>.Node nodeZ = list.addLast('z', "z");

        MatcherAssert.assertThat(list.getFirst(), CoreMatchers.sameInstance(nodeA));
        MatcherAssert.assertThat(list.getLast(), CoreMatchers.sameInstance(nodeZ));

        MatcherAssert.assertThat(nodeA.getPrevious(), CoreMatchers.nullValue());
        MatcherAssert.assertThat(nodeA.getNext(), CoreMatchers.sameInstance(nodeB));
        MatcherAssert.assertThat(nodeB.getPrevious(), CoreMatchers.sameInstance(nodeA));
        MatcherAssert.assertThat(nodeB.getNext(), CoreMatchers.sameInstance(nodeZ));
        MatcherAssert.assertThat(nodeZ.getPrevious(), CoreMatchers.sameInstance(nodeB));
        MatcherAssert.assertThat(nodeZ.getNext(), CoreMatchers.nullValue());

        final NamedElementChain<Character>.Node nodeD = list.addAfter("b", 'd', "d");
        MatcherAssert.assertThat(nodeD.getPrevious(), CoreMatchers.sameInstance(nodeB));
        MatcherAssert.assertThat(nodeD.getNext(), CoreMatchers.sameInstance(nodeZ));
        MatcherAssert.assertThat(nodeB.getNext(), CoreMatchers.sameInstance(nodeD));
        MatcherAssert.assertThat(nodeZ.getPrevious(), CoreMatchers.sameInstance(nodeD));

        final NamedElementChain<Character>.Node nodeC = list.addBefore("d", 'c', "c");
        MatcherAssert.assertThat(nodeC.getPrevious(), CoreMatchers.sameInstance(nodeB));
        MatcherAssert.assertThat(nodeC.getNext(), CoreMatchers.sameInstance(nodeD));
        MatcherAssert.assertThat(nodeB.getNext(), CoreMatchers.sameInstance(nodeC));
        MatcherAssert.assertThat(nodeD.getPrevious(), CoreMatchers.sameInstance(nodeC));
        MatcherAssert.assertThat(list.getSize(), CoreMatchers.equalTo(5));

        MatcherAssert.assertThat(list.remove("a"), CoreMatchers.is(true));
        MatcherAssert.assertThat(list.remove("z"), CoreMatchers.is(true));
        MatcherAssert.assertThat(list.remove("c"), CoreMatchers.is(true));
        MatcherAssert.assertThat(list.remove("c"), CoreMatchers.is(false));
        MatcherAssert.assertThat(list.remove("blah"), CoreMatchers.is(false));

        MatcherAssert.assertThat(list.getFirst(), CoreMatchers.sameInstance(nodeB));
        MatcherAssert.assertThat(list.getLast(), CoreMatchers.sameInstance(nodeD));

        MatcherAssert.assertThat(list.getSize(), CoreMatchers.equalTo(2));
        MatcherAssert.assertThat(list.addBefore("blah", 'e', "e"), CoreMatchers.nullValue());
        MatcherAssert.assertThat(list.getSize(), CoreMatchers.equalTo(2));

        MatcherAssert.assertThat(list.addAfter("yada", 'e', "e"), CoreMatchers.nullValue());
        MatcherAssert.assertThat(list.getSize(), CoreMatchers.equalTo(2));
    }

}
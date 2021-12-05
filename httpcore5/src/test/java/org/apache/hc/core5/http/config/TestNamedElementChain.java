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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.CoreMatchers.is;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link NamedElementChain}.
 */
public class TestNamedElementChain {

    @Test
    public void testBasics() {
        final NamedElementChain<Character> list = new NamedElementChain<>();
        assertThat(list.getFirst(), CoreMatchers.nullValue());
        assertThat(list.getLast(), CoreMatchers.nullValue());

        final NamedElementChain<Character>.Node nodeA = list.addFirst('a', "a");

        assertThat(list.getFirst(), CoreMatchers.sameInstance(nodeA));
        assertThat(list.getLast(), CoreMatchers.sameInstance(nodeA));

        final NamedElementChain<Character>.Node nodeB = list.addLast('b', "b");

        assertThat(list.getFirst(), CoreMatchers.sameInstance(nodeA));
        assertThat(list.getLast(), CoreMatchers.sameInstance(nodeB));

        final NamedElementChain<Character>.Node nodeZ = list.addLast('z', "z");

        assertThat(list.getFirst(), CoreMatchers.sameInstance(nodeA));
        assertThat(list.getLast(), CoreMatchers.sameInstance(nodeZ));

        assertThat(nodeA.getPrevious(), CoreMatchers.nullValue());
        assertThat(nodeA.getNext(), CoreMatchers.sameInstance(nodeB));
        assertThat(nodeB.getPrevious(), CoreMatchers.sameInstance(nodeA));
        assertThat(nodeB.getNext(), CoreMatchers.sameInstance(nodeZ));
        assertThat(nodeZ.getPrevious(), CoreMatchers.sameInstance(nodeB));
        assertThat(nodeZ.getNext(), CoreMatchers.nullValue());

        final NamedElementChain<Character>.Node nodeD = list.addAfter("b", 'd', "d");
        assertThat(nodeD.getPrevious(), CoreMatchers.sameInstance(nodeB));
        assertThat(nodeD.getNext(), CoreMatchers.sameInstance(nodeZ));
        assertThat(nodeB.getNext(), CoreMatchers.sameInstance(nodeD));
        assertThat(nodeZ.getPrevious(), CoreMatchers.sameInstance(nodeD));

        final NamedElementChain<Character>.Node nodeC = list.addBefore("d", 'c', "c");
        assertThat(nodeC.getPrevious(), CoreMatchers.sameInstance(nodeB));
        assertThat(nodeC.getNext(), CoreMatchers.sameInstance(nodeD));
        assertThat(nodeB.getNext(), CoreMatchers.sameInstance(nodeC));
        assertThat(nodeD.getPrevious(), CoreMatchers.sameInstance(nodeC));
        assertThat(list.getSize(), CoreMatchers.equalTo(5));

        assertThat(list.remove("a"), CoreMatchers.is(true));
        assertThat(list.remove("z"), CoreMatchers.is(true));
        assertThat(list.remove("c"), CoreMatchers.is(true));
        assertThat(list.remove("c"), CoreMatchers.is(false));
        assertThat(list.remove("blah"), CoreMatchers.is(false));

        assertThat(list.getFirst(), CoreMatchers.sameInstance(nodeB));
        assertThat(list.getLast(), CoreMatchers.sameInstance(nodeD));

        assertThat(list.getSize(), CoreMatchers.equalTo(2));
        assertThat(list.addBefore("blah", 'e', "e"), CoreMatchers.nullValue());
        assertThat(list.getSize(), CoreMatchers.equalTo(2));

        assertThat(list.addAfter("yada", 'e', "e"), CoreMatchers.nullValue());
        assertThat(list.getSize(), CoreMatchers.equalTo(2));
    }

    @Test
    public void testFind() {
        final NamedElementChain<Character> list = new NamedElementChain<>();
        list.addLast('c', "c");
        assertThat(list.find("c"), notNullValue());
        assertThat(list.find("a"), nullValue());
    }

    @Test
    public void testReplace() {
        final NamedElementChain<Character> list = new NamedElementChain<>();
        list.addLast('c', "c");
        final boolean found = list.replace("c",'z' );
        assertThat(found, is(true));
        assertThat(list.find("c").getValue(), equalTo('z'));
        assertThat(list.find("c").getName(), equalTo("c"));
        final boolean notFound = list.replace("X",'z' );
        assertThat(notFound, is(false));

    }
}

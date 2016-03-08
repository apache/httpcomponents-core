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

public class TestFifoBuffer {

    @Test
    public void testAddRemoveCycle() throws Exception {

        final FifoBuffer fifoBuffer = new FifoBuffer(5);

        final HPackHeader h1 = new HPackHeader("h", "1");
        final HPackHeader h2 = new HPackHeader("h", "2");
        final HPackHeader h3 = new HPackHeader("h", "3");
        final HPackHeader h4 = new HPackHeader("h", "4");

        for (int i = 0; i < 20; i++) {
            Assert.assertEquals(0, fifoBuffer.size());
            Assert.assertSame(null, fifoBuffer.getFirst());
            Assert.assertSame(null, fifoBuffer.getLast());
            fifoBuffer.addFirst(h1);
            Assert.assertSame(h1, fifoBuffer.getFirst());
            Assert.assertSame(h1, fifoBuffer.getLast());
            Assert.assertEquals(1, fifoBuffer.size());
            fifoBuffer.addFirst(h2);
            Assert.assertEquals(2, fifoBuffer.size());
            Assert.assertSame(h2, fifoBuffer.getFirst());
            Assert.assertSame(h1, fifoBuffer.getLast());
            fifoBuffer.addFirst(h3);
            Assert.assertEquals(3, fifoBuffer.size());
            Assert.assertSame(h3, fifoBuffer.getFirst());
            Assert.assertSame(h1, fifoBuffer.getLast());
            fifoBuffer.addFirst(h4);
            Assert.assertEquals(4, fifoBuffer.size());
            Assert.assertSame(h4, fifoBuffer.getFirst());
            Assert.assertSame(h1, fifoBuffer.getLast());

            Assert.assertSame(h4, fifoBuffer.get(0));
            Assert.assertSame(h3, fifoBuffer.get(1));
            Assert.assertSame(h2, fifoBuffer.get(2));
            Assert.assertSame(h1, fifoBuffer.get(3));

            fifoBuffer.removeLast();
            Assert.assertEquals(3, fifoBuffer.size());
            Assert.assertSame(h4, fifoBuffer.getFirst());
            Assert.assertSame(h2, fifoBuffer.getLast());
            fifoBuffer.removeLast();
            Assert.assertEquals(2, fifoBuffer.size());
            Assert.assertSame(h4, fifoBuffer.getFirst());
            Assert.assertSame(h3, fifoBuffer.getLast());
            fifoBuffer.removeLast();
            Assert.assertEquals(1, fifoBuffer.size());
            Assert.assertSame(h4, fifoBuffer.getFirst());
            Assert.assertSame(h4, fifoBuffer.getLast());
            fifoBuffer.removeLast();
            Assert.assertEquals(0, fifoBuffer.size());
            Assert.assertSame(null, fifoBuffer.getFirst());
            Assert.assertSame(null, fifoBuffer.getLast());
        }
    }

    @Test
    public void testExpand() throws Exception {

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

            Assert.assertEquals(0, fifoBuffer.size());
            Assert.assertSame(null, fifoBuffer.getFirst());
            Assert.assertSame(null, fifoBuffer.getLast());
            fifoBuffer.addFirst(h1);
            Assert.assertSame(h1, fifoBuffer.getFirst());
            Assert.assertSame(h1, fifoBuffer.getLast());
            Assert.assertEquals(1, fifoBuffer.size());
            fifoBuffer.addFirst(h2);
            Assert.assertEquals(2, fifoBuffer.size());
            Assert.assertSame(h2, fifoBuffer.getFirst());
            Assert.assertSame(h1, fifoBuffer.getLast());
            fifoBuffer.addFirst(h3);
            Assert.assertEquals(3, fifoBuffer.size());
            Assert.assertSame(h3, fifoBuffer.getFirst());
            Assert.assertSame(h1, fifoBuffer.getLast());
            fifoBuffer.addFirst(h4);
            Assert.assertEquals(4, fifoBuffer.size());
            Assert.assertSame(h4, fifoBuffer.getFirst());
            Assert.assertSame(h1, fifoBuffer.getLast());

            Assert.assertSame(h4, fifoBuffer.get(0));
            Assert.assertSame(h3, fifoBuffer.get(1));
            Assert.assertSame(h2, fifoBuffer.get(2));
            Assert.assertSame(h1, fifoBuffer.get(3));
        }
    }

}


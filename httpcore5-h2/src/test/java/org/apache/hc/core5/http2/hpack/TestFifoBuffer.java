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

public class TestFifoBuffer {

    @Test
    public void testAddRemoveCycle() throws Exception {

        final FifoBuffer fifoBuffer = new FifoBuffer(5);

        final HPackHeader h1 = new HPackHeader("h", "1");
        final HPackHeader h2 = new HPackHeader("h", "2");
        final HPackHeader h3 = new HPackHeader("h", "3");
        final HPackHeader h4 = new HPackHeader("h", "4");

        for (int i = 0; i < 20; i++) {
            Assertions.assertEquals(0, fifoBuffer.size());
            Assertions.assertSame(null, fifoBuffer.getFirst());
            Assertions.assertSame(null, fifoBuffer.getLast());
            fifoBuffer.addFirst(h1);
            Assertions.assertSame(h1, fifoBuffer.getFirst());
            Assertions.assertSame(h1, fifoBuffer.getLast());
            Assertions.assertEquals(1, fifoBuffer.size());
            fifoBuffer.addFirst(h2);
            Assertions.assertEquals(2, fifoBuffer.size());
            Assertions.assertSame(h2, fifoBuffer.getFirst());
            Assertions.assertSame(h1, fifoBuffer.getLast());
            fifoBuffer.addFirst(h3);
            Assertions.assertEquals(3, fifoBuffer.size());
            Assertions.assertSame(h3, fifoBuffer.getFirst());
            Assertions.assertSame(h1, fifoBuffer.getLast());
            fifoBuffer.addFirst(h4);
            Assertions.assertEquals(4, fifoBuffer.size());
            Assertions.assertSame(h4, fifoBuffer.getFirst());
            Assertions.assertSame(h1, fifoBuffer.getLast());

            Assertions.assertSame(h4, fifoBuffer.get(0));
            Assertions.assertSame(h3, fifoBuffer.get(1));
            Assertions.assertSame(h2, fifoBuffer.get(2));
            Assertions.assertSame(h1, fifoBuffer.get(3));

            fifoBuffer.removeLast();
            Assertions.assertEquals(3, fifoBuffer.size());
            Assertions.assertSame(h4, fifoBuffer.getFirst());
            Assertions.assertSame(h2, fifoBuffer.getLast());
            fifoBuffer.removeLast();
            Assertions.assertEquals(2, fifoBuffer.size());
            Assertions.assertSame(h4, fifoBuffer.getFirst());
            Assertions.assertSame(h3, fifoBuffer.getLast());
            fifoBuffer.removeLast();
            Assertions.assertEquals(1, fifoBuffer.size());
            Assertions.assertSame(h4, fifoBuffer.getFirst());
            Assertions.assertSame(h4, fifoBuffer.getLast());
            fifoBuffer.removeLast();
            Assertions.assertEquals(0, fifoBuffer.size());
            Assertions.assertSame(null, fifoBuffer.getFirst());
            Assertions.assertSame(null, fifoBuffer.getLast());
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

            Assertions.assertEquals(0, fifoBuffer.size());
            Assertions.assertSame(null, fifoBuffer.getFirst());
            Assertions.assertSame(null, fifoBuffer.getLast());
            fifoBuffer.addFirst(h1);
            Assertions.assertSame(h1, fifoBuffer.getFirst());
            Assertions.assertSame(h1, fifoBuffer.getLast());
            Assertions.assertEquals(1, fifoBuffer.size());
            fifoBuffer.addFirst(h2);
            Assertions.assertEquals(2, fifoBuffer.size());
            Assertions.assertSame(h2, fifoBuffer.getFirst());
            Assertions.assertSame(h1, fifoBuffer.getLast());
            fifoBuffer.addFirst(h3);
            Assertions.assertEquals(3, fifoBuffer.size());
            Assertions.assertSame(h3, fifoBuffer.getFirst());
            Assertions.assertSame(h1, fifoBuffer.getLast());
            fifoBuffer.addFirst(h4);
            Assertions.assertEquals(4, fifoBuffer.size());
            Assertions.assertSame(h4, fifoBuffer.getFirst());
            Assertions.assertSame(h1, fifoBuffer.getLast());

            Assertions.assertSame(h4, fifoBuffer.get(0));
            Assertions.assertSame(h3, fifoBuffer.get(1));
            Assertions.assertSame(h2, fifoBuffer.get(2));
            Assertions.assertSame(h1, fifoBuffer.get(3));
        }
    }

}


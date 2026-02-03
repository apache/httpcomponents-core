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

package org.apache.hc.core5.http.impl.nio;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestExpandableBuffer {

    @Test
    void testBasics() {
        final ExpandableBuffer buffer = new ExpandableBuffer(16);
        Assertions.assertEquals(ExpandableBuffer.Mode.INPUT, buffer.mode());
        Assertions.assertFalse(buffer.hasData());

        buffer.setInputMode();
        buffer.buffer().put(new byte[] { 0, 1, 2, 3, 4, 5});
        Assertions.assertTrue(buffer.hasData());
        Assertions.assertEquals(6, buffer.length());
        Assertions.assertEquals(16, buffer.buffer().capacity());
        Assertions.assertEquals(ExpandableBuffer.Mode.OUTPUT, buffer.mode());

        buffer.setInputMode();
        buffer.buffer().put(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 });
        Assertions.assertEquals(16, buffer.length());
        Assertions.assertEquals(16, buffer.buffer().capacity());
        Assertions.assertEquals(ExpandableBuffer.Mode.OUTPUT, buffer.mode());

        buffer.setInputMode();
        buffer.ensureCapacity(22);
        buffer.buffer().put(new byte[] { 0, 1, 2, 3, 4, 5});
        Assertions.assertEquals(22, buffer.length());
        Assertions.assertEquals(22, buffer.buffer().capacity());
        Assertions.assertEquals(ExpandableBuffer.Mode.OUTPUT, buffer.mode());

        buffer.clear();
        Assertions.assertEquals(ExpandableBuffer.Mode.INPUT, buffer.mode());
        Assertions.assertFalse(buffer.hasData());
        Assertions.assertEquals(22, buffer.capacity());
    }

    @Test
    void testAdjustCapacity() {
        final ExpandableBuffer buffer = new ExpandableBuffer(16);
        Assertions.assertEquals(16, buffer.capacity());

        buffer.ensureCapacity(21);
        Assertions.assertEquals(21, buffer.capacity());
        buffer.ensureAdjustedCapacity(22);
        Assertions.assertEquals(1024, buffer.capacity());
        buffer.ensureAdjustedCapacity(1024);
        Assertions.assertEquals(1024, buffer.capacity());
        buffer.ensureAdjustedCapacity(1025);
        Assertions.assertEquals(2048, buffer.capacity());
    }
}

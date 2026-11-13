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


import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestExpandableBuffer {

    @Test
    void testBasics() {
        final ExpandableBuffer buffer = new ExpandableBuffer(16);
        Assertions.assertThat(buffer.mode()).isEqualTo(ExpandableBuffer.Mode.INPUT);
        Assertions.assertThat(buffer.hasData()).isFalse();

        buffer.setInputMode();
        buffer.buffer().put(new byte[] { 0, 1, 2, 3, 4, 5});
        Assertions.assertThat(buffer.hasData()).isTrue();
        Assertions.assertThat(buffer.length()).isEqualTo(6);
        Assertions.assertThat(buffer.buffer().capacity()).isEqualTo(16);
        Assertions.assertThat(buffer.mode()).isEqualTo(ExpandableBuffer.Mode.OUTPUT);

        buffer.setInputMode();
        buffer.buffer().put(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 });
        Assertions.assertThat(buffer.length()).isEqualTo(16);
        Assertions.assertThat(buffer.buffer().capacity()).isEqualTo(16);
        Assertions.assertThat(buffer.mode()).isEqualTo(ExpandableBuffer.Mode.OUTPUT);

        buffer.setInputMode();
        buffer.ensureCapacity(22);
        buffer.buffer().put(new byte[] { 0, 1, 2, 3, 4, 5});
        Assertions.assertThat(buffer.length()).isEqualTo(22);
        Assertions.assertThat(buffer.buffer().capacity()).isEqualTo(22);
        Assertions.assertThat(buffer.mode()).isEqualTo(ExpandableBuffer.Mode.OUTPUT);

        buffer.clear();
        Assertions.assertThat(buffer.mode()).isEqualTo(ExpandableBuffer.Mode.INPUT);
        Assertions.assertThat(buffer.hasData()).isFalse();
        Assertions.assertThat(buffer.capacity()).isEqualTo(22);
    }

    @Test
    void testAdjustCapacity() {
        final ExpandableBuffer buffer = new ExpandableBuffer(16);
        Assertions.assertThat(buffer.capacity()).isEqualTo(16);

        buffer.ensureCapacity(21);
        Assertions.assertThat(buffer.capacity()).isEqualTo(21);
        buffer.ensureAdjustedCapacity(22);
        Assertions.assertThat(buffer.capacity()).isEqualTo(1024);
        buffer.ensureAdjustedCapacity(1024);
        Assertions.assertThat(buffer.capacity()).isEqualTo(1024);
        buffer.ensureAdjustedCapacity(1025);
        Assertions.assertThat(buffer.capacity()).isEqualTo(2048);
    }
}

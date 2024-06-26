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

import static org.hamcrest.MatcherAssert.assertThat;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

class TestExpandableBuffer {

    @Test
    void testBasics() {
        final ExpandableBuffer buffer = new ExpandableBuffer(16);
        assertThat(buffer.mode(), CoreMatchers.equalTo(ExpandableBuffer.Mode.INPUT));
        assertThat(buffer.hasData(), CoreMatchers.equalTo(false));

        buffer.setInputMode();
        buffer.buffer().put(new byte[] { 0, 1, 2, 3, 4, 5});
        assertThat(buffer.hasData(), CoreMatchers.equalTo(true));
        assertThat(buffer.length(), CoreMatchers.equalTo(6));
        assertThat(buffer.buffer().capacity(), CoreMatchers.equalTo(16));
        assertThat(buffer.mode(), CoreMatchers.equalTo(ExpandableBuffer.Mode.OUTPUT));

        buffer.setInputMode();
        buffer.buffer().put(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 });
        assertThat(buffer.length(), CoreMatchers.equalTo(16));
        assertThat(buffer.buffer().capacity(), CoreMatchers.equalTo(16));
        assertThat(buffer.mode(), CoreMatchers.equalTo(ExpandableBuffer.Mode.OUTPUT));

        buffer.setInputMode();
        buffer.ensureCapacity(22);
        buffer.buffer().put(new byte[] { 0, 1, 2, 3, 4, 5});
        assertThat(buffer.length(), CoreMatchers.equalTo(22));
        assertThat(buffer.buffer().capacity(), CoreMatchers.equalTo(22));
        assertThat(buffer.mode(), CoreMatchers.equalTo(ExpandableBuffer.Mode.OUTPUT));

        buffer.clear();
        assertThat(buffer.mode(), CoreMatchers.equalTo(ExpandableBuffer.Mode.INPUT));
        assertThat(buffer.hasData(), CoreMatchers.equalTo(false));
        assertThat(buffer.capacity(), CoreMatchers.equalTo(22));
    }

    @Test
    void testAdjustCapacity() {
        final ExpandableBuffer buffer = new ExpandableBuffer(16);
        assertThat(buffer.capacity(), CoreMatchers.equalTo(16));

        buffer.ensureCapacity(21);
        assertThat(buffer.capacity(), CoreMatchers.equalTo(21));
        buffer.ensureAdjustedCapacity(22);
        assertThat(buffer.capacity(), CoreMatchers.equalTo(1024));
        buffer.ensureAdjustedCapacity(1024);
        assertThat(buffer.capacity(), CoreMatchers.equalTo(1024));
        buffer.ensureAdjustedCapacity(1025);
        assertThat(buffer.capacity(), CoreMatchers.equalTo(2048));
    }
}

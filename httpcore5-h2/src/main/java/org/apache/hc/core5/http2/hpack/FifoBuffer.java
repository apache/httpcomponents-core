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

import org.apache.hc.core5.http.Header;

final class FifoBuffer {

    private HPackHeader[] array;
    private int head;
    private int tail;

    FifoBuffer(final int initialCapacity) {
        this.array = new HPackHeader[initialCapacity];
        this.head = 0;
        this.tail = 0;
    }

    private void expand() {

        int newcapacity = (array.length + 1) << 1;
        if (newcapacity < 0) {
            newcapacity = Integer.MAX_VALUE;
        }
        final Header[] oldArray = array;
        final int len = oldArray.length;
        final HPackHeader[] newArray = new HPackHeader[newcapacity];
        System.arraycopy(oldArray, head, newArray, 0, len - head);
        System.arraycopy(oldArray, 0, newArray, len - head, head);
        array = newArray;
        head = len;
        tail = 0;
    }

    public void clear() {
        head = 0;
        tail = 0;
    }

    public void addFirst(final HPackHeader header) {
        array[head++] = header;
        if (head == array.length) {
            head = 0;
        }
        if (head == tail) {
            expand();
        }
    }

    public HPackHeader get(final int index) {
        int i = head - index - 1;
        if (i < 0) {
            i = array.length + i;
        }
        return array[i];
    }

    public HPackHeader getFirst() {
        return array[head > 0 ? head - 1 : array.length - 1];
    }

    public HPackHeader getLast() {
        return array[tail];
    }

    public HPackHeader removeLast() {
        final HPackHeader header = array[tail];
        if (header != null) {
            array[tail++] = null;
            if (tail == array.length) {
                tail = 0;
            }
        }
        return header;
    }

    public int capacity() {
        return array.length;
    }

    public int size() {
        int i = head - tail;
        if (i < 0) {
            i = array.length + i;
        }
        return i;
    }

}

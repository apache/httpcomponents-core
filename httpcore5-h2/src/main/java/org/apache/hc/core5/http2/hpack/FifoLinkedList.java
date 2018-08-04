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

final class FifoLinkedList {

    private final InternalNode master;
    private int length;

    FifoLinkedList() {
        this.master = new InternalNode(null);
        this.master.previous = this.master;
        this.master.next = this.master;
    }

    public Header get(final int index) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException();
        }
        InternalNode current = master.next;
        int n = 0;
        while (current != master) {
            if (index == n) {
                return current.header;
            }
            current = current.next;
            n++;
        }
        return null;
    }

    public int getIndex(final InternalNode node) {
        final int seqNum = node.seqNum;
        if (seqNum < 1) {
            return -1;
        }
        return length - (seqNum - master.previous.seqNum) - 1;
    }

    public Header getFirst() {
        return master.next.header;
    }

    public Header getLast() {
        return master.previous.header;
    }

    public int size() {
        return length;
    }

    public InternalNode addFirst(final HPackHeader header) {

        final InternalNode newNode = new InternalNode(header);
        final InternalNode oldNode = master.next;
        master.next = newNode;
        newNode.previous = master;
        newNode.next = oldNode;
        oldNode.previous = newNode;
        newNode.seqNum = oldNode.seqNum + 1;
        length++;
        return newNode;
    }

    public InternalNode removeLast() {

        final InternalNode last = master.previous;
        if (last.header != null) {
            final InternalNode lastButOne = last.previous;
            master.previous = lastButOne;
            lastButOne.next = master;
            last.previous = null;
            last.next = null;
            last.seqNum = 0;
            length--;
            return last;
        }
        master.seqNum = 0;
        return null;
    }

    public void clear() {

        master.previous = master;
        master.next = master;
        master.seqNum = 0;
        length = 0;
    }

    class InternalNode implements HPackEntry {

        private final HPackHeader header;
        private InternalNode previous;
        private InternalNode next;
        private int seqNum;

        InternalNode(final HPackHeader header) {
            this.header = header;
        }

        @Override
        public HPackHeader getHeader() {
            return header;
        }

        @Override
        public int getIndex() {
            return StaticTable.INSTANCE.length() + FifoLinkedList.this.getIndex(this) + 1;
        }

        @Override
        public String toString() {
            return "[" +
                    (header != null ? header.toString() : "master") +
                    "; seqNum=" + seqNum +
                    "; previous=" + (previous != null ? previous.header : null) +
                    "; next=" + (next != null ? next.header : null) +
                    ']';
        }

    }

}

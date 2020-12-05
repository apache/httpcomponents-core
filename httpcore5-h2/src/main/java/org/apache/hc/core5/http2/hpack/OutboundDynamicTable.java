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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.util.Asserts;

final class OutboundDynamicTable {

    private final StaticTable staticTable;
    private final FifoLinkedList headers;
    private final Map<String, LinkedList<HPackEntry>> mapByName;

    private int maxSize;
    private int currentSize;

    OutboundDynamicTable(final StaticTable staticTable) {
        this.staticTable = staticTable;
        this.headers = new FifoLinkedList();
        this.mapByName = new HashMap<>();
        this.maxSize = Integer.MAX_VALUE;
        this.currentSize = 0;
    }

    OutboundDynamicTable() {
        this(StaticTable.INSTANCE);
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(final int maxSize) {
        this.maxSize = maxSize;
        evict();
    }

    public int getCurrentSize() {
        return currentSize;
    }

    int staticLength() {
        return staticTable.length();
    }

    int dynamicLength() {
        return headers.size();
    }

    Header getDynamicEntry(final int index) {
        return headers.get(index);
    }

    public int length() {
        return staticTable.length() + headers.size();
    }

    public Header getHeader(final int index) {
        if (index < 1 || index > length()) {
            throw new IndexOutOfBoundsException();
        }
        return index <= staticTable.length()
                        ? staticTable.get(index)
                        : headers.get(index - staticTable.length() - 1);
    }

    public void add(final HPackHeader header) {
        final int entrySize = header.getTotalSize();
        if (entrySize > this.maxSize) {
            clear();
            this.mapByName.clear();
            return;
        }
        final String key = header.getName();
        final FifoLinkedList.InternalNode node = headers.addFirst(header);
        final LinkedList<HPackEntry> nodes = mapByName.computeIfAbsent(key, k -> new LinkedList<>());
        nodes.addFirst(node);
        currentSize += entrySize;
        evict();
    }

    private void clear() {
        currentSize = 0;
        headers.clear();
    }

    public List<HPackEntry> getByName(final String key) {
        return this.mapByName.get(key);
    }

    private void evict() {
        while (currentSize > maxSize) {
            final FifoLinkedList.InternalNode node = headers.removeLast();
            if (node != null) {
                final HPackHeader header = node.getHeader();
                currentSize -= header.getTotalSize();

                final String key = header.getName();
                final LinkedList<HPackEntry> nodes = mapByName.get(key);
                if (nodes != null) {
                    nodes.remove(node);
                }
            } else {
                Asserts.check(currentSize == 0, "Current table size must be zero");
                break;
            }
        }
    }

}

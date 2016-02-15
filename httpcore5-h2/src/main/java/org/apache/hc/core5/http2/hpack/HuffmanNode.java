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

import java.util.Arrays;

import org.apache.hc.core5.util.Asserts;

/**
 * This Huffman codec implementation has been derived from Twitter HPack project
 * (https://github.com/twitter/hpack)
 */
final class HuffmanNode {

    private final int symbol;
    private final int bits;
    private final HuffmanNode[] children;

    HuffmanNode() {
        this.symbol = 0;
        this.bits = 8;
        this.children = new HuffmanNode[256];
    }

    HuffmanNode(final int symbol, final int bits) {
        this.symbol = symbol;
        this.bits = bits;
        this.children = null;
    }

    public int getBits() {
        return this.bits;
    }

    public int getSymbol() {
        return this.symbol;
    }

    public boolean hasChild(final int index) {
        return this.children != null && this.children[index] != null;
    }

    public HuffmanNode getChild(final int index) {
        return this.children != null ? this.children[index] : null;
    }

    void setChild(final int index, final HuffmanNode child) {
        Asserts.notNull(this.children, "Children nodes");
        this.children[index] = child;
    }

    public boolean isTerminal() {
        return this.children == null;
    }

    @Override
    public String toString() {
        return "[" +
                "symbol=" + symbol +
                ", bits=" + bits +
                ", children=" + Arrays.toString(children) +
                ']';
    }

}
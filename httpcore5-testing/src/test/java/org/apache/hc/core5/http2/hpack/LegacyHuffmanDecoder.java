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

import java.nio.ByteBuffer;

import org.apache.hc.core5.util.ByteArrayBuffer;

/**
 * Huffman decoder implementation used before the DFA optimization.
 */
final class LegacyHuffmanDecoder {

    private final Node root;

    LegacyHuffmanDecoder(final int[] codes, final byte[] lengths) {
        this.root = buildTree(codes, lengths);
    }

    void decode(final ByteArrayBuffer out, final ByteBuffer src) throws HPackException {
        Node node = this.root;
        int current = 0;
        int bits = 0;
        while (src.hasRemaining()) {
            final int b = src.get() & 0xff;
            current = (current << 8) | b;
            bits += 8;
            while (bits >= 8) {
                final int c = (current >>> (bits - 8)) & 0xff;
                node = node.getChild(c);
                bits -= node.getBits();
                if (node.isTerminal()) {
                    if (node.getSymbol() == Huffman.EOS) {
                        throw new HPackException("EOS decoded");
                    }
                    out.append(node.getSymbol());
                    node = root;
                }
            }
        }
        while (bits > 0) {
            final int c = (current << (8 - bits)) & 0xff;
            node = node.getChild(c);
            if (node.isTerminal() && node.getBits() <= bits) {
                bits -= node.getBits();
                out.append(node.getSymbol());
                node = this.root;
            } else {
                break;
            }
        }
        final int mask = (1 << bits) - 1;
        if ((current & mask) != mask) {
            throw new HPackException("Invalid padding");
        }
    }

    private static Node buildTree(final int[] codes, final byte[] lengths) {
        final Node root = new Node();
        for (int symbol = 0; symbol < codes.length; symbol++) {
            final int code = codes[symbol];
            int length = lengths[symbol];
            Node current = root;
            while (length > 8) {
                if (current.isTerminal()) {
                    throw new IllegalStateException("Invalid Huffman code: prefix not unique");
                }
                length -= 8;
                final int i = (code >>> length) & 0xff;
                if (!current.hasChild(i)) {
                    current.setChild(i, new Node());
                }
                current = current.getChild(i);
            }
            final Node terminal = new Node(symbol, length);
            final int shift = 8 - length;
            final int start = (code << shift) & 0xff;
            final int end = 1 << shift;
            for (int i = start; i < start + end; i++) {
                current.setChild(i, terminal);
            }
        }
        return root;
    }

    private static final class Node {

        private final int symbol;
        private final int bits;
        private final Node[] children;

        Node() {
            this.symbol = 0;
            this.bits = 8;
            this.children = new Node[256];
        }

        Node(final int symbol, final int bits) {
            this.symbol = symbol;
            this.bits = bits;
            this.children = null;
        }

        int getBits() {
            return bits;
        }

        int getSymbol() {
            return symbol;
        }

        boolean hasChild(final int index) {
            return children != null && children[index] != null;
        }

        Node getChild(final int index) {
            return children != null ? children[index] : null;
        }

        void setChild(final int index, final Node child) {
            if (children == null) {
                throw new IllegalStateException("Children nodes must not be null");
            }
            children[index] = child;
        }

        boolean isTerminal() {
            return children == null;
        }

    }

}

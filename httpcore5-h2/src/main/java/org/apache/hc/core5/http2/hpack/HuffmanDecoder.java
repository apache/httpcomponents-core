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
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.util.ByteArrayBuffer;

/**
 * This Huffman codec implementation has been derived from Twitter HPack project
 * (https://github.com/twitter/hpack)
 */
final class HuffmanDecoder {

    private static final int BYTE_VALUES = 256;
    private static final int MISSING = -1;

    private static final int STATE_BITS = 9;
    private static final int STATE_MASK = (1 << STATE_BITS) - 1;
    private static final int FIRST_SYMBOL_SHIFT = STATE_BITS;
    private static final int SECOND_SYMBOL_SHIFT = FIRST_SYMBOL_SHIFT + 8;
    private static final int SYMBOL_COUNT_SHIFT = SECOND_SYMBOL_SHIFT + 8;
    private static final int SYMBOL_COUNT_MASK = 0x3;
    private static final int INVALID_FLAG = 0x40000000;
    private static final int EOS_FLAG = 0x80000000;
    private static final int ERROR_MASK = INVALID_FLAG | EOS_FLAG;

    private final int[] transitions;
    private final boolean[] endStates;

    HuffmanDecoder(final int[] codes, final byte[] lengths) {
        if (codes.length != lengths.length) {
            throw new IllegalArgumentException("Mismatched Huffman code table");
        }
        final List<Node> nodes = buildTree(codes, lengths);
        if (nodes.size() > STATE_MASK + 1) {
            throw new IllegalStateException("Huffman decode table too large");
        }
        this.transitions = buildTransitions(nodes);
        this.endStates = buildEndStates(nodes);
    }

    void decode(final ByteArrayBuffer out, final ByteBuffer src) throws HPackException {
        int state = 0;
        byte[] output = out.array();
        int outputPos = out.length();

        while (src.hasRemaining()) {
            final int transition = transitions[(state << 8) | (src.get() & 0xFF)];
            final int symbolCount = (transition >>> SYMBOL_COUNT_SHIFT) & SYMBOL_COUNT_MASK;

            if (symbolCount != 0) {
                if (outputPos + symbolCount > output.length) {
                    out.setLength(outputPos);
                    out.ensureCapacity(symbolCount);
                    output = out.array();
                    outputPos = out.length();
                }
                output[outputPos++] = (byte) (transition >>> FIRST_SYMBOL_SHIFT);
                if (symbolCount == 2) {
                    output[outputPos++] = (byte) (transition >>> SECOND_SYMBOL_SHIFT);
                }
            }

            final int error = transition & ERROR_MASK;
            if (error != 0) {
                out.setLength(outputPos);
                if ((error & EOS_FLAG) != 0) {
                    throw new HPackException("EOS decoded");
                }
                throw new HPackException("Invalid Huffman code");
            }
            state = transition & STATE_MASK;
        }

        out.setLength(outputPos);
        if (!endStates[state]) {
            throw new HPackException("Invalid padding");
        }
    }

    private static List<Node> buildTree(final int[] codes, final byte[] lengths) {
        final List<Node> nodes = new ArrayList<>(BYTE_VALUES);
        nodes.add(new Node(0, 0));

        for (int symbol = 0; symbol < codes.length; symbol++) {
            final int code = codes[symbol];
            final int length = lengths[symbol];
            int state = 0;

            for (int bitIndex = length - 1; bitIndex >= 0; bitIndex--) {
                final int bit = (code >>> bitIndex) & 1;
                final Node node = nodes.get(state);
                final int child = node.get(bit);

                if (bitIndex == 0) {
                    if (child != MISSING) {
                        throw new IllegalStateException("Invalid Huffman code: prefix not unique");
                    }
                    node.set(bit, leaf(symbol));
                } else {
                    if (isLeaf(child)) {
                        throw new IllegalStateException("Invalid Huffman code: prefix not unique");
                    }
                    if (child == MISSING) {
                        final int next = nodes.size();
                        nodes.add(new Node(node.depth + 1, (node.path << 1) | bit));
                        node.set(bit, next);
                        state = next;
                    } else {
                        state = child;
                    }
                }
            }
        }
        return nodes;
    }

    private static int[] buildTransitions(final List<Node> nodes) {
        final int[] table = new int[nodes.size() * BYTE_VALUES];

        for (int initialState = 0; initialState < nodes.size(); initialState++) {
            for (int value = 0; value < BYTE_VALUES; value++) {
                int state = initialState;
                int firstSymbol = 0;
                int secondSymbol = 0;
                int symbolCount = 0;
                int error = 0;

                for (int bitIndex = 7; bitIndex >= 0; bitIndex--) {
                    final int bit = (value >>> bitIndex) & 1;
                    final int child = nodes.get(state).get(bit);
                    if (child == MISSING) {
                        error = INVALID_FLAG;
                        break;
                    }
                    if (isLeaf(child)) {
                        final int symbol = symbol(child);
                        if (symbol == Huffman.EOS) {
                            error = EOS_FLAG;
                            break;
                        }
                        if (symbolCount == 0) {
                            firstSymbol = symbol;
                        } else if (symbolCount == 1) {
                            secondSymbol = symbol;
                        } else {
                            throw new IllegalStateException("More than two symbols decoded from one input byte");
                        }
                        symbolCount++;
                        state = 0;
                    } else {
                        state = child;
                    }
                }

                table[(initialState << 8) | value] = state
                        | (firstSymbol << FIRST_SYMBOL_SHIFT)
                        | (secondSymbol << SECOND_SYMBOL_SHIFT)
                        | (symbolCount << SYMBOL_COUNT_SHIFT)
                        | error;
            }
        }
        return table;
    }

    private static boolean[] buildEndStates(final List<Node> nodes) {
        final boolean[] endStates = new boolean[nodes.size()];
        for (int state = 0; state < nodes.size(); state++) {
            final Node node = nodes.get(state);
            final int residualBits = node.depth & 7;
            if (residualBits == 0) {
                endStates[state] = true;
            } else {
                final int mask = (1 << residualBits) - 1;
                endStates[state] = (node.path & mask) == mask;
            }
        }
        return endStates;
    }

    private static int leaf(final int symbol) {
        return -symbol - 2;
    }

    private static boolean isLeaf(final int value) {
        return value < MISSING;
    }

    private static int symbol(final int leaf) {
        return -leaf - 2;
    }

    private static final class Node {

        private final int depth;
        private final int path;
        private int zero = MISSING;
        private int one = MISSING;

        Node(final int depth, final int path) {
            this.depth = depth;
            this.path = path;
        }

        int get(final int bit) {
            return bit == 0 ? zero : one;
        }

        void set(final int bit, final int value) {
            if (bit == 0) {
                zero = value;
            } else {
                one = value;
            }
        }
    }

}

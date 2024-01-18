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

package org.apache.hc.core5.util;

import java.util.BitSet;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * Tokenizer that can be used as a foundation for more complex parsing routines.
 * Methods of this class are designed to produce near zero intermediate garbage
 * and make no intermediate copies of input data.
 * <p>
 * This class is immutable and thread safe.
 *
 * @since 5.1
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class Tokenizer {

    public static class Cursor {

        private final int lowerBound;
        private final int upperBound;
        private int pos;

        public Cursor(final int lowerBound, final int upperBound) {
            super();
            Args.notNegative(lowerBound, "lowerBound");
            Args.check(lowerBound <= upperBound, "lowerBound cannot be greater than upperBound");
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
            this.pos = lowerBound;
        }

        public int getLowerBound() {
            return this.lowerBound;
        }

        public int getUpperBound() {
            return this.upperBound;
        }

        public int getPos() {
            return this.pos;
        }

        public void updatePos(final int pos) {
            Args.check(pos >= this.lowerBound, "pos: %s < lowerBound: %s", pos, this.lowerBound);
            Args.check(pos <= this.upperBound, "pos: %s > upperBound: %s", pos, this.upperBound);
            this.pos = pos;
        }

        public boolean atEnd() {
            return this.pos >= this.upperBound;
        }

        @Override
        public String toString() {
            final StringBuilder buffer = new StringBuilder();
            buffer.append('[');
            buffer.append(this.lowerBound);
            buffer.append('>');
            buffer.append(this.pos);
            buffer.append('>');
            buffer.append(this.upperBound);
            buffer.append(']');
            return buffer.toString();
        }

    }

    /**
     * @deprecated Do not use.
     */
    @Deprecated
    public static BitSet INIT_BITSET(final int ... b) {
        final BitSet bitset = new BitSet();
        for (final int aB : b) {
            bitset.set(aB);
        }
        return bitset;
    }

    /** Double quote */
    public static final char DQUOTE = '\"';

    /** Backward slash / escape character */
    public static final char ESCAPE = '\\';

    public static final int CR = 13; // <US-ASCII CR, carriage return (13)>
    public static final int LF = 10; // <US-ASCII LF, linefeed (10)>
    public static final int SP = 32; // <US-ASCII SP, space (32)>
    public static final int HT = 9;  // <US-ASCII HT, horizontal-tab (9)>

    public static boolean isWhitespace(final char ch) {
        return ch == SP || ch == HT || ch == CR || ch == LF;
    }

    /**
     * Represents a predicate whether the given character is a delimiter.
     *
     * @since 5.3
     */
    @FunctionalInterface
    public interface Delimiter {

        boolean test(char ch);

    }

    /**
     * @since 5.3
     */
    public static Delimiter delimiters(final BitSet delimiters) {
        return delimiters::get;
    }

    /**
     * @since 5.3
     */
    public static Delimiter delimiters(final char... delimiters) {
        return ch -> {
            for (final char delimiter : delimiters) {
                if (delimiter == ch) {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * @since 5.3
     */
    public static Delimiter delimiters(final char delimiter) {
        return ch -> ch == delimiter;
    }

    /**
     * @since 5.3
     */
    public static Delimiter delimiters(final char delimiter1, final char delimiter2) {
        return ch -> ch == delimiter1 || ch == delimiter2;
    }

    /**
     * @since 5.3
     */
    public static Delimiter delimiters(final char delimiter1, final char delimiter2, final char delimiter3) {
        return ch -> ch == delimiter1 || ch == delimiter2 || ch == delimiter3;
    }

    public static final Tokenizer INSTANCE = new Tokenizer();

    /**
     * Extracts from the sequence of chars a token terminated with any of the given delimiters
     * or a whitespace characters.
     *
     * @param buf buffer with the sequence of chars to be parsed
     * @param cursor defines the bounds and current position of the buffer
     * @param delimiterPredicate delimiter predicate. Can be {@code null} if the token
     *  is not delimited by any character.
     */
    public String parseContent(final CharSequence buf, final Cursor cursor, final Delimiter delimiterPredicate) {
        Args.notNull(buf, "Char sequence");
        Args.notNull(cursor, "Parser cursor");
        final StringBuilder dst = new StringBuilder();
        copyContent(buf, cursor, delimiterPredicate, dst);
        return dst.toString();
    }

    /**
     * @deprecated use {@link #parseContent(CharSequence, Cursor, Delimiter)}
     */
    @Deprecated
    public String parseContent(final CharSequence buf, final Cursor cursor, final BitSet bitSet) {
        return parseContent(buf, cursor, bitSet != null ? bitSet::get : null);
    }

    /**
     * Extracts from the sequence of chars a token terminated with any of the given delimiters
     * discarding semantically insignificant whitespace characters.
     *
     * @param buf buffer with the sequence of chars to be parsed
     * @param cursor defines the bounds and current position of the buffer
     * @param delimiterPredicate delimiter predicate. Can be {@code null} if the token
     *  is not delimited by any character.
     */
    public String parseToken(final CharSequence buf, final Cursor cursor, final Delimiter delimiterPredicate) {
        Args.notNull(buf, "Char sequence");
        Args.notNull(cursor, "Parser cursor");
        final StringBuilder dst = new StringBuilder();
        boolean whitespace = false;
        while (!cursor.atEnd()) {
            final char current = buf.charAt(cursor.getPos());
            if (delimiterPredicate != null && delimiterPredicate.test(current)) {
                break;
            } else if (isWhitespace(current)) {
                skipWhiteSpace(buf, cursor);
                whitespace = true;
            } else {
                if (whitespace && dst.length() > 0) {
                    dst.append(' ');
                }
                copyContent(buf, cursor, delimiterPredicate, dst);
                whitespace = false;
            }
        }
        return dst.toString();
    }

    /**
     * @deprecated use {@link #parseToken(CharSequence, Cursor, Delimiter)}
     */
    @Deprecated
    public String parseToken(final CharSequence buf, final Cursor cursor, final BitSet bitSet) {
        return parseToken(buf, cursor, bitSet != null ? bitSet::get : null);
    }

    /**
     * Extracts from the sequence of chars a value which can be enclosed in quote marks and
     * terminated with any of the given delimiters discarding semantically insignificant
     * whitespace characters.
     *
     * @param buf buffer with the sequence of chars to be parsed
     * @param cursor defines the bounds and current position of the buffer
     * @param delimiterPredicate delimiter predicate. Can be {@code null} if the token
     *  is not delimited by any character.
     */
    public String parseValue(final CharSequence buf, final Cursor cursor, final Delimiter delimiterPredicate) {
        Args.notNull(buf, "Char sequence");
        Args.notNull(cursor, "Parser cursor");
        final StringBuilder dst = new StringBuilder();
        boolean whitespace = false;
        while (!cursor.atEnd()) {
            final char current = buf.charAt(cursor.getPos());
            if (delimiterPredicate != null && delimiterPredicate.test(current)) {
                break;
            } else if (isWhitespace(current)) {
                skipWhiteSpace(buf, cursor);
                whitespace = true;
            } else if (current == DQUOTE) {
                if (whitespace && dst.length() > 0) {
                    dst.append(' ');
                }
                copyQuotedContent(buf, cursor, dst);
                whitespace = false;
            } else {
                if (whitespace && dst.length() > 0) {
                    dst.append(' ');
                }
                copyUnquotedContent(buf, cursor, delimiterPredicate, dst);
                whitespace = false;
            }
        }
        return dst.toString();
    }

    /**
     * @deprecated use {@link #parseValue(CharSequence, Cursor, Delimiter)}
     */
    @Deprecated
    public String parseValue(final CharSequence buf, final Cursor cursor, final BitSet bitSet) {
        return parseValue(buf, cursor, bitSet != null ? bitSet::get : null);
    }

    /**
     * Skips semantically insignificant whitespace characters and moves the cursor to the closest
     * non-whitespace character.
     *
     * @param buf buffer with the sequence of chars to be parsed
     * @param cursor defines the bounds and current position of the buffer
     */
    public void skipWhiteSpace(final CharSequence buf, final Cursor cursor) {
        Args.notNull(buf, "Char sequence");
        Args.notNull(cursor, "Parser cursor");
        int pos = cursor.getPos();
        final int indexFrom = cursor.getPos();
        final int indexTo = cursor.getUpperBound();
        for (int i = indexFrom; i < indexTo; i++) {
            final char current = buf.charAt(i);
            if (!isWhitespace(current)) {
                break;
            }
            pos++;
        }
        cursor.updatePos(pos);
    }

    /**
     * Transfers content into the destination buffer until a whitespace character or any of
     * the given delimiters is encountered.
     *
     * @param buf buffer with the sequence of chars to be parsed
     * @param cursor defines the bounds and current position of the buffer
     * @param delimiterPredicate delimiter predicate. Can be {@code null} if the token
     *  is delimited by a whitespace only.
     * @param dst destination buffer
     */
    public void copyContent(final CharSequence buf, final Cursor cursor, final Delimiter delimiterPredicate,
                            final StringBuilder dst) {
        Args.notNull(buf, "Char sequence");
        Args.notNull(cursor, "Parser cursor");
        Args.notNull(dst, "String builder");
        int pos = cursor.getPos();
        final int indexFrom = cursor.getPos();
        final int indexTo = cursor.getUpperBound();
        for (int i = indexFrom; i < indexTo; i++) {
            final char current = buf.charAt(i);
            if ((delimiterPredicate != null && delimiterPredicate.test(current)) || isWhitespace(current)) {
                break;
            }
            pos++;
            dst.append(current);
        }
        cursor.updatePos(pos);
    }

    /**
     * @deprecated Use {@link #copyContent(CharSequence, Cursor, Delimiter, StringBuilder)}
     */
    @Deprecated
    public void copyContent(final CharSequence buf, final Cursor cursor, final BitSet bitSet,
                            final StringBuilder dst) {
        copyContent(buf, cursor, bitSet != null ? bitSet::get : null, dst);
    }

    /**
     * Transfers content into the destination buffer until a whitespace character,  a quote,
     * or any of the given delimiters is encountered.
     *
     * @param buf buffer with the sequence of chars to be parsed
     * @param cursor defines the bounds and current position of the buffer
     * @param delimiterPredicate delimiter predicate. Can be {@code null} if the token
     *  is delimited by a whitespace or a quote only.
     * @param dst destination buffer
     */
    public void copyUnquotedContent(final CharSequence buf, final Cursor cursor,
            final Delimiter delimiterPredicate, final StringBuilder dst) {
        Args.notNull(buf, "Char sequence");
        Args.notNull(cursor, "Parser cursor");
        Args.notNull(dst, "String builder");
        int pos = cursor.getPos();
        final int indexFrom = cursor.getPos();
        final int indexTo = cursor.getUpperBound();
        for (int i = indexFrom; i < indexTo; i++) {
            final char current = buf.charAt(i);
            if ((delimiterPredicate != null && delimiterPredicate.test(current))
                    || isWhitespace(current) || current == DQUOTE) {
                break;
            }
            pos++;
            dst.append(current);
        }
        cursor.updatePos(pos);
    }

    /**
     * @deprecated Use {@link #copyUnquotedContent(CharSequence, Cursor, Delimiter, StringBuilder)}
     */
    @Deprecated
    public void copyUnquotedContent(final CharSequence buf, final Cursor cursor,
                                    final BitSet bitSet, final StringBuilder dst) {
        copyUnquotedContent(buf, cursor, bitSet != null ? bitSet::get : null, dst);
    }

    /**
     * Transfers content enclosed with quote marks into the destination buffer.
     *
     * @param buf buffer with the sequence of chars to be parsed
     * @param cursor defines the bounds and current position of the buffer
     * @param dst destination buffer
     */
    public void copyQuotedContent(final CharSequence buf, final Cursor cursor,
            final StringBuilder dst) {
        Args.notNull(buf, "Char sequence");
        Args.notNull(cursor, "Parser cursor");
        Args.notNull(dst, "String builder");
        if (cursor.atEnd()) {
            return;
        }
        int pos = cursor.getPos();
        int indexFrom = cursor.getPos();
        final int indexTo = cursor.getUpperBound();
        char current = buf.charAt(pos);
        if (current != DQUOTE) {
            return;
        }
        pos++;
        indexFrom++;
        boolean escaped = false;
        for (int i = indexFrom; i < indexTo; i++, pos++) {
            current = buf.charAt(i);
            if (escaped) {
                if (current != DQUOTE && current != ESCAPE) {
                    dst.append(ESCAPE);
                }
                dst.append(current);
                escaped = false;
            } else {
                if (current == DQUOTE) {
                    pos++;
                    break;
                }
                if (current == ESCAPE) {
                    escaped = true;
                } else if (current != CR && current != LF) {
                    dst.append(current);
                }
            }
        }
        cursor.updatePos(pos);
    }

}

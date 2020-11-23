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

package org.apache.hc.core5.http.message;

import java.util.BitSet;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.util.Tokenizer;

/**
 * Low level parser for header field elements. The parsing routines of this class are designed
 * to produce near zero intermediate garbage and make no intermediate copies of input data.
 * <p>
 * This class is immutable and thread safe.
 *
 * @since 4.4
 *
 * @deprecated Use {@link Tokenizer}
 */
@Deprecated
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class TokenParser extends Tokenizer {

    public static final TokenParser INSTANCE = new TokenParser();

    /** Double quote */
    public static final char DQUOTE = '\"';

    /** Backward slash / escape character */
    public static final char ESCAPE = '\\';

    public String parseToken(final CharSequence buf, final ParserCursor cursor, final BitSet delimiters) {
        return super.parseToken(buf, cursor, delimiters);
    }

    public String parseValue(final CharSequence buf, final ParserCursor cursor, final BitSet delimiters) {
        return super.parseValue(buf, cursor, delimiters);
    }

    public void skipWhiteSpace(final CharSequence buf, final ParserCursor cursor) {
        super.skipWhiteSpace(buf, cursor);
    }

    public void copyContent(final CharSequence buf, final ParserCursor cursor, final BitSet delimiters,
                            final StringBuilder dst) {
        super.copyContent(buf, cursor, delimiters, dst);
    }

    @Override
    public void copyContent(final CharSequence buf, final Tokenizer.Cursor cursor, final BitSet delimiters,
                            final StringBuilder dst) {
        final ParserCursor parserCursor = new ParserCursor(cursor.getLowerBound(), cursor.getUpperBound());
        parserCursor.updatePos(cursor.getPos());
        copyContent(buf, parserCursor, delimiters, dst);
        cursor.updatePos(parserCursor.getPos());
    }

    public void copyUnquotedContent(final CharSequence buf, final ParserCursor cursor, final BitSet delimiters,
                                    final StringBuilder dst) {
        super.copyUnquotedContent(buf, cursor, delimiters, dst);
    }

    @Override
    public void copyUnquotedContent(final CharSequence buf, final Tokenizer.Cursor cursor, final BitSet delimiters,
                                    final StringBuilder dst) {
        final ParserCursor parserCursor = new ParserCursor(cursor.getLowerBound(), cursor.getUpperBound());
        parserCursor.updatePos(cursor.getPos());
        copyUnquotedContent(buf, parserCursor, delimiters, dst);
        cursor.updatePos(parserCursor.getPos());
    }

    public void copyQuotedContent(final CharSequence buf, final ParserCursor cursor, final StringBuilder dst) {
        super.copyQuotedContent(buf, cursor, dst);
    }

    @Override
    public void copyQuotedContent(final CharSequence buf, final Tokenizer.Cursor cursor, final StringBuilder dst) {
        final ParserCursor parserCursor = new ParserCursor(cursor.getLowerBound(), cursor.getUpperBound());
        parserCursor.updatePos(cursor.getPos());
        copyQuotedContent(buf, parserCursor, dst);
        cursor.updatePos(parserCursor.getPos());
    }

}

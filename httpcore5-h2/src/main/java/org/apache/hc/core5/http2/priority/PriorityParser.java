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
package org.apache.hc.core5.http2.priority;

import java.util.Locale;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.util.Tokenizer;

@Internal
public final class PriorityParser {

    private static final Tokenizer TK = Tokenizer.INSTANCE;

    // Non-deprecated delimiter predicates
    private static final Tokenizer.Delimiter KEY_DELIMS = Tokenizer.delimiters('=', ',', ';');
    private static final Tokenizer.Delimiter VALUE_DELIMS = Tokenizer.delimiters(',', ';');

    private PriorityParser() {
    }

    public static PriorityValue parse(final Header header) {
        if (header == null) {
            return PriorityValue.defaults();
        }
        final PriorityValue[] out = new PriorityValue[1];
        MessageSupport.parseHeader(header, (seq, cur) -> out[0] = parse(seq, cur));
        return out[0] != null ? out[0] : PriorityValue.defaults();
    }

    public static PriorityValue parse(final String headerValue) {
        if (headerValue == null || headerValue.isEmpty()) {
            return PriorityValue.defaults();
        }
        final ParserCursor c = new ParserCursor(0, headerValue.length());
        return parse(headerValue, c);
    }

    public static PriorityValue parse(final CharSequence src, final ParserCursor cursor) {
        int urgency = PriorityValue.DEFAULT_URGENCY;
        boolean incremental = PriorityValue.DEFAULT_INCREMENTAL;

        while (!cursor.atEnd()) {
            TK.skipWhiteSpace(src, cursor);
            if (cursor.atEnd()) {
                break;
            }

            final String rawKey = TK.parseToken(src, cursor, KEY_DELIMS);
            if (rawKey == null || rawKey.isEmpty()) {
                skipToNextItem(src, cursor);
                continue;
            }
            final String key = rawKey.toLowerCase(Locale.ROOT);

            TK.skipWhiteSpace(src, cursor);
            final char ch = currentChar(src, cursor);

            if (ch == '=') {
                cursor.updatePos(cursor.getPos() + 1);
                TK.skipWhiteSpace(src, cursor);

                if ("u".equals(key)) {
                    final String numTok = TK.parseToken(src, cursor, VALUE_DELIMS);
                    final Integer u = safeParseInt(numTok);
                    if (u != null && u >= 0 && u <= 7) {
                        urgency = u;
                    }
                } else if ("i".equals(key)) {
                    final char b = currentChar(src, cursor);
                    if (b == '?') {
                        cursor.updatePos(cursor.getPos() + 1);
                        final char v = currentChar(src, cursor);
                        if (v == '1') {
                            incremental = true;
                            cursor.updatePos(cursor.getPos() + 1);
                        } else if (v == '0') {
                            incremental = false;
                            cursor.updatePos(cursor.getPos() + 1);
                        }
                    } else {
                        final String tok = TK.parseToken(src, cursor, VALUE_DELIMS);
                        if ("1".equals(tok)) {
                            incremental = true;
                        } else if ("0".equals(tok)) {
                            incremental = false;
                        }
                    }
                } else {
                    TK.parseToken(src, cursor, VALUE_DELIMS); // ignore unknown member
                }
                skipParamsThenNextItem(src, cursor);

            } else {
                if ("i".equals(key)) {
                    incremental = true; // bare true
                }
                skipParamsThenNextItem(src, cursor);
            }
        }
        return PriorityValue.of(urgency, incremental);
    }

    private static char currentChar(final CharSequence buf, final Tokenizer.Cursor c) {
        return c.atEnd() ? 0 : buf.charAt(c.getPos());
    }

    private static Integer safeParseInt(final String s) {
        if (s == null) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (final NumberFormatException ignore) {
            return null;
        }
    }

    private static void skipToNextItem(final CharSequence buf, final Tokenizer.Cursor c) {
        while (!c.atEnd()) {
            final char ch = buf.charAt(c.getPos());
            c.updatePos(c.getPos() + 1);
            if (ch == ',') {
                break;
            }
        }
    }

    // Skip any SF parameters (';param[=value]...'), then advance to the next item (after a single ',') if present.
    private static void skipParamsThenNextItem(final CharSequence buf, final Tokenizer.Cursor c) {
        while (!c.atEnd()) {
            final int pos = c.getPos();
            final char ch = buf.charAt(pos);
            if (ch == ';') {
                c.updatePos(pos + 1);
                // consume parameter token (up to ',' or ';')
                TK.parseToken(buf, c, VALUE_DELIMS);
                continue;
            }
            break;
        }
        while (!c.atEnd()) {
            final char ch = buf.charAt(c.getPos());
            if (ch == ',') {
                c.updatePos(c.getPos() + 1); // consume comma
                break;
            }
            c.updatePos(c.getPos() + 1);
        }
    }
}

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
package org.apache.hc.core5.http.priority;

import java.util.Locale;

import org.apache.hc.core5.util.CharArrayBuffer;
import org.apache.hc.core5.util.Tokenizer;

public final class PriorityParser {

    private static final Tokenizer TK = Tokenizer.INSTANCE;

    // Non-deprecated delimiter predicates
    private static final Tokenizer.Delimiter KEY_DELIMS = Tokenizer.delimiters('=', ',', ';');
    private static final Tokenizer.Delimiter VALUE_DELIMS = Tokenizer.delimiters(',', ';');

    private PriorityParser() {
    }

    public static PriorityValue parse(final String headerValue) {
        int urgency = PriorityValue.DEFAULT_URGENCY;
        boolean incremental = PriorityValue.DEFAULT_INCREMENTAL;

        if (headerValue == null || headerValue.isEmpty()) {
            return PriorityValue.of(urgency, incremental);
        }

        final CharArrayBuffer buf = new CharArrayBuffer(headerValue.length());
        buf.append(headerValue);
        final Tokenizer.Cursor cursor = new Tokenizer.Cursor(0, buf.length());

        while (!cursor.atEnd()) {
            TK.skipWhiteSpace(buf, cursor);
            if (cursor.atEnd()) {
                break;
            }

            final String rawKey = TK.parseToken(buf, cursor, KEY_DELIMS);
            if (rawKey == null || rawKey.isEmpty()) {
                skipToNextItem(buf, cursor);
                continue;
            }
            final String key = rawKey.toLowerCase(Locale.ROOT);

            TK.skipWhiteSpace(buf, cursor);
            final char ch = currentChar(buf, cursor);

            if (ch == '=') {
                cursor.updatePos(cursor.getPos() + 1); // consume '='
                TK.skipWhiteSpace(buf, cursor);

                if ("u".equals(key)) {
                    final String numTok = TK.parseToken(buf, cursor, VALUE_DELIMS);
                    final Integer u = safeParseInt(numTok);
                    if (u != null && u >= 0 && u <= 7) {
                        urgency = u;
                    }
                } else if ("i".equals(key)) {
                    // Accept RFC 8941 booleans '?1'/'?0' and tolerant '1'/'0'
                    final char b = currentChar(buf, cursor);
                    if (b == '?') {
                        cursor.updatePos(cursor.getPos() + 1);
                        final char v = currentChar(buf, cursor);
                        if (v == '1') {
                            incremental = true;
                            cursor.updatePos(cursor.getPos() + 1);
                        } else if (v == '0') {
                            incremental = false;
                            cursor.updatePos(cursor.getPos() + 1);
                        }
                    } else {
                        final String tok = TK.parseToken(buf, cursor, VALUE_DELIMS);
                        if ("1".equals(tok)) {
                            incremental = true;
                        } else if ("0".equals(tok)) {
                            incremental = false;
                        }
                    }
                } else {
                    // Unknown member -> parse & ignore its value token
                    TK.parseToken(buf, cursor, VALUE_DELIMS);
                }

                skipParamsThenNextItem(buf, cursor);

            } else {
                // Bare member: only "i" counts (true)
                if ("i".equals(key)) {
                    incremental = true;
                }
                skipParamsThenNextItem(buf, cursor);
            }
        }

        return PriorityValue.of(urgency, incremental);
    }

    // ---- helpers (no deprecated APIs) ----

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

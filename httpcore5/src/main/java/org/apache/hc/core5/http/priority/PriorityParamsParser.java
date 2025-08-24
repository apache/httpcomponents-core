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

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.apache.hc.core5.util.Tokenizer;

/**
 * Parser for the HTTP {@code Priority} header (RFC 9218).
 * <p>
 * Recognizes the subset used here:
 * <ul>
 *   <li>{@code u} — urgency, integer in the range {@code 0..7}</li>
 *   <li>{@code i} — incremental, boolean ({@code 1}/{@code 0} or {@code ?1}/{@code ?0});
 *       a bare {@code i} (no value) is treated as {@code true}</li>
 * </ul>
 * The parser is tolerant: keys are case-insensitive, unknown members and malformed values are ignored,
 * comma-separated items and semicolon-separated parameters are supported, and absence is reported
 * as {@code null} fields in {@link PriorityParams}. This class is stateless and thread-safe.
 * <p><strong>Internal:</strong> not part of the public API and subject to change without notice.
 */
@Internal
public final class PriorityParamsParser {
    private static final Tokenizer TK = Tokenizer.INSTANCE;
    private static final Tokenizer.Delimiter KEY_DELIMS = Tokenizer.delimiters('=', ',', ';');
    private static final Tokenizer.Delimiter VALUE_DELIMS = Tokenizer.delimiters(',', ';');

    private PriorityParamsParser() {
    }

    public static PriorityParams parse(final String headerValue) {
        Integer urgency = null;     // null means “u absent”
        Boolean incremental = null; // null means “i absent”

        if (headerValue == null || headerValue.isEmpty()) {
            return new PriorityParams(null, null);
        }

        final CharArrayBuffer buf = new CharArrayBuffer(headerValue.length());
        buf.append(headerValue);
        final Tokenizer.Cursor c = new Tokenizer.Cursor(0, buf.length());

        while (!c.atEnd()) {
            TK.skipWhiteSpace(buf, c);
            if (c.atEnd()) break;

            final String rawKey = TK.parseToken(buf, c, KEY_DELIMS);
            if (rawKey == null || rawKey.isEmpty()) {
                skipToNextItem(buf, c);
                continue;
            }
            final String key = rawKey.toLowerCase(Locale.ROOT);
            TK.skipWhiteSpace(buf, c);
            final char ch = c.atEnd() ? 0 : buf.charAt(c.getPos());

            if (ch == '=') {
                c.updatePos(c.getPos() + 1);
                TK.skipWhiteSpace(buf, c);

                if ("u".equals(key)) {
                    final String numTok = TK.parseToken(buf, c, VALUE_DELIMS);
                    try {
                        final int u = Integer.parseInt(numTok);
                        if (u >= 0 && u <= 7) urgency = u;
                    } catch (final Exception ignore) { /* absent on error */ }
                } else if ("i".equals(key)) {
                    final char b = c.atEnd() ? 0 : buf.charAt(c.getPos());
                    if (b == '?') {
                        c.updatePos(c.getPos() + 1);
                        final char v = c.atEnd() ? 0 : buf.charAt(c.getPos());
                        if (v == '1') {
                            incremental = Boolean.TRUE;
                            c.updatePos(c.getPos() + 1);
                        } else if (v == '0') {
                            incremental = Boolean.FALSE;
                            c.updatePos(c.getPos() + 1);
                        }
                    } else {
                        final String tok = TK.parseToken(buf, c, VALUE_DELIMS);
                        if ("1".equals(tok)) incremental = Boolean.TRUE;
                        else if ("0".equals(tok)) incremental = Boolean.FALSE;
                    }
                } else {
                    TK.parseToken(buf, c, VALUE_DELIMS); // ignore unknown member with value
                }
                skipParamsThenNextItem(buf, c);
            } else {
                if ("i".equals(key)) incremental = Boolean.TRUE; // bare true
                skipParamsThenNextItem(buf, c);
            }
        }
        return new PriorityParams(urgency, incremental);
    }

    private static void skipToNextItem(final CharSequence buf, final Tokenizer.Cursor c) {
        while (!c.atEnd()) {
            final char ch = buf.charAt(c.getPos());
            c.updatePos(c.getPos() + 1);
            if (ch == ',') break;
        }
    }

    private static void skipParamsThenNextItem(final CharSequence buf, final Tokenizer.Cursor c) {
        while (!c.atEnd()) {
            final int pos = c.getPos();
            final char ch = buf.charAt(pos);
            if (ch == ';') {
                c.updatePos(pos + 1);
                TK.parseToken(buf, c, VALUE_DELIMS);
                continue;
            }
            break;
        }
        while (!c.atEnd()) {
            final char ch = buf.charAt(c.getPos());
            if (ch == ',') {
                c.updatePos(c.getPos() + 1);
                break;
            }
            c.updatePos(c.getPos() + 1);
        }
    }
}

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

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.util.Tokenizer;

@Internal
public final class PriorityParamsParser {
    private static final Tokenizer TK = Tokenizer.INSTANCE;
    private static final Tokenizer.Delimiter KEY_DELIMS = Tokenizer.delimiters('=', ',', ';');
    private static final Tokenizer.Delimiter VALUE_DELIMS = Tokenizer.delimiters(',', ';');

    private PriorityParamsParser() {
    }

    public static PriorityParams parse(final CharSequence src, final ParserCursor c) {
        Integer u = null;
        Boolean i = null;
        while (!c.atEnd()) {
            TK.skipWhiteSpace(src, c);
            if (c.atEnd()) {
                break;
            }
            final String k = TK.parseToken(src, c, KEY_DELIMS);
            if (k == null || k.isEmpty()) {
                skipToNextItem(src, c);
                continue;
            }
            TK.skipWhiteSpace(src, c);
            final char ch = c.atEnd() ? 0 : src.charAt(c.getPos());
            if (ch == '=') {
                c.updatePos(c.getPos() + 1);
                TK.skipWhiteSpace(src, c);
                if ("u".equalsIgnoreCase(k)) {
                    final String t = TK.parseToken(src, c, VALUE_DELIMS);
                    try {
                        final int v = Integer.parseInt(t);
                        if (v >= 0 && v <= 7) {
                            u = v;
                        }
                    } catch (final Exception ignore) {
                    }
                } else if ("i".equalsIgnoreCase(k)) {
                    final char b = c.atEnd() ? 0 : src.charAt(c.getPos());
                    if (b == '?') {
                        c.updatePos(c.getPos() + 1);
                        final char v = c.atEnd() ? 0 : src.charAt(c.getPos());
                        if (v == '1') {
                            i = Boolean.TRUE;
                            c.updatePos(c.getPos() + 1);
                        } else if (v == '0') {
                            i = Boolean.FALSE;
                            c.updatePos(c.getPos() + 1);
                        }
                    } else {
                        final String t = TK.parseToken(src, c, VALUE_DELIMS);
                        if ("1".equals(t)) {
                            i = Boolean.TRUE;
                        } else if ("0".equals(t)) {
                            i = Boolean.FALSE;
                        }
                    }
                } else {
                    TK.parseToken(src, c, VALUE_DELIMS);
                }
                skipParamsThenNextItem(src, c);
            } else {
                if ("i".equalsIgnoreCase(k)) {
                    i = Boolean.TRUE;
                }
                skipParamsThenNextItem(src, c);
            }
        }
        return new PriorityParams(u, i);
    }

    public static PriorityParams parse(final String value) {
        if (value == null || value.isEmpty()) {
            return new PriorityParams(null, null);
        }
        final ParserCursor c = new ParserCursor(0, value.length());
        return parse(value, c);
    }

    public static PriorityParams parse(final Header header) {
        if (header == null) {
            return new PriorityParams(null, null);
        }
        final PriorityParams[] box = new PriorityParams[1];
        MessageSupport.parseHeader(header, (seq, cur) -> box[0] = parse(seq, cur));
        return box[0] != null ? box[0] : new PriorityParams(null, null);
    }

    private static void skipToNextItem(final CharSequence buf, final ParserCursor c) {
        while (!c.atEnd()) {
            final char ch = buf.charAt(c.getPos());
            c.updatePos(c.getPos() + 1);
            if (ch == ',') {
                break;
            }
        }
    }

    private static void skipParamsThenNextItem(final CharSequence buf, final ParserCursor c) {
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

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

package org.apache.hc.core5.http;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.util.TextUtils;
import org.apache.hc.core5.util.Tokenizer;

@Internal
public class ProtocolVersionParser {

    public final static ProtocolVersionParser INSTANCE = new ProtocolVersionParser();

    private static final char SLASH = '/';
    private static final char FULL_STOP = '.';
    private static final Tokenizer.Delimiter PROTO_DELIMITER = Tokenizer.delimiters(SLASH);
    private static final Tokenizer.Delimiter FULL_STOP_OR_BLANK = Tokenizer.delimiters(FULL_STOP, ' ', '\t');
    private static final Tokenizer.Delimiter BLANK = Tokenizer.delimiters(' ', '\t');
    private final Tokenizer tokenizer;

    public ProtocolVersionParser() {
        this.tokenizer = Tokenizer.INSTANCE;
    }

    @Internal
    @FunctionalInterface
    public interface Factory {

        ProtocolVersion create(int major, int minor);

    }

    public ProtocolVersion parse(
            final String protocol,
            final Factory factory,
            final CharSequence buffer,
            final Tokenizer.Cursor cursor,
            final Tokenizer.Delimiter delimiterPredicate) throws ParseException {
        final int lowerBound = cursor.getLowerBound();
        final int upperBound = cursor.getUpperBound();
        final String token1 = tokenizer.parseToken(buffer, cursor,
                delimiterPredicate != null ? (ch) -> delimiterPredicate.test(ch) || FULL_STOP_OR_BLANK.test(ch) : FULL_STOP_OR_BLANK);
        final int major;
        try {
            major = Integer.parseInt(token1);
        } catch (final NumberFormatException e) {
            throw new ParseException("Invalid " + protocol + " major version number",
                    buffer, lowerBound, upperBound, cursor.getPos());
        }
        if (cursor.atEnd()) {
            return factory != null ? factory.create(major, major) : new ProtocolVersion(protocol, major, 0);
        }
        if (buffer.charAt(cursor.getPos()) != FULL_STOP) {
            return factory != null ? factory.create(major, major) : new ProtocolVersion(protocol, major, 0);
        } else {
            cursor.updatePos(cursor.getPos() + 1);
            final String token2 = tokenizer.parseToken(buffer, cursor,
                    delimiterPredicate != null ? (ch) -> delimiterPredicate.test(ch) || BLANK.test(ch) : BLANK);
            final int minor;
            try {
                minor = Integer.parseInt(token2);
            } catch (final NumberFormatException e) {
                throw new ParseException("Invalid " + protocol + " minor version number",
                        buffer, lowerBound, upperBound, cursor.getPos());
            }
            return factory != null ? factory.create(major, minor) : new ProtocolVersion(protocol, major, minor);
        }
    }

    public ProtocolVersion parse(
            final String protocol,
            final CharSequence buffer,
            final Tokenizer.Cursor cursor,
            final Tokenizer.Delimiter delimiterPredicate) throws ParseException {
        return parse(protocol, null, buffer, cursor, delimiterPredicate);
    }

    public ProtocolVersion parse(
            final String protocol,
            final CharSequence buffer,
            final Tokenizer.Cursor cursor) throws ParseException {
        return parse(protocol, null, buffer, cursor, null);
    }

    public ProtocolVersion parse(
            final CharSequence buffer,
            final Tokenizer.Cursor cursor,
            final Tokenizer.Delimiter delimiterPredicate) throws ParseException {
        tokenizer.skipWhiteSpace(buffer, cursor);
        final String proto = tokenizer.parseToken(buffer, cursor, PROTO_DELIMITER);
        if (TextUtils.isBlank(proto)) {
            throw new ParseException("Invalid protocol name");
        }
        if (!cursor.atEnd() && buffer.charAt(cursor.getPos()) == SLASH) {
            cursor.updatePos(cursor.getPos() + 1);
            return parse(proto, null, buffer, cursor, delimiterPredicate);
        } else {
            throw new ParseException("Invalid protocol name");
        }
    }

}

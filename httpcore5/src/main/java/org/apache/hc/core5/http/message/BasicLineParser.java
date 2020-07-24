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
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.apache.hc.core5.util.TextUtils;
import org.apache.hc.core5.util.Tokenizer;

/**
 * Default {@link org.apache.hc.core5.http.message.LineParser} implementation.
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class BasicLineParser implements LineParser {

    public final static BasicLineParser INSTANCE = new BasicLineParser();

    // IMPORTANT!
    // These private static variables must be treated as immutable and never exposed outside this class
    private static final BitSet FULL_STOP = Tokenizer.INIT_BITSET('.');
    private static final BitSet BLANKS = Tokenizer.INIT_BITSET(' ', '\t');
    private static final BitSet COLON = Tokenizer.INIT_BITSET(':');

    /**
     * A version of the protocol to parse.
     * The version is typically not relevant, but the protocol name.
     */
    private final ProtocolVersion protocol;
    private final Tokenizer tokenizer;

    /**
     * Creates a new line parser for the given HTTP-like protocol.
     *
     * @param proto     a version of the protocol to parse, or
     *                  {@code null} for HTTP. The actual version
     *                  is not relevant, only the protocol name.
     */
    public BasicLineParser(final ProtocolVersion proto) {
        this.protocol = proto != null? proto : HttpVersion.HTTP_1_1;
        this.tokenizer = Tokenizer.INSTANCE;
    }

    /**
     * Creates a new line parser for HTTP.
     */
    public BasicLineParser() {
        this(null);
    }

    ProtocolVersion parseProtocolVersion(
            final CharArrayBuffer buffer,
            final ParserCursor cursor) throws ParseException {
        final String protoname = this.protocol.getProtocol();
        final int protolength  = protoname.length();

        this.tokenizer.skipWhiteSpace(buffer, cursor);

        final int pos = cursor.getPos();

        // long enough for "HTTP/1.1"?
        if (pos + protolength + 4 > cursor.getUpperBound()) {
            throw new ParseException("Invalid protocol version",
                    buffer, cursor.getLowerBound(), cursor.getUpperBound(), cursor.getPos());
        }

        // check the protocol name and slash
        boolean ok = true;
        for (int i = 0; ok && (i < protolength); i++) {
            ok = buffer.charAt(pos + i) == protoname.charAt(i);
        }
        if (ok) {
            ok = buffer.charAt(pos + protolength) == '/';
        }
        if (!ok) {
            throw new ParseException("Invalid protocol version",
                    buffer, cursor.getLowerBound(), cursor.getUpperBound(), cursor.getPos());
        }

        cursor.updatePos(pos + protolength + 1);

        final String token1 = this.tokenizer.parseToken(buffer, cursor, FULL_STOP);
        final int major;
        try {
            major = Integer.parseInt(token1);
        } catch (final NumberFormatException e) {
            throw new ParseException("Invalid protocol major version number",
                    buffer, cursor.getLowerBound(), cursor.getUpperBound(), cursor.getPos());
        }
        if (cursor.atEnd()) {
            throw new ParseException("Invalid protocol version",
                    buffer, cursor.getLowerBound(), cursor.getUpperBound(), cursor.getPos());
        }
        cursor.updatePos(cursor.getPos() + 1);
        final String token2 = this.tokenizer.parseToken(buffer, cursor, BLANKS);
        final int minor;
        try {
            minor = Integer.parseInt(token2);
        } catch (final NumberFormatException e) {
            throw new ParseException("Invalid protocol minor version number",
                    buffer, cursor.getLowerBound(), cursor.getUpperBound(), cursor.getPos());
        }
        return HttpVersion.get(major, minor);
    }

    /**
     * Parses a request line.
     *
     * @param buffer    a buffer holding the line to parse
     *
     * @return  the parsed request line
     *
     * @throws ParseException        in case of a parse error
     */
    @Override
    public RequestLine parseRequestLine(final CharArrayBuffer buffer) throws ParseException {
        Args.notNull(buffer, "Char array buffer");

        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        this.tokenizer.skipWhiteSpace(buffer, cursor);
        final String method = this.tokenizer.parseToken(buffer, cursor, BLANKS);
        if (TextUtils.isEmpty(method)) {
            throw new ParseException("Invalid request line",
                    buffer, cursor.getLowerBound(), cursor.getUpperBound(), cursor.getPos());
        }
        this.tokenizer.skipWhiteSpace(buffer, cursor);
        final String uri = this.tokenizer.parseToken(buffer, cursor, BLANKS);
        if (TextUtils.isEmpty(uri)) {
            throw new ParseException("Invalid request line",
                    buffer, cursor.getLowerBound(), cursor.getUpperBound(), cursor.getPos());
        }
        final ProtocolVersion ver = parseProtocolVersion(buffer, cursor);
        this.tokenizer.skipWhiteSpace(buffer, cursor);
        if (!cursor.atEnd()) {
            throw new ParseException("Invalid request line",
                    buffer, cursor.getLowerBound(), cursor.getUpperBound(), cursor.getPos());
        }
        return new RequestLine(method, uri, ver);
    }

    @Override
    public StatusLine parseStatusLine(final CharArrayBuffer buffer) throws ParseException {
        Args.notNull(buffer, "Char array buffer");

        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        this.tokenizer.skipWhiteSpace(buffer, cursor);
        final ProtocolVersion ver = parseProtocolVersion(buffer, cursor);
        this.tokenizer.skipWhiteSpace(buffer, cursor);
        final String s = this.tokenizer.parseToken(buffer, cursor, BLANKS);
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                throw new ParseException("Status line contains invalid status code",
                        buffer, cursor.getLowerBound(), cursor.getUpperBound(), cursor.getPos());
            }
        }
        final int statusCode;
        try {
            statusCode = Integer.parseInt(s);
        } catch (final NumberFormatException e) {
            throw new ParseException("Status line contains invalid status code",
                    buffer, cursor.getLowerBound(), cursor.getUpperBound(), cursor.getPos());
        }
        final String text = buffer.substringTrimmed(cursor.getPos(), cursor.getUpperBound());
        return new StatusLine(ver, statusCode, text);
    }

    @Override
    public Header parseHeader(final CharArrayBuffer buffer) throws ParseException {
        Args.notNull(buffer, "Char array buffer");

        final ParserCursor cursor = new ParserCursor(0, buffer.length());
        this.tokenizer.skipWhiteSpace(buffer, cursor);
        final String name = this.tokenizer.parseToken(buffer, cursor, COLON);
        if (cursor.getPos() == cursor.getLowerBound() || cursor.getPos() == cursor.getUpperBound() ||
                buffer.charAt(cursor.getPos()) != ':' ||
                TextUtils.isEmpty(name) ||
                Tokenizer.isWhitespace(buffer.charAt(cursor.getPos() - 1))) {
            throw new ParseException("Invalid header",
                    buffer, cursor.getLowerBound(), cursor.getUpperBound(), cursor.getPos());
        }
        final String value = buffer.substringTrimmed(cursor.getPos() + 1, cursor.getUpperBound());
        return new BasicHeader(name, value);
    }

}

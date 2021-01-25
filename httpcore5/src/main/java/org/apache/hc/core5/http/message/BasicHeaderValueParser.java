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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Tokenizer;

/**
 * Default {@link org.apache.hc.core5.http.message.HeaderValueParser} implementation.
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class BasicHeaderValueParser implements HeaderValueParser {

    public final static BasicHeaderValueParser INSTANCE = new BasicHeaderValueParser();

    private final static char PARAM_DELIMITER                = ';';
    private final static char ELEM_DELIMITER                 = ',';

    // IMPORTANT!
    // These private static variables must be treated as immutable and never exposed outside this class
    private static final BitSet TOKEN_DELIMS = Tokenizer.INIT_BITSET('=', PARAM_DELIMITER, ELEM_DELIMITER);
    private static final BitSet VALUE_DELIMS = Tokenizer.INIT_BITSET(PARAM_DELIMITER, ELEM_DELIMITER);

    private final Tokenizer tokenizer;

    public BasicHeaderValueParser() {
        this.tokenizer = Tokenizer.INSTANCE;
    }

    /**
     * An empty immutable {@code HeaderElement} array.
     */
    private static final HeaderElement[] EMPTY_HEADER_ELEMENT_ARRAY = new HeaderElement[0];

    /**
     * An empty immutable {@code NameValuePair} array.
     */
    private static final NameValuePair[] EMPTY_NAME_VALUE_ARRAY = new NameValuePair[0];

    @Override
    public HeaderElement[] parseElements(final CharSequence buffer, final ParserCursor cursor) {
        Args.notNull(buffer, "Char sequence");
        Args.notNull(cursor, "Parser cursor");
        final List<HeaderElement> elements = new ArrayList<>();
        while (!cursor.atEnd()) {
            final HeaderElement element = parseHeaderElement(buffer, cursor);
            if (!(element.getName().isEmpty() && element.getValue() == null)) {
                elements.add(element);
            }
        }
        return elements.toArray(EMPTY_HEADER_ELEMENT_ARRAY);
    }

    @Override
    public HeaderElement parseHeaderElement(final CharSequence buffer, final ParserCursor cursor) {
        Args.notNull(buffer, "Char sequence");
        Args.notNull(cursor, "Parser cursor");
        final NameValuePair nvp = parseNameValuePair(buffer, cursor);
        NameValuePair[] params = null;
        if (!cursor.atEnd()) {
            final char ch = buffer.charAt(cursor.getPos() - 1);
            if (ch != ELEM_DELIMITER) {
                params = parseParameters(buffer, cursor);
            }
        }
        return new BasicHeaderElement(nvp.getName(), nvp.getValue(), params);
    }

    @Override
    public NameValuePair[] parseParameters(final CharSequence buffer, final ParserCursor cursor) {
        Args.notNull(buffer, "Char sequence");
        Args.notNull(cursor, "Parser cursor");
        tokenizer.skipWhiteSpace(buffer, cursor);
        final List<NameValuePair> params = new ArrayList<>();
        while (!cursor.atEnd()) {
            final NameValuePair param = parseNameValuePair(buffer, cursor);
            params.add(param);
            final char ch = buffer.charAt(cursor.getPos() - 1);
            if (ch == ELEM_DELIMITER) {
                break;
            }
        }
        return params.toArray(EMPTY_NAME_VALUE_ARRAY);
    }

    @Override
    public NameValuePair parseNameValuePair(final CharSequence buffer, final ParserCursor cursor) {
        Args.notNull(buffer, "Char sequence");
        Args.notNull(cursor, "Parser cursor");

        final String name = tokenizer.parseToken(buffer, cursor, TOKEN_DELIMS);
        if (cursor.atEnd()) {
            return new BasicNameValuePair(name, null);
        }
        final int delim = buffer.charAt(cursor.getPos());
        cursor.updatePos(cursor.getPos() + 1);
        if (delim != '=') {
            return new BasicNameValuePair(name, null);
        }
        final String value = tokenizer.parseValue(buffer, cursor, VALUE_DELIMS);
        if (!cursor.atEnd()) {
            cursor.updatePos(cursor.getPos() + 1);
        }
        return new BasicNameValuePair(name, value);
    }

}


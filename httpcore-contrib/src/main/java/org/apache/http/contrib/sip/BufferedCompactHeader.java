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

package org.apache.http.contrib.sip;

import org.apache.http.FormattedHeader;
import org.apache.http.HeaderElement;
import org.apache.http.ParseException;
import org.apache.http.util.CharArrayBuffer;
import org.apache.http.message.ParserCursor;
import org.apache.http.message.BasicHeaderValueParser;


/**
 * Represents a SIP (or HTTP) header field parsed 'on demand'.
 * The name of the header will be parsed and mapped immediately,
 * the value only when accessed
 *
 *
 */
public class BufferedCompactHeader
    implements CompactHeader, FormattedHeader, Cloneable {

    /** The full header name. */
    private final String fullName;

    /** The compact header name, if there is one. */
    private final String compactName;

    /**
     * The buffer containing the entire header line.
     */
    private final CharArrayBuffer buffer;

    /**
     * The beginning of the header value in the buffer
     */
    private final int valuePos;


    /**
     * Creates a new header from a buffer.
     * The name of the header will be parsed and mapped immediately,
     * the value only if it is accessed.
     *
     * @param buffer    the buffer containing the header to represent
     * @param mapper    the header name mapper, or <code>null</code> for the
     *                  {@link BasicCompactHeaderMapper#DEFAULT default}
     *
     * @throws ParseException   in case of a parse error
     */
    public BufferedCompactHeader(final CharArrayBuffer buffer,
                                 CompactHeaderMapper mapper)
        throws ParseException {

        super();
        if (buffer == null) {
            throw new IllegalArgumentException
                ("Char array buffer may not be null");
        }

        final int colon = buffer.indexOf(':');
        if (colon == -1) {
            throw new ParseException
                ("Missing colon after header name.\n" + buffer.toString());
        }
        final String name = buffer.substringTrimmed(0, colon);
        if (name.length() == 0) {
            throw new ParseException
                ("Missing header name.\n" + buffer.toString());
        }

        if (mapper == null)
            mapper = BasicCompactHeaderMapper.DEFAULT;
        final String altname = mapper.getAlternateName(name);

        String fname = name;
        String cname = altname;

        if ((altname != null) && (name.length() < altname.length())) {
            // the header uses the compact name
            fname = altname;
            cname = name;
        }

        this.fullName    = fname;
        this.compactName = cname;
        this.buffer      = buffer;
        this.valuePos    = colon + 1;
    }


    // non-javadoc, see interface Header
    public String getName() {
        return this.fullName;
    }


    // non-javadoc, see interface CompactHeader
    public String getCompactName() {
        return this.compactName;
    }

    // non-javadoc, see interface Header
    public String getValue() {
        return this.buffer.substringTrimmed(this.valuePos,
                                            this.buffer.length());
    }

    // non-javadoc, see interface Header
    public HeaderElement[] getElements() throws ParseException {
        ParserCursor cursor = new ParserCursor(0, this.buffer.length());
        cursor.updatePos(this.valuePos);
        return BasicHeaderValueParser.DEFAULT
            .parseElements(this.buffer, cursor);
    }

    // non-javadoc, see interface BufferedHeader
    public int getValuePos() {
        return this.valuePos;
    }

    // non-javadoc, see interface BufferedHeader
    public CharArrayBuffer getBuffer() {
        return this.buffer;
    }

    @Override
    public String toString() {
        return this.buffer.toString();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        // buffer is considered immutable
        // no need to make a copy of it
        return super.clone();
    }

}



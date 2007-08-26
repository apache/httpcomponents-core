/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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

package org.apache.http.message;


import java.util.List;
import java.util.ArrayList;

import org.apache.http.HeaderElement;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.CharArrayBuffer;



/**
 * Basic implementation for parsing header values into elements.
 * Instances of this class are stateless and thread-safe.
 * Derived classes are expected to maintain these properties.
 *
 * @author <a href="mailto:bcholmes@interlog.com">B.C. Holmes</a>
 * @author <a href="mailto:jericho@thinkfree.com">Park, Sung-Gu</a>
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * @author <a href="mailto:oleg at ural.com">Oleg Kalnichevski</a>
 * @author and others
 *
 *
 * <!-- empty lines above to avoid 'svn diff' context problems -->
 * @version $Revision$
 *
 * @since 4.0
 */
public class BasicHeaderValueParser implements HeaderValueParser {

    /**
     * A default instance of this class, for use as default or fallback.
     * Note that {@link BasicHeaderValueParser} is not a singleton, there
     * can be many instances of the class itself and of derived classes.
     * The instance here provides non-customized, default behavior.
     */
    public final static
        BasicHeaderValueParser DEFAULT = new BasicHeaderValueParser();


    // public default constructor


    /**
     * Parses elements with the given parser.
     *
     * @param value     the header value to parse
     * @param parser    the parser to use, or <code>null</code> for default
     *
     * @return  array holding the header elements, never <code>null</code>
     */
    public final static
        HeaderElement[] parseElements(String value,
                                      HeaderValueParser parser)
        throws ParseException {

        if (value == null) {
            throw new IllegalArgumentException
                ("Value to parse may not be null.");
        }

        if (parser == null)
            parser = BasicHeaderValueParser.DEFAULT;

        CharArrayBuffer buffer = new CharArrayBuffer(value.length());
        buffer.append(value);
        return parser.parseElements(buffer, 0, buffer.length());
    }


    // non-javadoc, see interface HeaderValueParser
    public HeaderElement[] parseElements(final CharArrayBuffer buffer,
                                         final int indexFrom,
                                         final int indexTo) {

        if (buffer == null) {
            throw new IllegalArgumentException
                ("Char array buffer may not be null");
        }
        if (indexFrom < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (indexTo > buffer.length()) {
            throw new IndexOutOfBoundsException();
        }
        if (indexFrom > indexTo) {
            throw new IndexOutOfBoundsException();
        }
        List elements = new ArrayList(); 
        int cur = indexFrom;
        int from = indexFrom;
        boolean qouted = false;
        boolean escaped = false;
        while (cur < indexTo) {
            char ch = buffer.charAt(cur);
            if (ch == '"' && !escaped) {
                qouted = !qouted;
            }
            HeaderElement element = null;
            if ((!qouted) && (ch == ',')) {
                element = parseHeaderElement(buffer, from, cur);
                from = cur + 1;
            } else if (cur == indexTo - 1) {
                element = parseHeaderElement(buffer, from, indexTo);
            }
            if (element != null && !(element.getName().length() == 0 &&
                                     element.getValue() == null)
                ) {
                elements.add(element);
            }
            if (escaped) {
                escaped = false;
            } else {
                escaped = qouted && ch == '\\';
            }
            cur++;
        }
        return (HeaderElement[])
            elements.toArray(new HeaderElement[elements.size()]);
    }


    /**
     * Parses an element with the given parser.
     *
     * @param value     the header element to parse
     * @param parser    the parser to use, or <code>null</code> for default
     *
     * @return  the parsed header element
     */
    public final static
        HeaderElement parseHeaderElement(String value,
                                         HeaderValueParser parser)
        throws ParseException {

        if (value == null) {
            throw new IllegalArgumentException
                ("Value to parse may not be null.");
        }

        if (parser == null)
            parser = BasicHeaderValueParser.DEFAULT;

        CharArrayBuffer buffer = new CharArrayBuffer(value.length());
        buffer.append(value);
        return parser.parseHeaderElement(buffer, 0, buffer.length());
    }




    // non-javadoc, see interface HeaderValueParser
    public HeaderElement parseHeaderElement(final CharArrayBuffer buffer,
                                            final int indexFrom,
                                            final int indexTo) {

        if (buffer == null) {
            throw new IllegalArgumentException
                ("Char array buffer may not be null");
        }
        if (indexFrom < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (indexTo > buffer.length()) {
            throw new IndexOutOfBoundsException();
        }
        if (indexFrom > indexTo) {
            throw new IndexOutOfBoundsException();
        }
        NameValuePair[] nvps = parseParameters(buffer, indexFrom, indexTo);
        return createHeaderElement(nvps);
    }


    /**
     * Craetes a header element.
     * Called from {@link #parseHeaderElement}.
     *
     * @param nvps      the name-value pairs
     *
     * @return  a header element representing the argument
     */
    protected HeaderElement createHeaderElement(NameValuePair[] nvps) {
        return new BasicHeaderElement(nvps);
    }


    /**
     * Parses parameters with the given parser.
     *
     * @param value     the parameter list to parse
     * @param parser    the parser to use, or <code>null</code> for default
     *
     * @return  array holding the parameters, never <code>null</code>
     */
    public final static
        NameValuePair[] parseParameters(String value,
                                        HeaderValueParser parser)
        throws ParseException {

        if (value == null) {
            throw new IllegalArgumentException
                ("Value to parse may not be null.");
        }

        if (parser == null)
            parser = BasicHeaderValueParser.DEFAULT;

        CharArrayBuffer buffer = new CharArrayBuffer(value.length());
        buffer.append(value);
        return parser.parseParameters(buffer, 0, buffer.length());
    }



    // non-javadoc, see interface HeaderValueParser
    public NameValuePair[] parseParameters(final CharArrayBuffer buffer,
                                           final int indexFrom,
                                           final int indexTo) {

        if (buffer == null) {
            throw new IllegalArgumentException
                ("Char array buffer may not be null");
        }
        if (indexFrom < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (indexTo > buffer.length()) {
            throw new IndexOutOfBoundsException();
        }
        if (indexFrom > indexTo) {
            throw new IndexOutOfBoundsException();
        }
        List params = new ArrayList(); 
        int cur = indexFrom;
        int from = indexFrom;
        boolean qouted = false;
        boolean escaped = false;
        while (cur < indexTo) {
            char ch = buffer.charAt(cur);
            if (ch == '"' && !escaped) {
                qouted = !qouted;
            }
            NameValuePair param = null;
            if (!qouted && ch == ';') {
                param = parseNameValuePair(buffer, from, cur);
                from = cur + 1;
            } else if (cur == indexTo - 1) {
                param = parseNameValuePair(buffer, from, indexTo);
            }
            if (param != null && !(param.getName().length() == 0 &&
                                   param.getValue() == null)
                ) {
                params.add(param);
            }
            if (escaped) {
                escaped = false;
            } else {
                escaped = qouted && ch == '\\';
            }
            cur++;
        }
        return (NameValuePair[])
            params.toArray(new NameValuePair[params.size()]);
    }


    /**
     * Parses a name-value-pair with the given parser.
     *
     * @param value     the NVP to parse
     * @param parser    the parser to use, or <code>null</code> for default
     *
     * @return  the parsed name-value pair
     */
    public final static
       NameValuePair parseNameValuePair(String value,
                                        HeaderValueParser parser)
        throws ParseException {

        if (value == null) {
            throw new IllegalArgumentException
                ("Value to parse may not be null.");
        }

        if (parser == null)
            parser = BasicHeaderValueParser.DEFAULT;

        CharArrayBuffer buffer = new CharArrayBuffer(value.length());
        buffer.append(value);
        return parser.parseNameValuePair(buffer, 0, buffer.length());
    }


    /**
     * Parses a name=value specification, where the = and value are optional.
     *
     * @param buffer    the buffer holding the name-value pair to parse
     *
     * @return  the name-value pair, where the value is <code>null</code>
     *          if no value is specified
     */
    public NameValuePair parseNameValuePair(final CharArrayBuffer buffer,
                                            final int indexFrom,
                                            final int indexTo) {

        if (buffer == null) {
            throw new IllegalArgumentException
                ("Char array buffer may not be null");
        }
        if (indexFrom < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (indexTo > buffer.length()) {
            throw new IndexOutOfBoundsException();
        }
        if (indexFrom > indexTo) {
            throw new IndexOutOfBoundsException();
        }

        int eq = buffer.indexOf('=', indexFrom, indexTo);
        if (eq < 0) {
            return createNameValuePair(buffer.substringTrimmed(indexFrom, indexTo), null);
        }
        String name = buffer.substringTrimmed(indexFrom, eq);
        int i1 = eq + 1;
        int i2 = indexTo;
        // Trim leading white spaces
        while (i1 < i2 && (HTTP.isWhitespace(buffer.charAt(i1)))) {
            i1++;
        }
        // Trim trailing white spaces
        while ((i2 > i1) && (HTTP.isWhitespace(buffer.charAt(i2 - 1)))) {
            i2--;
        }
        // Strip away quotes if necessary
        if (((i2 - i1) >= 2) 
            && (buffer.charAt(i1) == '"') 
            && (buffer.charAt(i2 - 1) == '"')) {
            i1++;
            i2--;
        }
        String value = buffer.substring(i1, i2);
        return createNameValuePair(name, value);
    }


    /**
     * Creates a name-value pair.
     * Called from {@link #parseNameValuePair}.
     *
     * @param name      the name
     * @param value     the value, or <code>null</code>
     *
     * @return  a name-value pair representing the arguments
     */
    protected NameValuePair createNameValuePair(String name, String value) {
        return new BasicNameValuePair(name, value);
    }


}


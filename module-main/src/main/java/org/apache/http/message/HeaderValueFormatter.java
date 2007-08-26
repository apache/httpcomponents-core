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


import org.apache.http.HeaderElement;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.util.CharArrayBuffer;



/**
 * Interface for formatting elements of a header value.
 * This is the complement to {@link HeaderValueParser}.
 * Instances of this interface are expected to be stateless and thread-safe.
 *
 * <p>
 * All formatting methods accept an optional buffer argument.
 * If a buffer is passed in, the formatted element will be appended
 * and the modified buffer is returned. If no buffer is passed in,
 * a new buffer will be created and filled with the formatted element.
 * In both cases, the caller is allowed to modify the returned buffer.
 * </p>
 *
 *
 * <!-- empty lines above to avoid 'svn diff' context problems -->
 * @version $Revision$
 *
 * @since 4.0
 */
public interface HeaderValueFormatter {

    /* *
     * Parses a header value into elements.
     * Parse errors are indicated as <code>RuntimeException</code>.
     * <p>
     * Some HTTP headers (such as the set-cookie header) have values that
     * can be decomposed into multiple elements. In order to be processed
     * by this parser, such headers must be in the following form:
     * </p>
     * <pre>
     * header  = [ element ] *( "," [ element ] )
     * element = name [ "=" [ value ] ] *( ";" [ param ] )
     * param   = name [ "=" [ value ] ]
     *
     * name    = token
     * value   = ( token | quoted-string )
     *
     * token         = 1*&lt;any char except "=", ",", ";", &lt;"&gt; and
     *                       white space&gt;
     * quoted-string = &lt;"&gt; *( text | quoted-char ) &lt;"&gt;
     * text          = any char except &lt;"&gt;
     * quoted-char   = "\" char
     * </pre>
     * <p>
     * Any amount of white space is allowed between any part of the
     * header, element or param and is ignored. A missing value in any
     * element or param will be stored as the empty {@link String};
     * if the "=" is also missing <var>null</var> will be stored instead.
     * </p>
     *
     * @param buffer    buffer holding the header value to parse
     *
     * @return  an array holding all elements of the header value
     *
     * @throws ParseException        in case of a parse error
     * /
    HeaderElement[] parseElements(CharArrayBuffer buffer,
                                  int indexFrom,
                                  int indexTo)
        throws ParseException
        ;
    */


    /* *
     * Parses a single header element.
     * A header element consist of a semicolon-separate list
     * of name=value definitions.
     *
     * @param buffer    buffer holding the element to parse
     *
     * @return  the parsed element
     *
     * @throws ParseException        in case of a parse error
     * /
    HeaderElement parseHeaderElement(CharArrayBuffer buffer,
                                     int indexFrom,
                                     int indexTo)
        throws ParseException
        ;
    */

    /* *
     * Parses a list of name-value pairs.
     * These lists are used to specify parameters to a header element.
     * Parse errors are indicated as <code>RuntimeException</code>.
     * <p>
     * This method comforms to the generic grammar and formatting rules
     * outlined in the 
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec2.html#sec2.2"
     *   >Section 2.2</a>
     * and
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6"
     *   >Section 3.6</a>
     * of
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616.txt">RFC 2616</a>.
     * </p>
     * <h>2.2 Basic Rules</h>
     * <p>
     * The following rules are used throughout this specification to
     * describe basic parsing constructs. 
     * The US-ASCII coded character set is defined by ANSI X3.4-1986.
     * </p>
     * <pre>
     *     OCTET          = <any 8-bit sequence of data>
     *     CHAR           = <any US-ASCII character (octets 0 - 127)>
     *     UPALPHA        = <any US-ASCII uppercase letter "A".."Z">
     *     LOALPHA        = <any US-ASCII lowercase letter "a".."z">
     *     ALPHA          = UPALPHA | LOALPHA
     *     DIGIT          = <any US-ASCII digit "0".."9">
     *     CTL            = <any US-ASCII control character
     *                      (octets 0 - 31) and DEL (127)>
     *     CR             = <US-ASCII CR, carriage return (13)>
     *     LF             = <US-ASCII LF, linefeed (10)>
     *     SP             = <US-ASCII SP, space (32)>
     *     HT             = <US-ASCII HT, horizontal-tab (9)>
     *     <">            = <US-ASCII double-quote mark (34)>
     * </pre>
     * <p>
     * Many HTTP/1.1 header field values consist of words separated
     * by LWS or special characters. These special characters MUST be
     * in a quoted string to be used within 
     * a parameter value (as defined in section 3.6).
     * <p>
     * <pre>
     * token          = 1*<any CHAR except CTLs or separators>
     * separators     = "(" | ")" | "<" | ">" | "@"
     *                | "," | ";" | ":" | "\" | <">
     *                | "/" | "[" | "]" | "?" | "="
     *                | "{" | "}" | SP | HT
     * </pre>
     * <p>
     * A string of text is parsed as a single word if it is quoted using
     * double-quote marks.
     * </p>
     * <pre>
     * quoted-string  = ( <"> *(qdtext | quoted-pair ) <"> )
     * qdtext         = <any TEXT except <">>
     * </pre>
     * <p>
     * The backslash character ("\") MAY be used as a single-character
     * quoting mechanism only within quoted-string and comment constructs.
     * </p>
     * <pre>
     * quoted-pair    = "\" CHAR
     * </pre>
     * <h>3.6 Transfer Codings</h>
     * <p>
     * Parameters are in the form of attribute/value pairs.
     * </p>
     * <pre>
     * parameter               = attribute "=" value
     * attribute               = token
     * value                   = token | quoted-string
     * </pre> 
     *
     * @param buffer    buffer holding the name-value list to parse
     *
     * @return  an array holding all items of the name-value list
     *
     * @throws ParseException        in case of a parse error
     * /
    NameValuePair[] parseParameters(CharArrayBuffer buffer,
                                    int indexFrom,
                                    int indexTo)
        throws ParseException
        ;
    */


    /**
     * Formats the parameters of a header element.
     * That's a list of name-value pairs, to be separated by semicolons.
     * This method will <i>not</i> generate a leading semicolon.
     *
     * @param buffer    the buffer to append to, or
     *                  <code>null</code> to create a new buffer
     * @param nvps      the parameters (name-value pairs) to format
     * @param quote     <code>true</code> to always format with quoted values,
     *                  <code>false</code> to use quotes only when necessary
     *
     * @return  a buffer with the formatted parameters.
     *          If the <code>buffer</code> argument was not <code>null</code>,
     *          that buffer will be returned.
     */
    CharArrayBuffer formatParameters(CharArrayBuffer buffer,
                                     NameValuePair[] nvps,
                                     boolean quote)
        ;


    /**
     * Formats one name-value pair, where the value is optional.
     *
     * @param buffer    the buffer to append to, or
     *                  <code>null</code> to create a new buffer
     * @param nvp       the name-value pair to format
     * @param quote     <code>true</code> to always format with a quoted value,
     *                  <code>false</code> to use quotes only when necessary
     *
     * @return  a buffer with the formatted name-value pair.
     *          If the <code>buffer</code> argument was not <code>null</code>,
     *          that buffer will be returned.
     */
    CharArrayBuffer formatNameValuePair(CharArrayBuffer buffer,
                                        NameValuePair nvp,
                                        boolean quote)
        ;

}


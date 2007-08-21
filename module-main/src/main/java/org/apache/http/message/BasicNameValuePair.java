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

import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.CharArrayBuffer;
import org.apache.http.util.LangUtils;

/**
 * A simple class encapsulating an attribute/value pair.
 * <p>
 *  This class comforms to the generic grammar and formatting rules outlined in the 
 *  <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec2.html#sec2.2">Section 2.2</a>
 *  and  
 *  <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6">Section 3.6</a>
 *  of <a href="http://www.w3.org/Protocols/rfc2616/rfc2616.txt">RFC 2616</a>
 * </p>
 * <h>2.2 Basic Rules</h>
 * <p>
 *  The following rules are used throughout this specification to describe basic parsing constructs. 
 *  The US-ASCII coded character set is defined by ANSI X3.4-1986.
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
 *  Many HTTP/1.1 header field values consist of words separated by LWS or special 
 *  characters. These special characters MUST be in a quoted string to be used within 
 *  a parameter value (as defined in section 3.6).
 * <p>
 * <pre>
 * token          = 1*<any CHAR except CTLs or separators>
 * separators     = "(" | ")" | "<" | ">" | "@"
 *                | "," | ";" | ":" | "\" | <">
 *                | "/" | "[" | "]" | "?" | "="
 *                | "{" | "}" | SP | HT
 * </pre>
 * <p>
 *  A string of text is parsed as a single word if it is quoted using double-quote marks.
 * </p>
 * <pre>
 * quoted-string  = ( <"> *(qdtext | quoted-pair ) <"> )
 * qdtext         = <any TEXT except <">>
 * </pre>
 * <p>
 *  The backslash character ("\") MAY be used as a single-character quoting mechanism only 
 *  within quoted-string and comment constructs.
 * </p>
 * <pre>
 * quoted-pair    = "\" CHAR
 * </pre>
 * <h>3.6 Transfer Codings</h>
 * <p>
 *  Parameters are in the form of attribute/value pairs.
 * </p>
 * <pre>
 * parameter               = attribute "=" value
 * attribute               = token
 * value                   = token | quoted-string
 * </pre> 
 * 
 * @author <a href="mailto:oleg at ural.com">Oleg Kalnichevski</a>
 * 
 */
public class BasicNameValuePair implements NameValuePair {

    private final String name;
    private final String value;

    /**
     * Default Constructor taking a name and a value. The value may be null.
     * 
     * @param name The name.
     * @param value The value.
     */
    public BasicNameValuePair(final String name, final String value) {
        super();
        if (name == null) {
            throw new IllegalArgumentException("Name may not be null");
        }
        this.name = name;
        this.value = value;
    }

    /**
     * Returns the name.
     *
     * @return String name The name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Returns the value.
     *
     * @return String value The current value.
     */
    public String getValue() {
        return this.value;
    }


    /**
     * Special characters that can be used as separators in HTTP parameters.
     * These special characters MUST be in a quoted string to be used within
     * a parameter value 
     */
    private static final char[] SEPARATORS   = {
            '(', ')', '<', '>', '@', 
            ',', ';', ':', '\\', '"', 
            '/', '[', ']', '?', '=',
            '{', '}', ' ', '\t'
            };
    
    /**
     * Unsafe special characters that must be escaped using the backslash
     * character
     */
    private static final char[] UNSAFE_CHARS = {
            '"', '\\'
            };
    
    private static boolean isOneOf(char[] chars, char ch) {
        for (int i = 0; i < chars.length; i++) {
            if (ch == chars[i]) {
                return true;
            }
        }
        return false;
    }
    
    private static boolean isUnsafeChar(char ch) {
        return isOneOf(UNSAFE_CHARS, ch);
    }
    
    private static boolean isSeparator(char ch) {
        return isOneOf(SEPARATORS, ch);
    }

    private static void format(
            final CharArrayBuffer buffer, 
            final String value, 
            boolean alwaysUseQuotes) {
        boolean unsafe = false;
        if (alwaysUseQuotes) {
            unsafe = true;
        } else {
            for (int i = 0; i < value.length(); i++) {
                if (isSeparator(value.charAt(i))) {
                    unsafe = true;
                    break;
                }
            }
        }
        if (unsafe) buffer.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (isUnsafeChar(ch)) {
                buffer.append('\\');
            }
            buffer.append(ch);
        }
        if (unsafe) buffer.append('"');
    }
    
    /**
     * Produces textual representaion of the attribute/value pair using 
     * formatting rules defined in RFC 2616
     *  
     * @param buffer output buffer 
     * @param param the parameter to be formatted
     * @param alwaysUseQuotes <tt>true</tt> if the parameter values must 
     * always be enclosed in quotation marks, <tt>false</tt> otherwise
     */
    public static void format(
            final CharArrayBuffer buffer, 
            final NameValuePair param, 
            boolean alwaysUseQuotes) {
        if (buffer == null) {
            throw new IllegalArgumentException("String buffer may not be null");
        }
        if (param == null) {
            throw new IllegalArgumentException("Parameter may not be null");
        }
        buffer.append(param.getName());
        String value = param.getValue();
        if (value != null) {
            buffer.append("=");
            format(buffer, value, alwaysUseQuotes);
        }
    }
    
    /**
     * Produces textual representaion of the attribute/value pairs using 
     * formatting rules defined in RFC 2616
     *  
     * @param buffer output buffer 
     * @param params the parameters to be formatted
     * @param alwaysUseQuotes <tt>true</tt> if the parameter values must 
     * always be enclosed in quotation marks, <tt>false</tt> otherwise
     */
    public static void formatAll(
            final CharArrayBuffer buffer, 
            final NameValuePair[] params, 
            boolean alwaysUseQuotes) {
        if (buffer == null) {
            throw new IllegalArgumentException("String buffer may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("Array of parameter may not be null");
        }
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                buffer.append("; ");
            }
            format(buffer, params[i], alwaysUseQuotes);
        }
    }
    
    /**
     * Produces textual representaion of the attribute/value pair using 
     * formatting rules defined in RFC 2616
     *  
     * @param param the parameter to be formatted
     * @param alwaysUseQuotes <tt>true</tt> if the parameter values must 
     * always be enclosed in quotation marks, <tt>false</tt> otherwise
     * 
     * @return RFC 2616 conformant textual representaion of the 
     * attribute/value pair
     */
    public static String format(final NameValuePair param, boolean alwaysUseQuotes) {
        CharArrayBuffer buffer = new CharArrayBuffer(16);
        format(buffer, param, alwaysUseQuotes);
        return buffer.toString();
    }
    
    /**
     * Produces textual representaion of the attribute/value pair using 
     * formatting rules defined in RFC 2616
     *  
     * @param params the parameters to be formatted
     * @param alwaysUseQuotes <tt>true</tt> if the parameter values must 
     * always be enclosed in quotation marks, <tt>false</tt> otherwise
     * 
     * @return RFC 2616 conformant textual representaion of the 
     * attribute/value pair
     */
    public static String formatAll(final NameValuePair[] params, boolean alwaysUseQuotes) {
        CharArrayBuffer buffer = new CharArrayBuffer(16);
        formatAll(buffer, params, alwaysUseQuotes);
        return buffer.toString();
    }
    
    /**
     * Get a string representation of this pair.
     * 
     * @return A string representation.
     */
    public String toString() {
        CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append(this.name);
        if (this.value != null) {
            buffer.append("=");
            format(buffer, this.value, false);
        }
        return buffer.toString();
    }

    public boolean equals(final Object object) {
        if (object == null) return false;
        if (this == object) return true;
        if (object instanceof NameValuePair) {
            BasicNameValuePair that = (BasicNameValuePair) object;
            return this.name.equals(that.name)
                  && LangUtils.equals(this.value, that.value);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.name);
        hash = LangUtils.hashCode(hash, this.value);
        return hash;
    }
    
}

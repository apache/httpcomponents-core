/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2006 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.io.CharArrayBuffer;
import org.apache.http.util.LangUtils;

/**
 * <p>One element of an HTTP header's value.</p>
 * <p>
 * Some HTTP headers (such as the set-cookie header) have values that
 * can be decomposed into multiple elements.  Such headers must be in the
 * following form:
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
 * <p>
 * This class represents an individual header element, containing
 * both a name/value pair (value may be <tt>null</tt>) and optionally
 * a set of additional parameters.
 * </p>
 * <p>
 * This class also exposes a {@link #parse} method for parsing a
 * {@link Header} value into an array of elements.
 * </p>
 *
 * @see Header
 *
 * @author <a href="mailto:bcholmes@interlog.com">B.C. Holmes</a>
 * @author <a href="mailto:jericho@thinkfree.com">Park, Sung-Gu</a>
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * @author <a href="mailto:oleg at ural.com">Oleg Kalnichevski</a>
 * 
 * @since 1.0
 * @version $Revision$ $Date$
 */
public class HeaderElement {

    private final String name;
    private final String value;
    private final NameValuePair[] parameters;

    /**
     * Constructor with name, value and parameters.
     *
     * @param name header element name
     * @param value header element value. May be <tt>null</tt>
     * @param parameters header element parameters. May be <tt>null</tt>
     */
    public HeaderElement(
            final String name, 
            final String value,
            final NameValuePair[] parameters) {
        super();
        if (name == null) {
            throw new IllegalArgumentException("Name may not be null");
        }
        this.name = name;
        this.value = value;
        if (parameters != null) {
            this.parameters = parameters;
        } else {
            this.parameters = new NameValuePair[] {};
        }
    }

    /**
     * Constructor with name and value.
     * 
     * @param name header element name
     * @param value header element value. May be <tt>null</tt>
     */
    public HeaderElement(final String name, final String value) {
       this(name, value, null);
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
     * Get parameters, if any.
     *
     * @since 2.0
     * @return parameters as an array of {@link NameValuePair}s
     */
    public NameValuePair[] getParameters() {
        return this.parameters;
    }

    // --------------------------------------------------------- Public Methods

    /**
     * This parses the value part of a header. The result is an array of
     * HeaderElement objects.
     *
     * @param buffer    the buffer from which to parse
     * @param indexFrom where to start parsing in the buffer
     * @param indexTo   where to stop parsing in the buffer
     *
     * @return array of {@link HeaderElement}s.
     * 
     * @since 3.0
     */
    public static final HeaderElement[] parseAll(
            final CharArrayBuffer buffer, final int indexFrom, final int indexTo) {
        if (buffer == null) {
            throw new IllegalArgumentException("Char array buffer may not be null");
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
                element = parse(buffer, from, cur);
                from = cur + 1;
            } else if (cur == indexTo - 1) {
                element = parse(buffer, from, indexTo);
            }
            if (element != null && !element.getName().equals("")) {
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
     * This parses the value part of a header. The result is an array of
     * HeaderElement objects.
     *
     * @param s  the string representation of the header value
     *                     (as received from the web server).
     * @return array of {@link HeaderElement}s.
     * 
     * @since 3.0
     */
    public static final HeaderElement[] parseAll(final String s) {
        if (s == null) {
            throw new IllegalArgumentException("String may not be null");
        }
        CharArrayBuffer buffer = new CharArrayBuffer(s.length()); 
        buffer.append(s);
        return parseAll(buffer, 0, buffer.length());
    }

    public static HeaderElement parse(
            final CharArrayBuffer buffer, final int indexFrom, final int indexTo) {
        if (buffer == null) {
            throw new IllegalArgumentException("Char array buffer may not be null");
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
        NameValuePair[] nvps = NameValuePair.parseAll(buffer, indexFrom, indexTo);
        if (nvps.length > 0) {
            NameValuePair nvp = nvps[0];
            String name = nvp.getName();
            String value = nvp.getValue();
            NameValuePair[] params = null;
            int len = nvps.length - 1; 
            if (len > 0) {
                params = new NameValuePair[len];
                System.arraycopy(nvps, 1, params, 0, len);
            }
            return new HeaderElement(name, value, params);
        } else {
            return new HeaderElement("", null, null);
        }
    }

    public static final HeaderElement parse(final String s) {
        if (s == null) {
            throw new IllegalArgumentException("String may not be null");
        }
        CharArrayBuffer buffer = new CharArrayBuffer(s.length());
        buffer.append(s);
        return parse(buffer, 0, buffer.length());
    }

    public static void format(
            final CharArrayBuffer buffer, 
            final HeaderElement element) {
        if (buffer == null) {
            throw new IllegalArgumentException("String buffer may not be null");
        }
        if (element == null) {
            throw new IllegalArgumentException("Header element may not be null");
        }
        buffer.append(element.getName());
        if (element.getValue() != null) {
            buffer.append("=");
            buffer.append(element.getValue());
        }
        NameValuePair[] params = element.getParameters();
        for (int i = 0; i < params.length; i++) {
            buffer.append("; ");
            NameValuePair.format(buffer, params[i], false);
        }
    }
    
    public static String format(final HeaderElement element) {
        CharArrayBuffer buffer = new CharArrayBuffer(32);
        format(buffer, element);
        return buffer.toString();
    }
    
    public static void formatAll(
            final CharArrayBuffer buffer, 
            final HeaderElement[] elements) {
        if (buffer == null) {
            throw new IllegalArgumentException("String buffer may not be null");
        }
        if (elements == null) {
            throw new IllegalArgumentException("Array of header element may not be null");
        }
        for (int i = 0; i < elements.length; i++) {
            if (i > 0) {
                buffer.append(", ");
            }
            format(buffer, elements[i]);
        }
    }
    
    public static String formatAll(final HeaderElement[] elements) {
        CharArrayBuffer buffer = new CharArrayBuffer(64);
        formatAll(buffer, elements);
        return buffer.toString();
    }
    
    /**
     * Returns parameter with the given name, if found. Otherwise null 
     * is returned
     *
     * @param name The name to search by.
     * @return NameValuePair parameter with the given name
     */
    public NameValuePair getParameterByName(final String name) {
        if (name == null) {
            throw new IllegalArgumentException("Name may not be null");
        } 
        NameValuePair found = null;
        for (int i = 0; i < this.parameters.length; i++) {
            NameValuePair current = this.parameters[ i ];
            if (current.getName().equalsIgnoreCase(name)) {
                found = current;
                break;
            }
        }
        return found;
    }

    public boolean equals(final Object object) {
        if (object == null) return false;
        if (this == object) return true;
        if (object instanceof HeaderElement) {
            HeaderElement that = (HeaderElement) object;
            return this.name.equals(that.name)
                && LangUtils.equals(this.value, that.value)
                && LangUtils.equals(this.parameters, that.parameters);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.name);
        hash = LangUtils.hashCode(hash, this.value);
        for (int i = 0; i < this.parameters.length; i++) {
            hash = LangUtils.hashCode(hash, this.parameters[i]);
        }
        return hash;
    }
    
    public String toString() {
        CharArrayBuffer buffer = new CharArrayBuffer(64);
        buffer.append(this.name);
        if (this.value != null) {
            buffer.append("=");
            buffer.append(this.value);
        }
        for (int i = 0; i < this.parameters.length; i++) {
            buffer.append("; ");
            buffer.append(this.parameters[i]);
        }
        return buffer.toString();
    }
    
}


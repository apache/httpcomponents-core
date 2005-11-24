/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2004 The Apache Software Foundation
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
import org.apache.http.util.ParameterParser;

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
 * @author <a href="mailto:oleg@ural.com">Oleg Kalnichevski</a>
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
     * Constructor with array of characters.
     *
     * @param chars the array of characters
     * @param offset - the initial offset.
     * @param length - the length.
     * 
     * @since 3.0
     */
    public HeaderElement(char[] chars, int offset, int length) {
        super();
        ParameterParser parser = new ParameterParser();
        List params = parser.parse(chars, offset, length, ';');
        if (params.size() > 0) {
            NameValuePair element = (NameValuePair) params.remove(0);
            this.name = element.getName();
            this.value = element.getValue();
            if (params.size() > 0) {
                this.parameters = (NameValuePair[])
                    params.toArray(new NameValuePair[params.size()]);    
            } else {
                this.parameters = new NameValuePair[] {};
            }
        } else {
            this.name = "";
            this.value = null;
            this.parameters = new NameValuePair[] {};
        }
    }

    /**
     * Constructor with array of characters.
     *
     * @param chars the array of characters
     * 
     * @since 3.0
     */
    public HeaderElement(char[] chars) {
        this(chars, 0, chars.length);
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
     * @param headerValue  the array of char representation of the header value
     *                     (as received from the web server).
     * @return array of {@link HeaderElement}s.
     * 
     * @since 3.0
     */
    public static final HeaderElement[] parseElements(char[] headerValue, int indexFrom, int indexTo) {
        if (headerValue == null) {
            return new HeaderElement[] {};
        }
        List elements = new ArrayList(); 
        int i = indexFrom;
        boolean qouted = false;
        while (i < indexTo) {
            char ch = headerValue[i];
            if (ch == '"') {
                qouted = !qouted;
            }
            HeaderElement element = null;
            if ((!qouted) && (ch == ',')) {
                element = new HeaderElement(headerValue, indexFrom, i);
                indexFrom = i + 1;
            } else if (i == indexTo - 1) {
                element = new HeaderElement(headerValue, indexFrom, indexTo);
            }
            if (element != null && !element.getName().equals("")) {
                elements.add(element);
            }
            i++;
        }
        return (HeaderElement[])
            elements.toArray(new HeaderElement[elements.size()]);
    }

    /**
     * This parses the value part of a header. The result is an array of
     * HeaderElement objects.
     *
     * @param headerValue  the string representation of the header value
     *                     (as received from the web server).
     * @return array of {@link HeaderElement}s.
     * 
     * @since 3.0
     */
    public static final HeaderElement[] parseElements(final String headerValue) {
        if (headerValue == null) {
            return new HeaderElement[] {};
        }
        return parseElements(headerValue.toCharArray(), 0, headerValue.length());
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
            buffer.append(" = ");
            buffer.append(this.value);
        }
        for (int i = 0; i < this.parameters.length; i++) {
            buffer.append("; ");
            buffer.append(this.parameters[i]);
        }
        return buffer.toString();
    }
    
}


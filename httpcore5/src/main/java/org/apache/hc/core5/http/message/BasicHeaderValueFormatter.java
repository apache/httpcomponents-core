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

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Default {@link org.apache.hc.core5.http.message.HeaderValueFormatter} implementation.
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class BasicHeaderValueFormatter implements HeaderValueFormatter {

    public final static BasicHeaderValueFormatter INSTANCE = new BasicHeaderValueFormatter();

    private final static String SEPARATORS = " ;,:@()<>\\\"/[]?={}\t";
    private final static String UNSAFE_CHARS = "\"\\";

    public BasicHeaderValueFormatter() {
        super();
    }

    @Override
    public void formatElements(
            final CharArrayBuffer buffer, final HeaderElement[] elems, final boolean quote) {
        Args.notNull(buffer, "Char array buffer");
        Args.notNull(elems, "Header element array");

        for (int i = 0; i < elems.length; i++) {
            if (i > 0) {
                buffer.append(", ");
            }
            formatHeaderElement(buffer, elems[i], quote);
        }
    }

    @Override
    public void formatHeaderElement(
            final CharArrayBuffer buffer, final HeaderElement elem, final boolean quote) {
        Args.notNull(buffer, "Char array buffer");
        Args.notNull(elem, "Header element");

        buffer.append(elem.getName());
        final String value = elem.getValue();
        if (value != null) {
            buffer.append('=');
            formatValue(buffer, value, quote);
        }

        final int c = elem.getParameterCount();
        if (c > 0) {
            for (int i = 0; i < c; i++) {
                buffer.append("; ");
                formatNameValuePair(buffer, elem.getParameter(i), quote);
            }
        }
    }

    @Override
    public void formatParameters(
            final CharArrayBuffer buffer, final NameValuePair[] nvps, final boolean quote) {
        Args.notNull(buffer, "Char array buffer");
        Args.notNull(nvps, "Header parameter array");

        for (int i = 0; i < nvps.length; i++) {
            if (i > 0) {
                buffer.append("; ");
            }
            formatNameValuePair(buffer, nvps[i], quote);
        }
    }

    @Override
    public void formatNameValuePair(
            final CharArrayBuffer buffer, final NameValuePair nvp, final boolean quote) {
        Args.notNull(buffer, "Char array buffer");
        Args.notNull(nvp, "Name / value pair");

        buffer.append(nvp.getName());
        final String value = nvp.getValue();
        if (value != null) {
            buffer.append('=');
            formatValue(buffer, value, quote);
        }
    }

    void formatValue(final CharArrayBuffer buffer, final String value, final boolean quote) {

        boolean quoteFlag = quote;
        if (!quoteFlag) {
            for (int i = 0; (i < value.length()) && !quoteFlag; i++) {
                quoteFlag = isSeparator(value.charAt(i));
            }
        }

        if (quoteFlag) {
            buffer.append('"');
        }
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (isUnsafe(ch)) {
                buffer.append('\\');
            }
            buffer.append(ch);
        }
        if (quoteFlag) {
            buffer.append('"');
        }
    }

    boolean isSeparator(final char ch) {
        return SEPARATORS.indexOf(ch) >= 0;
    }

    boolean isUnsafe(final char ch) {
        return UNSAFE_CHARS.indexOf(ch) >= 0;
    }

}

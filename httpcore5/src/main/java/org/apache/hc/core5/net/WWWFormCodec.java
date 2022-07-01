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

package org.apache.hc.core5.net;

import java.nio.charset.Charset;
import java.util.List;

import org.apache.hc.core5.http.NameValuePair;

/**
 * {@code application/x-www-form-urlencoded} codec.
 *
 * @since 5.1
 */
public class WWWFormCodec {

    private static final char QP_SEP_A = '&';

    /**
     * Returns a list of {@link NameValuePair} parameters parsed
     * from the {@code application/x-www-form-urlencoded} content.
     *
     * @param s input text.
     * @param charset parameter charset.
     * @return list of form parameters.
     */
    public static List<NameValuePair> parse(final CharSequence s, final Charset charset) {
        return URIBuilder.parseQuery(s, charset, true);
    }

    /**
     * Formats the list of {@link NameValuePair} parameters into a {@code application/x-www-form-urlencoded}
     * content.
     *
     * @param buf the content buffer
     * @param params  The from parameters.
     * @param charset The encoding to use.
     */
    public static void format(
            final StringBuilder buf, final Iterable<? extends NameValuePair> params, final Charset charset) {
        URIBuilder.formatQuery(buf, params, charset, true);
    }

    /**
     * Formats the list of {@link NameValuePair} parameters into a {@code application/x-www-form-urlencoded}
     * content string.
     *
     * @param params  The from parameters.
     * @param charset The encoding to use.
     * @return content string
     */
    public static String format(final Iterable<? extends NameValuePair> params, final Charset charset) {
        final StringBuilder buf = new StringBuilder();
        URIBuilder.formatQuery(buf, params, charset, true);
        return buf.toString();
    }

}

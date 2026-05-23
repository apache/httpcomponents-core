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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.apache.hc.core5.util.Args;

/**
 * URI path segment codec.
 *
 * @since 5.5
 */
public class PathSegmentCodec {

    /**
     * Returns a list of URI path segments.
     *
     * @param s URI path component.
     * @param charset parameter charset.
     * @return list of segments.
     */
    public static List<String> parsePathSegments(final CharSequence s, final Charset charset) {
        return URIBuilder.parsePath(s, charset);
    }

    /**
     * Returns a list of URI path segments.
     *
     * @param s URI path component.
     * @return list of segments.
     */
    public static List<String> parsePathSegments(final CharSequence s) {
        return parsePathSegments(s, StandardCharsets.UTF_8);
    }

    /**
     * Returns a string consisting of joint encoded path segments.
     *
     * @param segments the segments.
     * @param charset parameter charset.
     * @return URI path component
     */
    public static String formatSegments(final Iterable<String> segments, final Charset charset) {
        Args.notNull(segments, "Segments");
        final StringBuilder buf = new StringBuilder();
        URIBuilder.formatPath(buf, segments, false, charset);
        return buf.toString();
    }

    /**
     * Returns a string consisting of joint encoded path segments.
     *
     * @param segments the segments.
     * @return URI path component
     */
    public static String formatSegments(final String... segments) {
        return formatSegments(Arrays.asList(segments), StandardCharsets.UTF_8);
    }

}

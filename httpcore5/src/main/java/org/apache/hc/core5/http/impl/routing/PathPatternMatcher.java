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

package org.apache.hc.core5.http.impl.routing;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * URI path component pattern matcher.
 * <p>
 * Patterns may have three formats:
 * </p>
 * <ul>
 * <li>{@code *}</li>
 * <li>{@code *<uri-path>}</li>
 * <li>{@code <uri-path>*}</li>
 * </ul>
 *
 * @since 5.3
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public final class PathPatternMatcher {

    public static final PathPatternMatcher INSTANCE = new PathPatternMatcher();

    public boolean match(final String pattern, final String path) {
        if (pattern.equals("*") || pattern.equals(path)) {
            return true;
        }
        return pattern.endsWith("*") && path.startsWith(pattern.substring(0, pattern.length() - 1))
                || pattern.startsWith("*") && path.endsWith(pattern.substring(1));
    }

    public boolean isBetter(final String pattern, final String bestMatch) {
        return bestMatch == null
                || bestMatch.length() < pattern.length()
                || bestMatch.length() == pattern.length() && pattern.endsWith("*");
    }

}

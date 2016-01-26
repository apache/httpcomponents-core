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

package org.apache.hc.core5.http.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BufferedHeader;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Utility class to generate Trailer header.
 *
 * @since 5.0
 */
public class TrailerNameFormatter {

    public static Header format(final HttpEntity entity) {
        if (entity == null) {
            return null;
        }
        final Set<String> trailerNames = entity.getTrailerNames();
        if (trailerNames != null && !trailerNames.isEmpty()) {
            final List<String> elements = new ArrayList<>(trailerNames);
            Collections.sort(elements);
            final CharArrayBuffer buffer = new CharArrayBuffer(trailerNames.size() + 20);
            buffer.append(HttpHeaders.TRAILER);
            buffer.append(": ");
            for (int i = 0; i < elements.size(); i++) {
                final String element = elements.get(i);
                if (i > 0) {
                    buffer.append(", ");
                }
                buffer.append(element);
            }
            return BufferedHeader.create(buffer);
        }
        return null;
    }

}

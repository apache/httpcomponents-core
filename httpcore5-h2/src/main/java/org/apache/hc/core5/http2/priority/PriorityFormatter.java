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
package org.apache.hc.core5.http2.priority;


import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BufferedHeader;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Formats PriorityValue as RFC 9218 Structured Fields Dictionary.
 * Only emits non-defaults: u when != 3, i when true.
 * Returns null when both are defaults (callers should omit the header then).
 */
@Internal
public final class PriorityFormatter {

    private PriorityFormatter() {
    }

    public static void format(final CharArrayBuffer dst, final PriorityValue value) {
        if (value == null) {
            return;
        }
        boolean urgencyPresent = false;
        if (value.getUrgency() != PriorityValue.DEFAULT_URGENCY) {
            dst.append("u=");
            dst.append(value.getUrgency());
            urgencyPresent = true;
        }
        if (value.isIncremental()) {
            // In SF Dictionary, boolean true can be represented by key without value (per RFC 8941).
            if (urgencyPresent) {
                dst.append(", ");
            }
            dst.append("i");
        }

    }

    public static String format(final PriorityValue value) {
        if (value == null) {
            return null;
        }
        final CharArrayBuffer buf = new CharArrayBuffer(16);
        format(buf, value);
        return buf.toString();
    }

    public static Header formatHeader(final PriorityValue value) {
        if (value == null) {
            return new BasicHeader(HttpHeaders.PRIORITY, null);
        }
        final CharArrayBuffer buf = new CharArrayBuffer(16);
        buf.append(HttpHeaders.PRIORITY);
        buf.append(": ");
        format(buf, value);
        return BufferedHeader.create(buf);
    }

}
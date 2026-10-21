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
package org.apache.hc.core5.http.impl;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Validates serialized HTTP/1 field lines without modifying their content.
 */
@Internal
public final class Http1FieldValidator {

    private Http1FieldValidator() {
    }

    public static void validate(final CharArrayBuffer line) throws ProtocolException {
        final int colon = line.indexOf(':');
        if (colon <= 0) {
            throw new ProtocolException("Invalid HTTP/1 field name");
        }
        for (int i = 0; i < colon; i++) {
            if (!isTokenChar(line.charAt(i))) {
                throw new ProtocolException("Invalid HTTP/1 field name");
            }
        }
        for (int i = colon + 1; i < line.length(); i++) {
            final char ch = line.charAt(i);
            if (ch < 0x20 && ch != '\t' || ch == 0x7f) {
                throw new ProtocolException("Invalid HTTP/1 field value");
            }
        }
    }

    private static boolean isTokenChar(final char ch) {
        if (ch >= '0' && ch <= '9'
                || ch >= 'A' && ch <= 'Z'
                || ch >= 'a' && ch <= 'z') {
            return true;
        }
        switch (ch) {
            case '!':
            case '#':
            case '$':
            case '%':
            case '&':
            case '\'':
            case '*':
            case '+':
            case '-':
            case '.':
            case '^':
            case '_':
            case '`':
            case '|':
            case '~':
                return true;
            default:
                return false;
        }
    }
}

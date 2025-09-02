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

package org.apache.hc.core5.http2.impl;

import org.apache.hc.core5.annotation.Internal;

@Internal
public class FieldValidationSupport {

    public static boolean isWhitespace(final char ch) {
        return ch == 0x20 || ch == 0x09;
    }

    public static boolean isNameCharValid(final char ch) {
        return ch > 0x20 && ch != ':' && ch < 0x7f;
    }

    public static boolean isNameCharLowerCaseValid(final char ch) {
        return isNameCharValid(ch) && (ch < 0x41 || ch > 0x5a);
    }

    public static boolean isValueCharValid(final char ch) {
        return ch != 0x00 && ch != 0x0a && ch != 0x0d;
    }

    public static boolean isNameValid(final CharSequence s, final int pos, final int len) {
        for (int i = pos; i < len; i++) {
            if (!isNameCharValid(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isNameValid(final CharSequence s) {
        return isNameValid(s, 0, s.length());
    }

    public static boolean isNameLowerCaseValid(final CharSequence s, final int pos, final int len) {
        for (int i = pos; i < len; i++) {
            if (!isNameCharLowerCaseValid(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isNameLowerCaseValid(final CharSequence s) {
        return isNameLowerCaseValid(s, 0, s.length());
    }

    public static boolean isValueValid(final CharSequence s) {
        if (s.length() == 0) {
            return true;
        }
        if (isWhitespace(s.charAt(0)) || isWhitespace(s.charAt(s.length() - 1))) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!isValueCharValid(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

}

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

import java.net.URISyntaxException;
import java.util.BitSet;

import org.apache.hc.core5.util.Tokenizer;

final class URISupport {

    static final BitSet HOST_SEPARATORS = new BitSet(256);
    static final BitSet IPV6_HOST_TERMINATORS = new BitSet(256);
    static final BitSet PORT_SEPARATORS = new BitSet(256);
    static final BitSet TERMINATORS = new BitSet(256);

    static {
        TERMINATORS.set('/');
        TERMINATORS.set('#');
        TERMINATORS.set('?');
        HOST_SEPARATORS.or(TERMINATORS);
        HOST_SEPARATORS.set('@');
        IPV6_HOST_TERMINATORS.set(']');
        PORT_SEPARATORS.or(TERMINATORS);
        PORT_SEPARATORS.set(':');
    }

    static URISyntaxException createException(
            final CharSequence input, final Tokenizer.Cursor cursor, final String reason) {
        return new URISyntaxException(
                input.subSequence(cursor.getLowerBound(), cursor.getUpperBound()).toString(),
                reason,
                cursor.getPos());
    }

}

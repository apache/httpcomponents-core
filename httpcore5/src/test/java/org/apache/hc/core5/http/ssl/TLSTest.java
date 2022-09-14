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

package org.apache.hc.core5.http.ssl;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.hc.core5.http.ParseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TLSTest {


    @Test
    void isSame() throws ParseException {
        assertTrue(TLS.V_1_0.isSame(TLS.parse("TLSv1")));
    }

    @Test
    void isComparable() throws ParseException {
        assertTrue(TLS.V_1_0.isComparable(TLS.parse("TLSv1")));
    }

    @Test
    void greaterEquals() throws ParseException {
        assertTrue(TLS.V_1_3.greaterEquals(TLS.parse("TLSv1")));
    }

    @Test
    void lessEquals() throws ParseException {
        assertTrue(TLS.V_1_0.lessEquals(TLS.parse("TLSv1.3")));
    }

    @Test
    void parse() throws ParseException {
        assertTrue(TLS.V_1_0.lessEquals(TLS.parse("TLSv1.3")));
    }

    @Test
    void parseNull() throws ParseException {
        assertNull(TLS.parse(null));
    }

    @Test
    void excludeWeakNull() {
        assertNull((TLS.excludeWeak((String[]) null)));
    }

    @Test
    void excludeWeak() {
        final String[] mixProtocol = {
                "SSL 2.0",
                "TLS 1.3",
                "SSL 3.0",
                "TLS 1.2",
                "TLS 1.1"
        };
        final String[] strongProtocols = TLS.excludeWeak(mixProtocol);
        for (final String protocol : strongProtocols) {
            Assertions.assertTrue(TLS.isSecure(protocol));
        }
    }
}

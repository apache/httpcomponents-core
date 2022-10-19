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

package org.apache.hc.core5.testing.nio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

public class H2AlpnTests {

    @Nested
    @DisplayName("Strict h2 ALPN, h2 allowed")
    public class StrictH2AlpnH2Allowed extends H2AlpnTest {

        public StrictH2AlpnH2Allowed() throws Exception {
            super(true, true);
        }

    }

    @Nested
    @DisplayName("Strict h2 ALPN, h2 disallowed")
    public class StrictH2AlpnH2Disllowed extends H2AlpnTest {

        public StrictH2AlpnH2Disllowed() throws Exception {
            super(true, false);
        }

    }

    @Nested
    @DisplayName("Non-strict h2 ALPN, h2 allowed")
    public class NonStrictH2AlpnH2Disllowed extends H2AlpnTest {

        public NonStrictH2AlpnH2Disllowed() throws Exception {
            super(false, true);
        }

    }
}

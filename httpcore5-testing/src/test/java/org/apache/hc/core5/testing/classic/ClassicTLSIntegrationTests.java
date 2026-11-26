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

package org.apache.hc.core5.testing.classic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.parallel.Isolated;

class ClassicTLSIntegrationTests {

    @Nested
    @Isolated
    @DisplayName("TLS Oracle")
    class OracleJSSEAlpnTest extends ClassicTLSIntegrationTest {

        public OracleJSSEAlpnTest() throws Exception {
            super("Oracle");
        }

    }

    @Nested
    @Isolated
    @DisplayName("TLS Conscrypt")
    @DisabledOnOs(value = {OS.WINDOWS, OS.MAC}, architectures = "aarch64")
    class ConscryptJSSEAndStrategiesAlpnTest extends ClassicTLSIntegrationTest {

        public ConscryptJSSEAndStrategiesAlpnTest() throws Exception {
            super("Conscrypt");
        }

    }
}

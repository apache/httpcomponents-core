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
package org.apache.hc.core5.testing;

import java.util.stream.Stream;

import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.util.ReflectionUtils;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.ParameterDeclarations;

public class SupportedTLSProtocolProvider implements ArgumentsProvider {

    int javaVere = ReflectionUtils.determineJRELevel();

    @Override
    public Stream<? extends Arguments> provideArguments(
            final ParameterDeclarations parameters,
            final ExtensionContext context) {
        if (javaVere >= 11) {
            return Stream.of(Arguments.of(TLS.V_1_2), Arguments.of(TLS.V_1_3));
        } else {
            return Stream.of(Arguments.of(TLS.V_1_2));
        }
    }
}

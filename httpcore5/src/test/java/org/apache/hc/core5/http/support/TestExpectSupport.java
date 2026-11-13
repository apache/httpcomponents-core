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

package org.apache.hc.core5.http.support;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.impl.BasicEntityDetails;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestExpectSupport {

    @Test
    void testExpectParsingBasics() throws Exception {
        Assertions.assertThat(ExpectSupport.parse(
                        BasicRequestBuilder.post()
                                .addHeader("Expect", "100-continue")
                                .build(),
                new BasicEntityDetails(100, ContentType.TEXT_PLAIN))).isEqualTo(Expectation.CONTINUE);
    }

    @Test
    void testExpectParsingTolerateEmptyTokens() throws Exception {
        Assertions.assertThat(ExpectSupport.parse(
                        BasicRequestBuilder.post()
                                .addHeader("Expect", ",,,")
                                .addHeader("Expect", ",100-continue")
                                .addHeader("Expect", ",,,")
                                .build(),
                new BasicEntityDetails(100, ContentType.TEXT_PLAIN))).isEqualTo(Expectation.CONTINUE);
    }

    @Test
    void testExpectParsingMissingEntity() {
        Assertions.assertThatThrownBy(() -> ExpectSupport.parse(
                        BasicRequestBuilder.post()
                                .addHeader("Expect", "100-continue")
                                .build(),
                null)).isInstanceOf(ProtocolException.class);
    }

    @Test
    void testExpectParsingUnknownExpectation() throws Exception {
        Assertions.assertThat(ExpectSupport.parse(
                        BasicRequestBuilder.post()
                                .addHeader("Expect", "whatever")
                                .addHeader("Expect", "100-continue")
                                .build(),
                new BasicEntityDetails(100, ContentType.TEXT_PLAIN))).isEqualTo(Expectation.UNKNOWN);
    }

    @Test
    void testExpectParsingUnknownExpectation2() throws Exception {
        Assertions.assertThat(ExpectSupport.parse(
                        BasicRequestBuilder.post()
                                .addHeader("Expect", "100-continue, whatever")
                                .build(),
                new BasicEntityDetails(100, ContentType.TEXT_PLAIN))).isEqualTo(Expectation.UNKNOWN);
    }

    @Test
    void testExpectParsingNoExpectation() throws Exception {
        Assertions.assertThat(ExpectSupport.parse(
                        BasicRequestBuilder.post()
                                .build(),
                new BasicEntityDetails(100, ContentType.TEXT_PLAIN))).isNull();
    }

    @Test
    void testExpectParsingIgnoreHTTP10() throws Exception {
        Assertions.assertThat(ExpectSupport.parse(
                BasicRequestBuilder.post()
                        .setVersion(HttpVersion.HTTP_1_0)
                        .addHeader("Expect", "100-continue")
                        .build(),
                new BasicEntityDetails(100, ContentType.TEXT_PLAIN))).isNull();
    }

}

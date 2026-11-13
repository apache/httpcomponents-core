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

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.MessageHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class IncomingEntityDetailsTest {

    @Test
    void getContentLengthEmpty() {
        final MessageHeaders messageHeaders = new HeaderGroup();
        final IncomingEntityDetails incomingEntityDetails = new IncomingEntityDetails(messageHeaders);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(incomingEntityDetails.getContentLength()).isEqualTo(-1);
            softly.assertThat(incomingEntityDetails.getContentType()).isNull();
            softly.assertThat(incomingEntityDetails.getContentEncoding()).isNull();
            softly.assertThat(incomingEntityDetails.getTrailerNames()).isEmpty();
        });
    }

    @Test
    void messageHeadersNull() {
        Assertions.assertThatThrownBy(() ->
                        new IncomingEntityDetails(null))
                .withFailMessage("Message Header Null").isInstanceOf(NullPointerException.class);
    }

    @Test
    void getContentLength() {
        final MessageHeaders messageHeaders = new HeaderGroup();
        final HeaderGroup headerGroup = new HeaderGroup();
        final Header header = new BasicHeader("name", "value");
        headerGroup.addHeader(header);
        final IncomingEntityDetails incomingEntityDetails = new IncomingEntityDetails(messageHeaders);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(incomingEntityDetails.getContentLength()).isEqualTo(-1);
            softly.assertThat(incomingEntityDetails.isChunked()).isTrue();
        });
    }

    @Test
    void getTrailerNames() {
        final HeaderGroup messageHeaders = new HeaderGroup();
        final Header header = new BasicHeader(HttpHeaders.TRAILER, "a, b, c, c");
        messageHeaders.setHeaders(header);
        final IncomingEntityDetails incomingEntityDetails = new IncomingEntityDetails(messageHeaders);
        final Set<String> incomingSet = incomingEntityDetails.getTrailerNames();
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(incomingSet).isNotEmpty();
            softly.assertThat(incomingSet).containsAll(Stream.of("a", "b", "c")
                    .collect(Collectors.toCollection(HashSet::new)));
        });
    }

}

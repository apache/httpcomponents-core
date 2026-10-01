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
package org.apache.hc.core5.http2.impl.nio;

import java.util.Arrays;
import java.util.Collections;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.MisdirectedRequestException;
import org.apache.hc.core5.http2.H2ConnectionException;
import org.apache.hc.core5.http2.H2Error;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestH2OriginSet {

    private static final HttpHost INITIAL = new HttpHost("https", "primary.example", 443);
    private static final HttpHost ALT = new HttpHost("https", "assets.example", 443);

    @Test
    void startsUninitializedAndAllowsAnyOrigin() {
        final H2OriginSet originSet = new H2OriginSet(INITIAL, 10);
        Assertions.assertFalse(originSet.isInitialized());
        Assertions.assertTrue(originSet.isAllowed(ALT));
        Assertions.assertTrue(originSet.snapshot().isEmpty());
    }

    @Test
    void firstFrameAddsInitialOriginAndLaterFramesMerge() throws Exception {
        final H2OriginSet originSet = new H2OriginSet(INITIAL, 10);
        originSet.update(Collections.emptyList());
        originSet.update(Collections.singleton(ALT));

        Assertions.assertTrue(originSet.isInitialized());
        Assertions.assertEquals(2, originSet.snapshot().size());
        Assertions.assertTrue(originSet.snapshot().contains(INITIAL));
        Assertions.assertTrue(originSet.snapshot().contains(ALT));
    }

    @Test
    void initializedSetRejectsAbsentOrigin() throws Exception {
        final H2OriginSet originSet = new H2OriginSet(INITIAL, 10);
        originSet.update(Collections.emptyList());
        Assertions.assertThrows(MisdirectedRequestException.class, () -> originSet.ensureAllowed(ALT));
    }

    @Test
    void removalLeavesSetInitialized() throws Exception {
        final H2OriginSet originSet = new H2OriginSet(INITIAL, 10);
        originSet.update(Collections.singleton(ALT));
        originSet.remove(ALT);
        Assertions.assertTrue(originSet.isInitialized());
        Assertions.assertFalse(originSet.snapshot().contains(ALT));
    }

    @Test
    void enforcesConfiguredResourceLimitAtomically() throws Exception {
        final H2OriginSet originSet = new H2OriginSet(INITIAL, 2);
        originSet.update(Collections.singleton(ALT));

        final H2ConnectionException ex = Assertions.assertThrows(H2ConnectionException.class, () ->
                originSet.update(Arrays.asList(
                        new HttpHost("https", "third.example", 443),
                        new HttpHost("https", "fourth.example", 443))));

        Assertions.assertEquals(H2Error.ENHANCE_YOUR_CALM.getCode(), ex.getCode());
        Assertions.assertEquals(2, originSet.snapshot().size());
    }
}

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

package org.apache.hc.core5.http.protocol;

import java.util.LinkedList;
import java.util.List;

import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestChainBuilder {

    @Test
    public void testBuildChain() throws Exception {
        final ChainBuilder<HttpRequestInterceptor> cb = new ChainBuilder<>();
        final HttpRequestInterceptor i1 = RequestContent.INSTANCE;
        final HttpRequestInterceptor i2 = RequestTargetHost.INSTANCE;
        final HttpRequestInterceptor i3 = RequestConnControl.INSTANCE;
        final HttpRequestInterceptor i4 = RequestUserAgent.INSTANCE;
        final HttpRequestInterceptor i5 = RequestExpectContinue.INSTANCE;
        cb.addFirst(i1);
        cb.addAllFirst(i2, i3);
        cb.addFirst(null);
        cb.addAllFirst((List<HttpRequestInterceptor>) null);
        cb.addLast(i4);
        cb.addLast(null);
        cb.addAllLast(i5);
        cb.addAllLast((List<HttpRequestInterceptor>) null);
        cb.addFirst(i1);
        cb.addAllLast(i3, i4, i5);
        final LinkedList<HttpRequestInterceptor> list = cb.build();
        Assertions.assertNotNull(list);
        Assertions.assertEquals(5, list.size());
        Assertions.assertSame(i1, list.get(0));
        Assertions.assertSame(i2, list.get(1));
        Assertions.assertSame(i3, list.get(2));
        Assertions.assertSame(i4, list.get(3));
        Assertions.assertSame(i5, list.get(4));
    }

}

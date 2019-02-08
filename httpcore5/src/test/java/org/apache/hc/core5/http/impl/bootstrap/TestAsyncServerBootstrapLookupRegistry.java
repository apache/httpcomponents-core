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
package org.apache.hc.core5.http.impl.bootstrap;

import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.protocol.LookupRegistry;
import org.junit.Test;

public class TestAsyncServerBootstrapLookupRegistry {

    @Test
    public void testCreateNullLookupRegistry() {
        AsyncServerBootstrap.bootstrap().setLookupRegistry(null).create();
    }

    @Test
    public void testCreateCustomLookupRegistry() {
        AsyncServerBootstrap.bootstrap().setLookupRegistry(new LookupRegistry<AsyncServerExchangeHandler>() {

            @Override
            public void register(final String pattern, final AsyncServerExchangeHandler obj) {
                // noop
            }

            @Override
            public AsyncServerExchangeHandler lookup(final String value) {
                // noop
                return null;
            }

            @Override
            public void unregister(final String pattern) {
                // noop
            }
        }).create();
    }
}

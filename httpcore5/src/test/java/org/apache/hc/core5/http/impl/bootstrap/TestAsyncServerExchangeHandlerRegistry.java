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

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.support.ImmediateResponseExchangeHandler;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestAsyncServerExchangeHandlerRegistry {
    private AsyncServerExchangeHandlerRegistry handlerRegistry;
    private AsyncServerExchangeHandler handler;
    private Supplier<AsyncServerExchangeHandler> supplier;

    @Before
    public void setUp() {
        handlerRegistry = new AsyncServerExchangeHandlerRegistry("localhost");
        handler = new ImmediateResponseExchangeHandler(HttpStatus.SC_OK, "Hello world");
        supplier = new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return handler;
            }
        };
    }

    @Test
    public void testCreate() throws Exception {
        handlerRegistry.register(null, "/test*", supplier);
        Assert.assertNotEquals(handler, handlerRegistry.create(new BasicHttpRequest("GET", "/")));
        Assert.assertNotEquals(handler, handlerRegistry.create(new BasicHttpRequest("GET", "/abc")));
        Assert.assertEquals(handler, handlerRegistry.create(new BasicHttpRequest("GET", "/test")));
        Assert.assertEquals(handler, handlerRegistry.create(new BasicHttpRequest("GET", "/testabc")));
    }

    @Test
    public void testCreateQuery() throws Exception {
        handlerRegistry.register(null, "/test*", supplier);
        Assert.assertEquals(handler, handlerRegistry.create(new BasicHttpRequest("GET", "/test?test=a")));
        Assert.assertNotEquals(handler, handlerRegistry.create(new BasicHttpRequest("GET", "/tes?test=a")));
    }

}

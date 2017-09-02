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

package org.apache.http.nio.protocol;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicRequestLine;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;

public class TestBasicAsyncRequestHandler {

    private HttpRequestHandler requestHandler;
    private BasicAsyncRequestHandler asyncRequestHandler;
    private HttpContext context;
    private HttpRequest request;
    private HttpResponse response;
    private HttpAsyncExchange httpexchange;

    @Before
    public void setUp() throws Exception {
        this.requestHandler = Mockito.mock(HttpRequestHandler.class);
        this.asyncRequestHandler = new BasicAsyncRequestHandler(this.requestHandler);
        this.context = new BasicHttpContext();
        this.request = Mockito.mock(HttpRequest.class);
        this.response = Mockito.mock(HttpResponse.class);
        this.httpexchange = Mockito.mock(HttpAsyncExchange.class);
        Mockito.when(this.httpexchange.getRequest()).thenReturn(this.request);
        Mockito.when(this.httpexchange.getResponse()).thenReturn(this.response);
    }

    @After
    public void tearDown() throws Exception {
    }


    @Test
    public void testInvalidConstruction() throws Exception {
        try {
            new BasicAsyncRequestHandler(null);
            Assert.fail("IllegalArgumentException expected");
        } catch (final IllegalArgumentException ex) {
        }
    }

    @Test
    public void testProcessRequest() throws Exception {
        final HttpAsyncRequestConsumer<HttpRequest> requestConsumer = this.asyncRequestHandler.processRequest(
                this.request, this.context);
        Assert.assertTrue(requestConsumer instanceof BasicAsyncRequestConsumer);
    }

    @Test
    public void testHandleRequest() throws Exception {
        Mockito.when(this.request.getRequestLine()).thenReturn(new BasicRequestLine("GET", "/", HttpVersion.HTTP_1_0));

        this.asyncRequestHandler.handle(this.request, this.httpexchange, this.context);

        Mockito.verify(this.requestHandler).handle(
                Matchers.eq(this.request), Matchers.eq(this.response), Matchers.eq(this.context));
        Mockito.verify(this.httpexchange).submitResponse();
    }

}

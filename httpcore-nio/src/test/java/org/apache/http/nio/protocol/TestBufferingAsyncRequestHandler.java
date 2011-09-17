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

import junit.framework.Assert;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.concurrent.Cancellable;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.message.BasicRequestLine;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class TestBufferingAsyncRequestHandler {

    private HttpRequestHandler requestHandler;
    private BufferingAsyncRequestHandler asyncRequestHandler;
    private HttpContext context;
    private HttpRequest request;
    private HttpAsyncResponseTrigger trigger;

    @Before
    public void setUp() throws Exception {
        this.requestHandler = Mockito.mock(HttpRequestHandler.class);
        this.asyncRequestHandler = new BufferingAsyncRequestHandler(this.requestHandler,
                new DefaultHttpResponseFactory(), new HeapByteBufferAllocator());
        this.context = new BasicHttpContext();
        this.request = Mockito.mock(HttpRequest.class);
        this.trigger = Mockito.mock(HttpAsyncResponseTrigger.class);
    }

    @After
    public void tearDown() throws Exception {
    }


    @Test
    public void testInvalidConstruction() throws Exception {
        try {
            new BufferingAsyncRequestHandler(null, new DefaultHttpResponseFactory(), new HeapByteBufferAllocator());
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }
        try {
            new BufferingAsyncRequestHandler(this.requestHandler, null, new HeapByteBufferAllocator());
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }
        try {
            new BufferingAsyncRequestHandler(this.requestHandler, new DefaultHttpResponseFactory(), null);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }
    }

    @Test
    public void testProcessRequest() throws Exception {
        HttpAsyncRequestConsumer<HttpRequest> requestConsumer = this.asyncRequestHandler.processRequest(
                this.request, this.context);
        Assert.assertTrue(requestConsumer instanceof BasicAsyncRequestConsumer);
    }

    @Test
    public void testHandleRequest() throws Exception {
        Mockito.when(this.request.getRequestLine()).thenReturn(new BasicRequestLine("GET", "/", HttpVersion.HTTP_1_0));

        Cancellable cancellable = this.asyncRequestHandler.handle(this.request, this.trigger, this.context);

        Assert.assertNull(cancellable);
        ArgumentCaptor<HttpResponse> argCaptor = ArgumentCaptor.forClass(HttpResponse.class);
        Mockito.verify(this.requestHandler).handle(
                Mockito.eq(this.request), argCaptor.capture(), Mockito.eq(this.context));
        HttpResponse response = argCaptor.getValue();
        Assert.assertEquals(HttpVersion.HTTP_1_0, response.getProtocolVersion());
        Mockito.verify(this.trigger).submitResponse(Mockito.any(BasicAsyncResponseProducer.class));
    }

    @Test
    public void testHandleRequestUnsupportedVersion() throws Exception {
        Mockito.when(this.request.getRequestLine()).thenReturn(new BasicRequestLine("GET", "/", new HttpVersion(234, 456)));

        Cancellable cancellable = this.asyncRequestHandler.handle(this.request, this.trigger, this.context);

        Assert.assertNull(cancellable);
        ArgumentCaptor<HttpResponse> argCaptor = ArgumentCaptor.forClass(HttpResponse.class);
        Mockito.verify(this.requestHandler).handle(
                Mockito.eq(this.request), argCaptor.capture(), Mockito.eq(this.context));
        HttpResponse response = argCaptor.getValue();
        Assert.assertEquals(HttpVersion.HTTP_1_1, response.getProtocolVersion());
        Mockito.verify(this.trigger).submitResponse(Mockito.any(BasicAsyncResponseProducer.class));
    }

}

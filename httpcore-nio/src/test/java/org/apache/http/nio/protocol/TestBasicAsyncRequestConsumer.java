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

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class TestBasicAsyncRequestConsumer {

    private BasicAsyncRequestConsumer consumer;
    @Mock private HttpEntityEnclosingRequest request;
    @Mock private HttpContext context;
    @Mock private ContentDecoder decoder;
    @Mock private IOControl ioctrl;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        consumer = Mockito.spy(new BasicAsyncRequestConsumer());
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testRequestProcessing() throws Exception {
        when(request.getEntity()).thenReturn(new StringEntity("stuff"));

        consumer.requestReceived(request);
        consumer.consumeContent(decoder, ioctrl);
        consumer.requestCompleted(context);

        verify(consumer).releaseResources();
        verify(consumer).buildResult(context);
        Assert.assertTrue(consumer.isDone());
        Assert.assertSame(request, consumer.getResult());

        consumer.requestCompleted(context);
        verify(consumer, times(1)).releaseResources();
        verify(consumer, times(1)).buildResult(context);
    }

    @Test
    public void testResponseProcessingWithException() throws Exception {
        when(request.getEntity()).thenReturn(new StringEntity("stuff"));
        final RuntimeException ooopsie = new RuntimeException();
        when(consumer.buildResult(context)).thenThrow(ooopsie);

        consumer.requestReceived(request);
        consumer.consumeContent(decoder, ioctrl);
        consumer.requestCompleted(context);

        verify(consumer).releaseResources();
        Assert.assertTrue(consumer.isDone());
        Assert.assertSame(ooopsie, consumer.getException());
    }

    @Test
    public void testClose() throws Exception {
        consumer.close();

        verify(consumer).releaseResources();
        Assert.assertTrue(consumer.isDone());

        consumer.close();

        verify(consumer, times(1)).releaseResources();
    }

}

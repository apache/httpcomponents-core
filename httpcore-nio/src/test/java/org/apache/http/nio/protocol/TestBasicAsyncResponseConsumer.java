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

import org.apache.http.HttpResponse;
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

public class TestBasicAsyncResponseConsumer {

    private BasicAsyncResponseConsumer consumer;
    @Mock private HttpResponse response;
    @Mock private HttpContext context;
    @Mock private ContentDecoder decoder;
    @Mock private IOControl ioctrl;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        consumer = Mockito.spy(new BasicAsyncResponseConsumer());
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testResponseProcessing() throws Exception {
        when(response.getEntity()).thenReturn(new StringEntity("stuff"));

        consumer.responseReceived(response);
        consumer.consumeContent(decoder, ioctrl);
        consumer.responseCompleted(context);

        verify(consumer).releaseResources();
        verify(consumer).buildResult(context);
        Assert.assertTrue(consumer.isDone());
        Assert.assertSame(response, consumer.getResult());

        consumer.responseCompleted(context);
        verify(consumer, times(1)).releaseResources();
        verify(consumer, times(1)).buildResult(context);
    }

    @Test
    public void testResponseProcessingWithException() throws Exception {
        when(response.getEntity()).thenReturn(new StringEntity("stuff"));
        final RuntimeException ooopsie = new RuntimeException();
        when(consumer.buildResult(context)).thenThrow(ooopsie);

        consumer.responseReceived(response);
        consumer.consumeContent(decoder, ioctrl);
        consumer.responseCompleted(context);

        verify(consumer).releaseResources();
        Assert.assertTrue(consumer.isDone());
        Assert.assertSame(ooopsie, consumer.getException());
    }

    @Test
    public void testCancel() throws Exception {
        Assert.assertTrue(consumer.cancel());

        verify(consumer).releaseResources();
        Assert.assertTrue(consumer.isDone());

        Assert.assertFalse(consumer.cancel());
        verify(consumer, times(1)).releaseResources();
    }

    @Test
    public void testFailed() throws Exception {
        final RuntimeException ooopsie = new RuntimeException();

        consumer.failed(ooopsie);

        verify(consumer).releaseResources();
        Assert.assertTrue(consumer.isDone());
        Assert.assertSame(ooopsie, consumer.getException());
    }

    @Test
    public void testFailedAfterDone() throws Exception {
        final RuntimeException ooopsie = new RuntimeException();

        consumer.cancel();
        consumer.failed(ooopsie);

        verify(consumer, times(1)).releaseResources();
        Assert.assertTrue(consumer.isDone());
        Assert.assertNull(consumer.getException());
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

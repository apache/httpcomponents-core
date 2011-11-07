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
import junit.framework.Assert;

import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.entity.HttpAsyncContentProducer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TestBasicAsyncRequestProducer {

    private BasicAsyncRequestProducer producer;
    private HttpHost target;
    @Mock private HttpAsyncContentProducer contentProducer;
    @Mock private HttpEntityEnclosingRequest request;
    @Mock private ContentEncoder encoder;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        target = new HttpHost("localhost");
        producer = new BasicAsyncRequestProducer(target, request, contentProducer);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullTarget3ArgConstructor() throws Exception {
        producer = new BasicAsyncRequestProducer(null, request, contentProducer);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullRequest3ArgConstructor() throws Exception {
        producer = new BasicAsyncRequestProducer(target, null, contentProducer);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullTarget2ArgConstructor() throws Exception {
        producer = new BasicAsyncRequestProducer(null, request);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullRequest2ArgConstructor() throws Exception {
        producer = new BasicAsyncRequestProducer(target, null);
    }

    @Test
    public void testGenerateRequest() {
        HttpRequest res = producer.generateRequest();

        Assert.assertSame(request, res);
    }

    @Test
    public void testGetTarget() {
        HttpHost res = producer.getTarget();

        Assert.assertSame(target, res);
    }

    @Test
    public void testProduceContentEncoderCompleted() throws Exception {
        when(encoder.isCompleted()).thenReturn(true);

        producer.produceContent(encoder,  null);

        verify(contentProducer, times(1)).close();
    }

    @Test
    public void testProduceContentEncoderNotCompleted() throws Exception {
        when(encoder.isCompleted()).thenReturn(false);

        producer.produceContent(encoder,  null);

        verify(contentProducer, times(0)).close();
    }

    @Test
    public void testResetRequest() throws Exception {
        producer.resetRequest();
        verify(contentProducer, times(1)).close();
    }

    @Test
    public void testClose() throws Exception {
        producer.close();
        verify(contentProducer, times(1)).close();
    }

}

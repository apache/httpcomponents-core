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
import static org.mockito.Mockito.never;
import junit.framework.Assert;

import org.apache.http.HttpResponse;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.entity.HttpAsyncContentProducer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TestBasicAsyncResponseProducer {

    private BasicAsyncResponseProducer producer;
    @Mock private HttpAsyncContentProducer contentProducer;
    @Mock private HttpResponse response;
    @Mock private ContentEncoder encoder;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        producer = new BasicAsyncResponseProducer(response, contentProducer);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNullTargetArgConstructor() throws Exception {
        producer = new BasicAsyncResponseProducer(null);
    }

    @Test
    public void testGenerateRequest() {
        HttpResponse res = producer.generateResponse();

        Assert.assertSame(response, res);
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

        verify(contentProducer, never()).close();
    }

    @Test
    public void testClose() throws Exception {
        producer.close();
        verify(contentProducer, times(1)).close();
    }

}

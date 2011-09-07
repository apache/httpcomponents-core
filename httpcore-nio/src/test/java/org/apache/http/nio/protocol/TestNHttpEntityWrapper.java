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

import java.io.ByteArrayInputStream;

import junit.framework.Assert;

import org.apache.http.HttpEntity;
import org.apache.http.nio.ContentEncoder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TestNHttpEntityWrapper {

    private NHttpEntityWrapper wrapper;
    @Mock private HttpEntity entity;
    private ByteArrayInputStream is;
    private byte[] buff = { 0x01, 0x02, 0x03 };
    @Mock private ContentEncoder encoder;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        is = new ByteArrayInputStream(buff);
        when(entity.getContent()).thenReturn(is);

        wrapper = new NHttpEntityWrapper(entity);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test(expected=UnsupportedOperationException.class)
    public void testGetContent() throws Exception {
        wrapper.getContent();
    }

    @Test
    public void testIsStreaming() {
        boolean res = wrapper.isStreaming();

        Assert.assertEquals(true, res);
    }

    @Test(expected=UnsupportedOperationException.class)
    public void testWriteTo() throws Exception {
        wrapper.writeTo(null);
    }

    @Test
    public void testProduceContentWithContent() throws Exception {
        wrapper.produceContent(encoder, null);
        wrapper.finish();

        verify(encoder, times(0)).complete();
    }

    @Test
    public void testProduceContentNoContent() throws Exception {
        is = new ByteArrayInputStream(new byte[] { });
        when(entity.getContent()).thenReturn(is);

        wrapper.produceContent(encoder, null);
        wrapper.finish();

        verify(encoder, times(1)).complete();
    }

}

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

package org.apache.http.entity;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link AbstractHttpEntity}.
 *
 */
public class TestAbstractHttpEntity {

    @Test
    public void testContentType() throws Exception {
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, "blah"));
        Assert.assertEquals(HTTP.CONTENT_TYPE, httpentity.getContentType().getName());
        Assert.assertEquals("blah", httpentity.getContentType().getValue());

        httpentity.setContentType("blah");
        Assert.assertEquals(HTTP.CONTENT_TYPE, httpentity.getContentType().getName());
        Assert.assertEquals("blah", httpentity.getContentType().getValue());

        httpentity.setContentType((Header)null);
        Assert.assertNull(httpentity.getContentType());
        httpentity.setContentType((String)null);
        Assert.assertNull(httpentity.getContentType());
    }

    @Test
    public void testContentEncoding() throws Exception {
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentEncoding(new BasicHeader(HTTP.CONTENT_ENCODING, "gzip"));
        Assert.assertEquals(HTTP.CONTENT_ENCODING, httpentity.getContentEncoding().getName());
        Assert.assertEquals("gzip", httpentity.getContentEncoding().getValue());

        httpentity.setContentEncoding("gzip");
        Assert.assertEquals(HTTP.CONTENT_ENCODING, httpentity.getContentEncoding().getName());
        Assert.assertEquals("gzip", httpentity.getContentEncoding().getValue());

        httpentity.setContentEncoding((Header)null);
        Assert.assertNull(httpentity.getContentEncoding());
        httpentity.setContentEncoding((String)null);
        Assert.assertNull(httpentity.getContentEncoding());
    }

    @Test
    public void testChunkingFlag() throws Exception {
        BasicHttpEntity httpentity = new BasicHttpEntity();
        Assert.assertFalse(httpentity.isChunked());
        httpentity.setChunked(true);
        Assert.assertTrue(httpentity.isChunked());
    }

}

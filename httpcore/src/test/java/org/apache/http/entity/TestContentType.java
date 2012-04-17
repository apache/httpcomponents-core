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
import org.apache.http.ParseException;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHeader;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link ContentType}.
 *
 */
public class TestContentType {

    @Test
    public void testBasis() throws Exception {
        ContentType contentType = ContentType.create("text/plain", "US-ASCII");
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals("US-ASCII", contentType.getCharset().name());
        Assert.assertEquals("text/plain; charset=US-ASCII", contentType.toString());
    }

    @Test
    public void testLowCaseText() throws Exception {
        ContentType contentType = ContentType.create("Text/Plain", "ascii");
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals("US-ASCII", contentType.getCharset().name());
    }

    @Test
    public void testCreateInvalidInput() throws Exception {
        try {
            ContentType.create(null, (String) null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            ContentType.create("  ", (String) null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            ContentType.create("stuff;", (String) null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            ContentType.create("text/plain", ",");
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testParse() throws Exception {
        ContentType contentType = ContentType.parse("text/plain; charset=\"ascii\"");
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals("US-ASCII", contentType.getCharset().name());
        Assert.assertEquals("text/plain; charset=US-ASCII", contentType.toString());
    }

    @Test
    public void testParseInvalidInput() throws Exception {
        try {
            ContentType.parse(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            ContentType.parse(";");
            Assert.fail("ParseException should have been thrown");
        } catch (ParseException ex) {
            // expected
        }
    }

    @Test
    public void testExtractNullInput() throws Exception {
        Assert.assertNull(ContentType.get(null));
    }

    @Test
    public void testExtractNullContentType() throws Exception {
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType((Header)null);
        Assert.assertNull(ContentType.get(httpentity));
    }

    @Test
    public void testExtract() throws Exception {
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType(new BasicHeader("Content-Type", "text/plain; charset = UTF-8"));
        ContentType contentType = ContentType.get(httpentity);
        Assert.assertNotNull(contentType);
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals("UTF-8", contentType.getCharset().name());
    }

    @Test
    public void testExtractNoCharset() throws Exception {
        BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType(new BasicHeader("Content-Type", "text/plain; param=yadayada"));
        ContentType contentType = ContentType.get(httpentity);
        Assert.assertNotNull(contentType);
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertNull(contentType.getCharset());
    }

}

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

package org.apache.hc.core5.http;

import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link ContentType}.
 *
 */
public class TestContentType {

    @Test
    public void testBasis() throws Exception {
        final ContentType contentType = ContentType.create("text/plain", "US-ASCII");
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals(StandardCharsets.US_ASCII, contentType.getCharset());
        Assert.assertEquals("text/plain; charset=US-ASCII", contentType.toString());
    }

    @Test
    public void testWithCharset() throws Exception {
        ContentType contentType = ContentType.create("text/plain", "US-ASCII");
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals(StandardCharsets.US_ASCII, contentType.getCharset());
        Assert.assertEquals("text/plain; charset=US-ASCII", contentType.toString());
        contentType = contentType.withCharset(StandardCharsets.UTF_8);
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals("UTF-8", contentType.getCharset().name());
        Assert.assertEquals("text/plain; charset=UTF-8", contentType.toString());
    }

    @Test
    public void testWithCharsetString() throws Exception {
        ContentType contentType = ContentType.create("text/plain", "US-ASCII");
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals(StandardCharsets.US_ASCII, contentType.getCharset());
        Assert.assertEquals("text/plain; charset=US-ASCII", contentType.toString());
        contentType = contentType.withCharset(StandardCharsets.UTF_8);
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals(StandardCharsets.UTF_8, contentType.getCharset());
        Assert.assertEquals("text/plain; charset=UTF-8", contentType.toString());
    }

    @Test
    public void testLowCaseText() throws Exception {
        final ContentType contentType = ContentType.create("Text/Plain", "ascii");
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals(StandardCharsets.US_ASCII, contentType.getCharset());
    }

    @Test
    public void testCreateInvalidInput() throws Exception {
        Assert.assertThrows(NullPointerException.class, () -> ContentType.create(null, (String) null));
        Assert.assertThrows(IllegalArgumentException.class, () -> ContentType.create("  ", (String) null));
        Assert.assertThrows(IllegalArgumentException.class, () -> ContentType.create("stuff;", (String) null));
        Assert.assertThrows(IllegalArgumentException.class, () -> ContentType.create("text/plain", ","));
    }

    @Test
    public void testParse() throws Exception {
        final ContentType contentType = ContentType.parse("text/plain; charset=\"ascii\"");
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals(StandardCharsets.US_ASCII, contentType.getCharset());
        Assert.assertEquals("text/plain; charset=ascii", contentType.toString());
    }

    @Test
    public void testParseMultiparam() throws Exception {
        final ContentType contentType = ContentType.parse("text/plain; charset=\"ascii\"; " +
                "p0 ; p1 = \"blah-blah\"  ; p2 = \" yada yada \" ");
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals(StandardCharsets.US_ASCII, contentType.getCharset());
        Assert.assertEquals("text/plain; charset=ascii; p0; p1=blah-blah; p2=\" yada yada \"",
                contentType.toString());
        Assert.assertNull(contentType.getParameter("p0"));
        Assert.assertEquals("blah-blah", contentType.getParameter("p1"));
        Assert.assertEquals(" yada yada ", contentType.getParameter("p2"));
        Assert.assertNull(contentType.getParameter("p3"));
    }

    @Test
    public void testParseEmptyCharset() throws Exception {
        final ContentType contentType = ContentType.parse("text/plain; charset=\" \"");
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertNull(contentType.getCharset());
    }

    @Test
    public void testParseEmptyValue() throws Exception {
        Assert.assertNull(ContentType.parse(null));
        Assert.assertNull(ContentType.parse(""));
        Assert.assertNull(ContentType.parse("   "));
        Assert.assertNull(ContentType.parse(";"));
        Assert.assertNull(ContentType.parse("="));
    }

    @Test
    public void testWithParams() throws Exception {
        ContentType contentType = ContentType.create("text/plain",
                new BasicNameValuePair("charset", "UTF-8"),
                new BasicNameValuePair("p", "this"),
                new BasicNameValuePair("p", "that"));
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals(StandardCharsets.UTF_8, contentType.getCharset());
        Assert.assertEquals("text/plain; charset=UTF-8; p=this; p=that", contentType.toString());

        contentType = contentType.withParameters(
                new BasicNameValuePair("charset", "ascii"),
                new BasicNameValuePair("p", "this and that"));
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals(StandardCharsets.US_ASCII, contentType.getCharset());
        Assert.assertEquals("text/plain; charset=ascii; p=\"this and that\"", contentType.toString());

        contentType = ContentType.create("text/blah").withParameters(
                new BasicNameValuePair("p", "blah"));
        Assert.assertEquals("text/blah", contentType.getMimeType());
        Assert.assertNull(contentType.getCharset());
        Assert.assertEquals("text/blah; p=blah", contentType.toString());

        contentType = ContentType.create("text/blah", StandardCharsets.ISO_8859_1).withParameters(
                new BasicNameValuePair("p", "blah"));
        Assert.assertEquals("text/blah", contentType.getMimeType());
        Assert.assertEquals(StandardCharsets.ISO_8859_1, contentType.getCharset());
        Assert.assertEquals("text/blah; charset=ISO-8859-1; p=blah", contentType.toString());
    }

}

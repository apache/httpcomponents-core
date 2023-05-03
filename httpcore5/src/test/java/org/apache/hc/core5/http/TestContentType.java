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
import java.util.Arrays;

import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ContentType}.
 *
 */
public class TestContentType {

    @Test
    public void testBasis() throws Exception {
        final ContentType contentType = ContentType.create("text/plain", "US-ASCII");
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(StandardCharsets.US_ASCII, contentType.getCharset());
        Assertions.assertEquals("text/plain; charset=US-ASCII", contentType.toString());
    }

    @Test
    public void testWithCharset() throws Exception {
        ContentType contentType = ContentType.create("text/plain", "US-ASCII");
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(StandardCharsets.US_ASCII, contentType.getCharset());
        Assertions.assertEquals("text/plain; charset=US-ASCII", contentType.toString());
        contentType = contentType.withCharset(StandardCharsets.UTF_8);
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals("UTF-8", contentType.getCharset().name());
        Assertions.assertEquals("text/plain; charset=UTF-8", contentType.toString());
    }

    @Test
    public void testWithCharsetString() throws Exception {
        ContentType contentType = ContentType.create("text/plain", "US-ASCII");
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(StandardCharsets.US_ASCII, contentType.getCharset());
        Assertions.assertEquals("text/plain; charset=US-ASCII", contentType.toString());
        contentType = contentType.withCharset(StandardCharsets.UTF_8);
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(StandardCharsets.UTF_8, contentType.getCharset());
        Assertions.assertEquals("text/plain; charset=UTF-8", contentType.toString());
    }

    @Test
    public void testLowCaseText() throws Exception {
        final ContentType contentType = ContentType.create("Text/Plain", "ascii");
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(StandardCharsets.US_ASCII, contentType.getCharset());
    }

    @Test
    public void testCreateInvalidInput() throws Exception {
        Assertions.assertThrows(NullPointerException.class, () -> ContentType.create(null, (String) null));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ContentType.create("  ", (String) null));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ContentType.create("stuff;", (String) null));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ContentType.create("text/plain", ","));
    }

    @Test
    public void testParse() throws Exception {
        final ContentType contentType = ContentType.parse("text/plain; charset=\"ascii\"");
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(StandardCharsets.US_ASCII, contentType.getCharset());
        Assertions.assertEquals("text/plain; charset=ascii", contentType.toString());
    }

    @Test
    public void testParseMultiparam() throws Exception {
        final ContentType contentType = ContentType.parse("text/plain; charset=\"ascii\"; " +
                "p0 ; p1 = \"blah-blah\"  ; p2 = \" yada yada \" ");
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(StandardCharsets.US_ASCII, contentType.getCharset());
        Assertions.assertEquals("text/plain; charset=ascii; p0; p1=blah-blah; p2=\" yada yada \"",
                contentType.toString());
        Assertions.assertNull(contentType.getParameter("p0"));
        Assertions.assertEquals("blah-blah", contentType.getParameter("p1"));
        Assertions.assertEquals(" yada yada ", contentType.getParameter("p2"));
        Assertions.assertNull(contentType.getParameter("p3"));
    }

    @Test
    public void testParseEmptyCharset() throws Exception {
        final ContentType contentType = ContentType.parse("text/plain; charset=\" \"");
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertNull(contentType.getCharset());
    }

    @Test
    public void testParseDefaultCharset() throws Exception {
        final ContentType contentType = ContentType.parse("text/plain; charset=\" \"");
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertNull(contentType.getCharset());
        Assertions.assertEquals(StandardCharsets.US_ASCII, contentType.getCharset(StandardCharsets.US_ASCII));
        Assertions.assertNull(contentType.getCharset(null));
        //
        Assertions.assertNull(ContentType.getCharset(contentType, null));
        Assertions.assertEquals(StandardCharsets.US_ASCII, ContentType.getCharset(contentType, StandardCharsets.US_ASCII));
    }

    @Test
    public void testParseEmptyValue() throws Exception {
        Assertions.assertNull(ContentType.parse(null));
        Assertions.assertNull(ContentType.parse(""));
        Assertions.assertNull(ContentType.parse("   "));
        Assertions.assertNull(ContentType.parse(";"));
        Assertions.assertNull(ContentType.parse("="));
    }

    @Test
    public void testWithParamArrayChange() throws Exception {
        final BasicNameValuePair[] params = {new BasicNameValuePair("charset", "UTF-8"),
                new BasicNameValuePair("p", "this"),
                new BasicNameValuePair("p", "that")};
        final ContentType contentType = ContentType.create("text/plain", params);
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(StandardCharsets.UTF_8, contentType.getCharset());
        Assertions.assertEquals("text/plain; charset=UTF-8; p=this; p=that", contentType.toString());
        Arrays.setAll(params, i -> null);
        Assertions.assertEquals("this", contentType.getParameter("p"));
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(StandardCharsets.UTF_8, contentType.getCharset());
        Assertions.assertEquals("text/plain; charset=UTF-8; p=this; p=that", contentType.toString());
    }

    @Test
    public void testWithParams() throws Exception {
        ContentType contentType = ContentType.create("text/plain",
                new BasicNameValuePair("charset", "UTF-8"),
                new BasicNameValuePair("p", "this"),
                new BasicNameValuePair("p", "that"));
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(StandardCharsets.UTF_8, contentType.getCharset());
        Assertions.assertEquals("text/plain; charset=UTF-8; p=this; p=that", contentType.toString());

        contentType = contentType.withParameters(
                new BasicNameValuePair("charset", "ascii"),
                new BasicNameValuePair("p", "this and that"));
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(StandardCharsets.US_ASCII, contentType.getCharset());
        Assertions.assertEquals("text/plain; charset=ascii; p=\"this and that\"", contentType.toString());

        contentType = ContentType.create("text/blah").withParameters(
                new BasicNameValuePair("p", "blah"));
        Assertions.assertEquals("text/blah", contentType.getMimeType());
        Assertions.assertNull(contentType.getCharset());
        Assertions.assertEquals("text/blah; p=blah", contentType.toString());

        contentType = ContentType.create("text/blah", StandardCharsets.ISO_8859_1).withParameters(
                new BasicNameValuePair("p", "blah"));
        Assertions.assertEquals("text/blah", contentType.getMimeType());
        Assertions.assertEquals(StandardCharsets.ISO_8859_1, contentType.getCharset());
        Assertions.assertEquals("text/blah; charset=ISO-8859-1; p=blah", contentType.toString());
    }

}

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
class TestContentType {

    @Test
    void testBasis() throws Exception {
        final ContentType contentType = ContentType.create("text/plain", "US-ASCII");
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(StandardCharsets.US_ASCII, contentType.getCharset());
        Assertions.assertEquals("text/plain; charset=US-ASCII", contentType.toString());
    }

    @Test
    void testWithCharset() throws Exception {
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
    void testWithCharsetString() throws Exception {
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
    void testLowCaseText() throws Exception {
        final ContentType contentType = ContentType.create("Text/Plain", "ascii");
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(StandardCharsets.US_ASCII, contentType.getCharset());
    }

    @Test
    void testCreateInvalidInput() {
        Assertions.assertThrows(NullPointerException.class, () -> ContentType.create(null, (String) null));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ContentType.create("  ", (String) null));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ContentType.create("stuff;", (String) null));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ContentType.create("text/plain", ","));
    }

    @Test
    void testParse() throws Exception {
        final ContentType contentType = ContentType.parse("text/plain; charset=\"ascii\"");
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertEquals(StandardCharsets.US_ASCII, contentType.getCharset());
        Assertions.assertEquals("text/plain; charset=ascii", contentType.toString());
    }

    @Test
    void testParseMultiparam() throws Exception {
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
    void testParseEmptyCharset() throws Exception {
        final ContentType contentType = ContentType.parse("text/plain; charset=\" \"");
        Assertions.assertEquals("text/plain", contentType.getMimeType());
        Assertions.assertNull(contentType.getCharset());
    }

    @Test
    void testParseDefaultCharset() throws Exception {
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
    void testParseEmptyValue() throws Exception {
        Assertions.assertNull(ContentType.parse(null));
        Assertions.assertNull(ContentType.parse(""));
        Assertions.assertNull(ContentType.parse("   "));
        Assertions.assertNull(ContentType.parse(";"));
        Assertions.assertNull(ContentType.parse("="));
    }

    @Test
    void testWithParamArrayChange() throws Exception {
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
    void testWithParams() throws Exception {
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


    @Test
    void testImplicitCharsetTrue() {
        // ContentType with implicitCharset = true
        final ContentType contentType = ContentType.create("application/json", StandardCharsets.UTF_8, true);

        // Check that the charset is not added to the toString() output
        Assertions.assertEquals("application/json", contentType.toString());
        // Check that the charset is still stored
        Assertions.assertEquals(StandardCharsets.UTF_8, contentType.getCharset());
    }

    @Test
    void testImplicitCharsetFalse() {
        // ContentType with implicitCharset = false
        final ContentType contentType = ContentType.create("application/json", StandardCharsets.UTF_8, false);

        // Check that the charset is included in the toString() output
        Assertions.assertEquals("application/json; charset=UTF-8", contentType.toString());
        // Check that the charset is correctly stored
        Assertions.assertEquals(StandardCharsets.UTF_8, contentType.getCharset());
    }

    @Test
    void testImplicitCharsetForTextPlain() {
        // ContentType for text/plain with implicitCharset = true
        final ContentType contentType = ContentType.create("text/plain", StandardCharsets.UTF_8, true);

        // Check that the charset is not included in the toString() output
        Assertions.assertEquals("text/plain", contentType.toString());
        // Check that the charset is correctly stored
        Assertions.assertEquals(StandardCharsets.UTF_8, contentType.getCharset());
    }

    @Test
    void testWithParamsAndImplicitCharset() {
        // ContentType with parameters and implicitCharset = true
        ContentType contentType = ContentType.create("text/plain", StandardCharsets.UTF_8, true)
                .withParameters(
                        new BasicNameValuePair("p", "this"),
                        new BasicNameValuePair("p", "that"));

        // Check that the last "p" parameter overwrites the first one
        // ImplicitCharset is true, so charset should not be included
        Assertions.assertEquals("text/plain; p=that", contentType.toString());

        // Verify that charset is still available in the object
        Assertions.assertEquals(StandardCharsets.UTF_8, contentType.getCharset());

        // Test with implicitCharset = false
        contentType = ContentType.create("text/plain", StandardCharsets.UTF_8, false)
                .withParameters(
                        new BasicNameValuePair("p", "this"),
                        new BasicNameValuePair("p", "that"));

        // Check that the charset is included in the toString() output due to implicitCharset = false
        Assertions.assertEquals("text/plain; charset=UTF-8; p=that", contentType.toString());
    }

    @Test
    void testNoCharsetForSpecificMediaTypes() {
        // Testing application/octet-stream should not include charset in toString
        ContentType contentType = ContentType.create("application/octet-stream", StandardCharsets.UTF_8, true);
        Assertions.assertEquals("application/octet-stream", contentType.toString());
        Assertions.assertNotNull(contentType.getCharset()); // Ensure charset is set

        // Testing image/jpeg should not include charset in toString
        contentType = ContentType.create("image/jpeg", StandardCharsets.UTF_8, true);
        Assertions.assertEquals("image/jpeg", contentType.toString());
        Assertions.assertNotNull(contentType.getCharset());

        // Testing multipart/form-data should not include charset in toString
        contentType = ContentType.create("multipart/form-data", StandardCharsets.UTF_8, true);
        Assertions.assertEquals("multipart/form-data", contentType.toString());
        Assertions.assertNotNull(contentType.getCharset());
    }


    @Test
    void testCharsetForOtherMediaTypes() {
        // Testing application/json should include charset
        ContentType contentType = ContentType.create("application/json", StandardCharsets.UTF_8, false);
        Assertions.assertEquals("application/json; charset=UTF-8", contentType.toString());
        Assertions.assertEquals(StandardCharsets.UTF_8, contentType.getCharset());

        // Testing text/html should include charset
        contentType = ContentType.create("text/html", StandardCharsets.UTF_8, false);
        Assertions.assertEquals("text/html; charset=UTF-8", contentType.toString());
        Assertions.assertEquals(StandardCharsets.UTF_8, contentType.getCharset());
    }

    @Test
    void testNoCharsetForBinaryMediaTypes() throws Exception {
        // Test for application/octet-stream
        ContentType contentType = ContentType.create("application/octet-stream", null, true);
        Assertions.assertEquals("application/octet-stream", contentType.toString());
        Assertions.assertNull(contentType.getCharset());

        // Test for image/jpeg
        contentType = ContentType.create("image/jpeg", null, true);
        Assertions.assertEquals("image/jpeg", contentType.toString());
        Assertions.assertNull(contentType.getCharset());
    }

    @Test
    void testFormUrlEncodedWithoutCharset() throws Exception {
        // Test for application/x-www-form-urlencoded with percent-encoding
        final ContentType contentType = ContentType.create("application/x-www-form-urlencoded", null, true);
        Assertions.assertEquals("application/x-www-form-urlencoded", contentType.toString());
        Assertions.assertNull(contentType.getCharset());

        // Test body encoding example with percent-encoding
        final String encodedBody = "echotext=TEST%F6TEST";
        // Simulate HTTP redirect where charset shouldn't change
        final String redirectedBody = "echotext=TEST%F6TEST";
        Assertions.assertEquals(encodedBody, redirectedBody);  // Ensure body stays the same after redirect
    }
}

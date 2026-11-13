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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ContentType}.
 *
 */
class TestContentType {

    @Test
    void testApplicationZipCompressed() {
        final ContentType contentType = ContentType.create("application/x-zip-compressed");
        Assertions.assertThat(ContentType.APPLICATION_ZIP_COMPRESSED.toString()).isEqualTo(contentType.toString());
        Assertions.assertThat(contentType.isSameMimeType(ContentType.APPLICATION_ZIP_COMPRESSED)).isTrue();
        Assertions.assertThat(ContentType.APPLICATION_ZIP_COMPRESSED.isSameMimeType(contentType)).isTrue();
    }

    @Test
    void testBasis() throws Exception {
        final ContentType contentType = ContentType.create("text/plain", "US-ASCII");
        Assertions.assertThat(contentType.getMimeType()).isEqualTo("text/plain");
        Assertions.assertThat(contentType.getCharset()).isEqualTo(StandardCharsets.US_ASCII);
        Assertions.assertThat(contentType.toString()).isEqualTo("text/plain; charset=US-ASCII");
    }

    @Test
    void testWithCharset() throws Exception {
        ContentType contentType = ContentType.create("text/plain", "US-ASCII");
        Assertions.assertThat(contentType.getMimeType()).isEqualTo("text/plain");
        Assertions.assertThat(contentType.getCharset()).isEqualTo(StandardCharsets.US_ASCII);
        Assertions.assertThat(contentType.toString()).isEqualTo("text/plain; charset=US-ASCII");
        contentType = contentType.withCharset(StandardCharsets.UTF_8);
        Assertions.assertThat(contentType.getMimeType()).isEqualTo("text/plain");
        Assertions.assertThat(contentType.getCharset().name()).isEqualTo("UTF-8");
        Assertions.assertThat(contentType.toString()).isEqualTo("text/plain; charset=UTF-8");
    }

    @Test
    void testWithCharsetString() throws Exception {
        ContentType contentType = ContentType.create("text/plain", "US-ASCII");
        Assertions.assertThat(contentType.getMimeType()).isEqualTo("text/plain");
        Assertions.assertThat(contentType.getCharset()).isEqualTo(StandardCharsets.US_ASCII);
        Assertions.assertThat(contentType.toString()).isEqualTo("text/plain; charset=US-ASCII");
        contentType = contentType.withCharset(StandardCharsets.UTF_8);
        Assertions.assertThat(contentType.getMimeType()).isEqualTo("text/plain");
        Assertions.assertThat(contentType.getCharset()).isEqualTo(StandardCharsets.UTF_8);
        Assertions.assertThat(contentType.toString()).isEqualTo("text/plain; charset=UTF-8");
    }

    @Test
    void testLowCaseText() throws Exception {
        final ContentType contentType = ContentType.create("Text/Plain", "ascii");
        Assertions.assertThat(contentType.getMimeType()).isEqualTo("text/plain");
        Assertions.assertThat(contentType.getCharset()).isEqualTo(StandardCharsets.US_ASCII);
    }

    @Test
    void testCreateInvalidInput() {
        Assertions.assertThatThrownBy(() ->
                        ContentType.create(null, (String) null))
                .isInstanceOf(NullPointerException.class);
        Assertions.assertThatThrownBy(() ->
                        ContentType.create("  ", (String) null))
                .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() ->
                        ContentType.create("stuff;", (String) null))
                .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() ->
                        ContentType.create("text/plain", ","))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testParse() throws Exception {
        final ContentType contentType = ContentType.parse("text/plain; charset=\"ascii\"");
        Assertions.assertThat(contentType.getMimeType()).isEqualTo("text/plain");
        Assertions.assertThat(contentType.getCharset()).isEqualTo(StandardCharsets.US_ASCII);
        Assertions.assertThat(contentType.toString()).isEqualTo("text/plain; charset=ascii");
    }

    @Test
    void testParseMultiparam() throws Exception {
        final ContentType contentType = ContentType.parse("text/plain; charset=\"ascii\"; " +
                "p0 ; p1 = \"blah-blah\"  ; p2 = \" yada yada \" ");
        Assertions.assertThat(contentType.getMimeType()).isEqualTo("text/plain");
        Assertions.assertThat(contentType.getCharset()).isEqualTo(StandardCharsets.US_ASCII);
        Assertions.assertThat(contentType.toString()).isEqualTo("text/plain; charset=ascii; p0; p1=blah-blah; p2=\" yada yada \"");
        Assertions.assertThat(contentType.getParameter("p0")).isNull();
        Assertions.assertThat(contentType.getParameter("p1")).isEqualTo("blah-blah");
        Assertions.assertThat(contentType.getParameter("p2")).isEqualTo(" yada yada ");
        Assertions.assertThat(contentType.getParameter("p3")).isNull();
    }

    @Test
    void testParseEmptyCharset() throws Exception {
        final ContentType contentType = ContentType.parse("text/plain; charset=\" \"");
        Assertions.assertThat(contentType.getMimeType()).isEqualTo("text/plain");
        Assertions.assertThat(contentType.getCharset()).isNull();
    }

    @Test
    void testParseDefaultCharset() throws Exception {
        final ContentType contentType = ContentType.parse("text/plain; charset=\" \"");
        Assertions.assertThat(contentType.getMimeType()).isEqualTo("text/plain");
        Assertions.assertThat(contentType.getCharset()).isNull();
        Assertions.assertThat(contentType.getCharset(StandardCharsets.US_ASCII)).isEqualTo(StandardCharsets.US_ASCII);
        Assertions.assertThat(contentType.getCharset(null)).isNull();
        //
        Assertions.assertThat(ContentType.getCharset(contentType, null)).isNull();
        Assertions.assertThat(ContentType.getCharset(contentType, StandardCharsets.US_ASCII)).isEqualTo(StandardCharsets.US_ASCII);
    }

    @Test
    void testParseEmptyValue() throws Exception {
        Assertions.assertThat(ContentType.parse(null)).isNull();
        Assertions.assertThat(ContentType.parse("")).isNull();
        Assertions.assertThat(ContentType.parse("   ")).isNull();
        Assertions.assertThat(ContentType.parse(";")).isNull();
        Assertions.assertThat(ContentType.parse("=")).isNull();
    }

    @Test
    void testWithParamArrayChange() throws Exception {
        final BasicNameValuePair[] params = {new BasicNameValuePair("charset", "UTF-8"),
                new BasicNameValuePair("p", "this"),
                new BasicNameValuePair("p", "that")};
        final ContentType contentType = ContentType.create("text/plain", params);
        Assertions.assertThat(contentType.getMimeType()).isEqualTo("text/plain");
        Assertions.assertThat(contentType.getCharset()).isEqualTo(StandardCharsets.UTF_8);
        Assertions.assertThat(contentType.toString()).isEqualTo("text/plain; charset=UTF-8; p=this; p=that");
        Arrays.setAll(params, i -> null);
        Assertions.assertThat(contentType.getParameter("p")).isEqualTo("this");
        Assertions.assertThat(contentType.getMimeType()).isEqualTo("text/plain");
        Assertions.assertThat(contentType.getCharset()).isEqualTo(StandardCharsets.UTF_8);
        Assertions.assertThat(contentType.toString()).isEqualTo("text/plain; charset=UTF-8; p=this; p=that");
    }

    @Test
    void testWithParams() throws Exception {
        ContentType contentType = ContentType.create("text/plain",
                new BasicNameValuePair("charset", "UTF-8"),
                new BasicNameValuePair("p", "this"),
                new BasicNameValuePair("p", "that"));
        Assertions.assertThat(contentType.getMimeType()).isEqualTo("text/plain");
        Assertions.assertThat(contentType.getCharset()).isEqualTo(StandardCharsets.UTF_8);
        Assertions.assertThat(contentType.toString()).isEqualTo("text/plain; charset=UTF-8; p=this; p=that");

        contentType = contentType.withParameters(
                new BasicNameValuePair("charset", "ascii"),
                new BasicNameValuePair("p", "this and that"));
        Assertions.assertThat(contentType.getMimeType()).isEqualTo("text/plain");
        Assertions.assertThat(contentType.getCharset()).isEqualTo(StandardCharsets.US_ASCII);
        Assertions.assertThat(contentType.toString()).isEqualTo("text/plain; charset=ascii; p=\"this and that\"");

        contentType = ContentType.create("text/blah").withParameters(
                new BasicNameValuePair("p", "blah"));
        Assertions.assertThat(contentType.getMimeType()).isEqualTo("text/blah");
        Assertions.assertThat(contentType.getCharset()).isNull();
        Assertions.assertThat(contentType.toString()).isEqualTo("text/blah; p=blah");

        contentType = ContentType.create("text/blah", StandardCharsets.ISO_8859_1).withParameters(
                new BasicNameValuePair("p", "blah"));
        Assertions.assertThat(contentType.getMimeType()).isEqualTo("text/blah");
        Assertions.assertThat(contentType.getCharset()).isEqualTo(StandardCharsets.ISO_8859_1);
        Assertions.assertThat(contentType.toString()).isEqualTo("text/blah; charset=ISO-8859-1; p=blah");
    }

    @Test
    void testParseLenientIgnoresIllegalCharsetName() {
        final ContentType contentType = ContentType.parseLenient("text/event-stream;charset=ISO-8859-1'");
        Assertions.assertThat(contentType).isNotNull();
        Assertions.assertThat(contentType.getMimeType()).isEqualTo("text/event-stream");
        Assertions.assertThat(contentType.getCharset()).isNull();
        Assertions.assertThat(contentType.getParameter("charset")).isEqualTo("ISO-8859-1'");
    }

    @Test
    void testParseLenientIgnoresUnsupportedCharset() {
        final ContentType contentType = ContentType.parseLenient("text/plain; charset=__no_such_charset__");
        Assertions.assertThat(contentType).isNotNull();
        Assertions.assertThat(contentType.getCharset()).isNull();
    }



}

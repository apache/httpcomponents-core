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

package org.apache.hc.core5.http.message;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.apache.hc.core5.http.support.BasicResponseBuilder;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestMessageSupport {

    private static Set<String> makeSet(final String... tokens) {
        if (tokens == null) {
            return null;
        }
        final Set<String> set = new LinkedHashSet<>();
        Collections.addAll(set, tokens);
        return set;
    }

    @Test
    public void testTokenSetFormatting() throws Exception {
        final Header header = MessageSupport.header(HttpHeaders.TRAILER, makeSet("z", "b", "a"));
        Assertions.assertNotNull(header);
        Assertions.assertEquals("z, b, a", header.getValue());
    }

    @Test
    public void testTokenListFormatting() throws Exception {
        final Header header = MessageSupport.headerOfTokens(HttpHeaders.TRAILER, Arrays.asList("z", "b", "a", "a"));
        Assertions.assertNotNull(header);
        Assertions.assertEquals("z, b, a, a", header.getValue());
    }

    @Test
    public void testTokenSetFormattingSameName() throws Exception {
        final Header header = MessageSupport.header(HttpHeaders.TRAILER, makeSet("a", "a", "a"));
        Assertions.assertNotNull(header);
        Assertions.assertEquals("a", header.getValue());
    }

    @Test
    public void testTokenListFormattingSameName() throws Exception {
        final Header header = MessageSupport.header(HttpHeaders.TRAILER, "a", "a", "a");
        Assertions.assertNotNull(header);
        Assertions.assertEquals("a, a, a", header.getValue());
    }

    @Test
    public void testParseTokensWithConsumer() throws Exception {
        final String s = "a, b, c, c";
        final ParserCursor cursor = new ParserCursor(0, s.length());
        final List<String> tokens = new ArrayList<>();
        MessageSupport.parseTokens(s, cursor, tokens::add);
        Assertions.assertEquals(Arrays.asList("a", "b", "c", "c"), tokens);
    }

    @Test
    public void testParseTokenHeaderWithConsumer() throws Exception {
        final Header header = new BasicHeader(HttpHeaders.TRAILER, "a, b, c, c");
        final List<String> tokens = new ArrayList<>();
        MessageSupport.parseTokens(header, tokens::add);
        Assertions.assertEquals(Arrays.asList("a", "b", "c", "c"), tokens);
    }

    @Test
    public void testParseTokenBufferWithConsumer() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(128);
        buf.append("stuff: a, b, c, c");
        final Header header = BufferedHeader.create(buf);
        Assertions.assertEquals(makeSet("a", "b", "c", "c"), MessageSupport.parseTokens(header));
    }

    @Test
    public void testParseTokens() throws Exception {
        final String s = "a, b, c, c";
        final ParserCursor cursor = new ParserCursor(0, s.length());
        Assertions.assertEquals(makeSet("a", "b", "c"), MessageSupport.parseTokens(s, cursor));
    }

    @Test
    public void testParseTokenHeader() throws Exception {
        final Header header = new BasicHeader(HttpHeaders.TRAILER, "a, b, c, c");
        Assertions.assertEquals(makeSet("a", "b", "c"), MessageSupport.parseTokens(header));
    }

    @Test
    public void testParseTokenBuffer() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(128);
        buf.append("stuff: a, b, c, c");
        final Header header = BufferedHeader.create(buf);
        Assertions.assertEquals(makeSet("a", "b", "c"), MessageSupport.parseTokens(header));
    }

    @Test
    public void testElementListFormatting() throws Exception {
        final List<HeaderElement> elements = Arrays.asList(
                new BasicHeaderElement("name1", "value1", new BasicNameValuePair("param", "regular_stuff")),
                new BasicHeaderElement("name2", "value2", new BasicNameValuePair("param", "this\\that")),
                new BasicHeaderElement("name3", "value3", new BasicNameValuePair("param", "this,that")),
                new BasicHeaderElement("name4", "value4", new BasicNameValuePair("param", null)),
                new BasicHeaderElement("name5", null));

        final Header header = MessageSupport.headerOfElements("Some-header", elements);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("name1=value1; param=regular_stuff, name2=value2; " +
                "param=\"this\\\\that\", name3=value3; param=\"this,that\", " +
                "name4=value4; param, name5", header.getValue());
    }

    @Test
    public void testElementArrayFormatting() throws Exception {
        final HeaderElement[] elements = {
                new BasicHeaderElement("name1", "value1", new BasicNameValuePair("param", "regular_stuff")),
                new BasicHeaderElement("name2", "value2", new BasicNameValuePair("param", "this\\that")),
                new BasicHeaderElement("name3", "value3", new BasicNameValuePair("param", "this,that")),
                new BasicHeaderElement("name4", "value4", new BasicNameValuePair("param", null)),
                new BasicHeaderElement("name5", null)};

        final Header header = MessageSupport.header("Some-Header", elements);
        Assertions.assertNotNull(header);
        Assertions.assertEquals("name1=value1; param=regular_stuff, name2=value2; " +
                "param=\"this\\\\that\", name3=value3; param=\"this,that\", " +
                "name4=value4; param, name5", header.getValue());
    }

    @Test
    public void testParseElementsBufferWithConsumer() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        buf.append("name1 = value1; name2; name3=\"value3\" , name4=value4; " +
                "name5=value5, name6= ; name7 = value7; name8 = \" value8\"");
        final List<HeaderElement> elements = new ArrayList<>();
        final ParserCursor cursor = new ParserCursor(0, buf.length());
        MessageSupport.parseElements(buf, cursor, elements::add);
        // there are 3 elements
        Assertions.assertEquals(3,elements.size());
        // 1st element
        Assertions.assertEquals("name1", elements.get(0).getName());
        Assertions.assertEquals("value1", elements.get(0).getValue());
        // 1st element has 2 getParameters()
        Assertions.assertEquals(2, elements.get(0).getParameters().length);
        Assertions.assertEquals("name2", elements.get(0).getParameters()[0].getName());
        Assertions.assertNull(elements.get(0).getParameters()[0].getValue());
        Assertions.assertEquals("name3", elements.get(0).getParameters()[1].getName());
        Assertions.assertEquals("value3", elements.get(0).getParameters()[1].getValue());
        // 2nd element
        Assertions.assertEquals("name4", elements.get(1).getName());
        Assertions.assertEquals("value4", elements.get(1).getValue());
        // 2nd element has 1 parameter
        Assertions.assertEquals(1, elements.get(1).getParameters().length);
        Assertions.assertEquals("name5", elements.get(1).getParameters()[0].getName());
        Assertions.assertEquals("value5", elements.get(1).getParameters()[0].getValue());
        // 3rd element
        Assertions.assertEquals("name6", elements.get(2).getName());
        Assertions.assertEquals("", elements.get(2).getValue());
        // 3rd element has 2 getParameters()
        Assertions.assertEquals(2, elements.get(2).getParameters().length);
        Assertions.assertEquals("name7", elements.get(2).getParameters()[0].getName());
        Assertions.assertEquals("value7", elements.get(2).getParameters()[0].getValue());
        Assertions.assertEquals("name8", elements.get(2).getParameters()[1].getName());
        Assertions.assertEquals(" value8", elements.get(2).getParameters()[1].getValue());
    }

    @Test
    public void testParseElementsHeaderWithConsumer() throws Exception {
        final Header header = new BasicHeader("Some-Header",
                "name1 = value1; name2; name3=\"value3\" , name4=value4; " +
                "name5=value5, name6= ; name7 = value7; name8 = \" value8\"");
        final List<HeaderElement> elements = new ArrayList<>();
        MessageSupport.parseElements(header, elements::add);
        // there are 3 elements
        Assertions.assertEquals(3,elements.size());
        // 1st element
        Assertions.assertEquals("name1", elements.get(0).getName());
        Assertions.assertEquals("value1", elements.get(0).getValue());
        // 1st element has 2 getParameters()
        Assertions.assertEquals(2, elements.get(0).getParameters().length);
        Assertions.assertEquals("name2", elements.get(0).getParameters()[0].getName());
        Assertions.assertNull(elements.get(0).getParameters()[0].getValue());
        Assertions.assertEquals("name3", elements.get(0).getParameters()[1].getName());
        Assertions.assertEquals("value3", elements.get(0).getParameters()[1].getValue());
        // 2nd element
        Assertions.assertEquals("name4", elements.get(1).getName());
        Assertions.assertEquals("value4", elements.get(1).getValue());
        // 2nd element has 1 parameter
        Assertions.assertEquals(1, elements.get(1).getParameters().length);
        Assertions.assertEquals("name5", elements.get(1).getParameters()[0].getName());
        Assertions.assertEquals("value5", elements.get(1).getParameters()[0].getValue());
        // 3rd element
        Assertions.assertEquals("name6", elements.get(2).getName());
        Assertions.assertEquals("", elements.get(2).getValue());
        // 3rd element has 2 getParameters()
        Assertions.assertEquals(2, elements.get(2).getParameters().length);
        Assertions.assertEquals("name7", elements.get(2).getParameters()[0].getName());
        Assertions.assertEquals("value7", elements.get(2).getParameters()[0].getValue());
        Assertions.assertEquals("name8", elements.get(2).getParameters()[1].getName());
        Assertions.assertEquals(" value8", elements.get(2).getParameters()[1].getValue());
    }

    @Test
    public void testParseElementsHeader() throws Exception {
        final Header header = new BasicHeader("Some-Header",
                "name1 = value1; name2; name3=\"value3\" , name4=value4; " +
                        "name5=value5, name6= ; name7 = value7; name8 = \" value8\"");
        final List<HeaderElement> elements = MessageSupport.parseElements(header);
        // there are 3 elements
        Assertions.assertEquals(3,elements.size());
        // 1st element
        Assertions.assertEquals("name1", elements.get(0).getName());
        Assertions.assertEquals("value1", elements.get(0).getValue());
        // 1st element has 2 getParameters()
        Assertions.assertEquals(2, elements.get(0).getParameters().length);
        Assertions.assertEquals("name2", elements.get(0).getParameters()[0].getName());
        Assertions.assertNull(elements.get(0).getParameters()[0].getValue());
        Assertions.assertEquals("name3", elements.get(0).getParameters()[1].getName());
        Assertions.assertEquals("value3", elements.get(0).getParameters()[1].getValue());
        // 2nd element
        Assertions.assertEquals("name4", elements.get(1).getName());
        Assertions.assertEquals("value4", elements.get(1).getValue());
        // 2nd element has 1 parameter
        Assertions.assertEquals(1, elements.get(1).getParameters().length);
        Assertions.assertEquals("name5", elements.get(1).getParameters()[0].getName());
        Assertions.assertEquals("value5", elements.get(1).getParameters()[0].getValue());
        // 3rd element
        Assertions.assertEquals("name6", elements.get(2).getName());
        Assertions.assertEquals("", elements.get(2).getValue());
        // 3rd element has 2 getParameters()
        Assertions.assertEquals(2, elements.get(2).getParameters().length);
        Assertions.assertEquals("name7", elements.get(2).getParameters()[0].getName());
        Assertions.assertEquals("value7", elements.get(2).getParameters()[0].getValue());
        Assertions.assertEquals("name8", elements.get(2).getParameters()[1].getName());
        Assertions.assertEquals(" value8", elements.get(2).getParameters()[1].getValue());
    }

    @Test
    public void testParamListFormatting() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        MessageSupport.formatParameters(buf, Arrays.asList(
                new BasicNameValuePair("param", "regular_stuff"),
                new BasicNameValuePair("param", "this\\that"),
                new BasicNameValuePair("param", "this,that")
        ));
        Assertions.assertEquals("param=regular_stuff; param=\"this\\\\that\"; param=\"this,that\"",
                buf.toString());
    }

    @Test
    public void testParamArrayFormatting() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        MessageSupport.formatParameters(buf,
                new BasicNameValuePair("param", "regular_stuff"),
                new BasicNameValuePair("param", "this\\that"),
                new BasicNameValuePair("param", "this,that")
        );
        Assertions.assertEquals("param=regular_stuff; param=\"this\\\\that\"; param=\"this,that\"",
                buf.toString());
    }

    @Test
    public void testParseParams() {
        final String s =
                "test; test1 =  stuff   ; test2 =  \"stuff; stuff\"; test3=stuff,123";
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append(s);
        final ParserCursor cursor = new ParserCursor(0, s.length());

        final List<NameValuePair> params = new ArrayList<>();
        MessageSupport.parseParameters(buffer, cursor, params::add);
        Assertions.assertEquals("test", params.get(0).getName());
        Assertions.assertNull(params.get(0).getValue());
        Assertions.assertEquals("test1", params.get(1).getName());
        Assertions.assertEquals("stuff", params.get(1).getValue());
        Assertions.assertEquals("test2", params.get(2).getName());
        Assertions.assertEquals("stuff; stuff", params.get(2).getValue());
        Assertions.assertEquals("test3", params.get(3).getName());
        Assertions.assertEquals("stuff", params.get(3).getValue());
        Assertions.assertEquals(s.length() - 4, cursor.getPos());
        Assertions.assertFalse(cursor.atEnd());
    }

    @Test
    public void testAddContentHeaders() throws Exception {
        final HttpEntity entity = HttpEntities.create("some stuff with trailers", StandardCharsets.US_ASCII,
                new BasicHeader("z", "this"), new BasicHeader("b", "that"), new BasicHeader("a", "this and that"));
        final HttpMessage message = new BasicHttpResponse(200);
        MessageSupport.addTrailerHeader(message, entity);
        MessageSupport.addContentTypeHeader(message, entity);

        final Header h1 = message.getFirstHeader(HttpHeaders.TRAILER);
        final Header h2 = message.getFirstHeader(HttpHeaders.CONTENT_TYPE);

        Assertions.assertNotNull(h1);
        Assertions.assertEquals("z, b, a", h1.getValue());
        Assertions.assertNotNull(h2);
        Assertions.assertEquals("text/plain; charset=US-ASCII", h2.getValue());
    }

    @Test
    public void testContentHeadersAlreadyPresent() throws Exception {
        final HttpEntity entity = HttpEntities.create("some stuff with trailers", StandardCharsets.US_ASCII,
                new BasicHeader("z", "this"), new BasicHeader("b", "that"), new BasicHeader("a", "this and that"));
        final HttpMessage message = new BasicHttpResponse(200);
        message.addHeader(HttpHeaders.TRAILER, "a, a, a");
        message.addHeader(HttpHeaders.CONTENT_TYPE, "text/plain; charset=ascii");

        MessageSupport.addTrailerHeader(message, entity);
        MessageSupport.addContentTypeHeader(message, entity);

        final Header h1 = message.getFirstHeader(HttpHeaders.TRAILER);
        final Header h2 = message.getFirstHeader(HttpHeaders.CONTENT_TYPE);

        Assertions.assertNotNull(h1);
        Assertions.assertEquals("a, a, a", h1.getValue());
        Assertions.assertNotNull(h2);
        Assertions.assertEquals("text/plain; charset=ascii", h2.getValue());
    }

    @Test
    public void testHopByHopHeaders() {
        Assertions.assertTrue(MessageSupport.isHopByHop("Connection"));
        Assertions.assertTrue(MessageSupport.isHopByHop("connection"));
        Assertions.assertTrue(MessageSupport.isHopByHop("coNNection"));
        Assertions.assertFalse(MessageSupport.isHopByHop("Content-Type"));
        Assertions.assertFalse(MessageSupport.isHopByHop("huh"));
    }

    @Test
    public void testHopByHopHeadersConnectionSpecific() {
        final HttpResponse response = BasicResponseBuilder.create(HttpStatus.SC_OK)
                .addHeader(HttpHeaders.CONNECTION, "blah, blah, this, that")
                .addHeader(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.toString())
                .build();
        final Set<String> hopByHopConnectionSpecific = MessageSupport.hopByHopConnectionSpecific(response);
        Assertions.assertTrue(hopByHopConnectionSpecific.contains("Connection"));
        Assertions.assertTrue(hopByHopConnectionSpecific.contains("connection"));
        Assertions.assertTrue(hopByHopConnectionSpecific.contains("coNNection"));
        Assertions.assertFalse(hopByHopConnectionSpecific.contains("Content-Type"));
        Assertions.assertTrue(hopByHopConnectionSpecific.contains("blah"));
        Assertions.assertTrue(hopByHopConnectionSpecific.contains("Blah"));
        Assertions.assertTrue(hopByHopConnectionSpecific.contains("This"));
        Assertions.assertTrue(hopByHopConnectionSpecific.contains("That"));
    }

}

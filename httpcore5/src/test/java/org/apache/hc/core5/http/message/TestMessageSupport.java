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
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.apache.hc.core5.http.support.BasicResponseBuilder;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.apache.hc.core5.util.Tokenizer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestMessageSupport {

    private static Set<String> makeSet(final String... tokens) {
        if (tokens == null) {
            return null;
        }
        final Set<String> set = new LinkedHashSet<>();
        Collections.addAll(set, tokens);
        return set;
    }

    @Test
    void testTokenSetFormatting() {
        final Header header = MessageSupport.header(HttpHeaders.TRAILER, makeSet("z", "b", "a"));
        Assertions.assertThat(header).isNotNull();
        Assertions.assertThat(header.getValue()).isEqualTo("z, b, a");
    }

    @Test
    void testTokenListFormatting() {
        final Header header = MessageSupport.headerOfTokens(HttpHeaders.TRAILER, Arrays.asList("z", "b", "a", "a"));
        Assertions.assertThat(header).isNotNull();
        Assertions.assertThat(header.getValue()).isEqualTo("z, b, a, a");
    }

    @Test
    void testTokenListFormattingWithTransformation() {
        final Header header = MessageSupport.headerOfTokens(HttpHeaders.TRAILER, Arrays.asList("z", "b", "a", "a"),
                e -> e.toUpperCase(Locale.ROOT));
        Assertions.assertThat(header).isNotNull();
        Assertions.assertThat(header.getValue()).isEqualTo("Z, B, A, A");
    }

    @Test
    void testTokenSetFormattingSameName() {
        final Header header = MessageSupport.header(HttpHeaders.TRAILER, makeSet("a", "a", "a"));
        Assertions.assertThat(header).isNotNull();
        Assertions.assertThat(header.getValue()).isEqualTo("a");
    }

    @Test
    void testTokenListFormattingSameName() {
        final Header header = MessageSupport.header(HttpHeaders.TRAILER, "a", "a", "a");
        Assertions.assertThat(header).isNotNull();
        Assertions.assertThat(header.getValue()).isEqualTo("a, a, a");
    }

    @Test
    void testParseTokensWithConsumer() {
        final String s = "a, b, c, c";
        final ParserCursor cursor = new ParserCursor(0, s.length());
        final List<String> tokens = new ArrayList<>();
        MessageSupport.parseTokens(s, cursor, tokens::add);
        Assertions.assertThat(tokens).isEqualTo(Arrays.asList("a", "b", "c", "c"));
    }

    @Test
    void testParseTokensWithConsumerAndCustomDelimiter() {
        final String s = "a, b ;  c, c";
        final ParserCursor cursor = new ParserCursor(0, s.length());
        final List<String> tokens = new ArrayList<>();
        MessageSupport.parseTokens(s, cursor, Tokenizer.delimiters(';'), tokens::add);
        Assertions.assertThat(tokens).isEqualTo(Arrays.asList("a, b", "c, c"));
    }

    @Test
    void testParseTokenHeaderWithConsumer() {
        final Header header = new BasicHeader(HttpHeaders.TRAILER, "a, b, c, c");
        final List<String> tokens = new ArrayList<>();
        MessageSupport.parseTokens(header, tokens::add);
        Assertions.assertThat(tokens).isEqualTo(Arrays.asList("a", "b", "c", "c"));
    }

    @Test
    void testParseTokenBufferWithConsumer() {
        final CharArrayBuffer buf = new CharArrayBuffer(128);
        buf.append("stuff: a, b, c, c");
        final Header header = BufferedHeader.create(buf);
        Assertions.assertThat(MessageSupport.parseTokens(header)).isEqualTo(makeSet("a", "b", "c", "c"));
    }

    @Test
    void testParseTokens() {
        final String s = "a, b, c, c";
        final ParserCursor cursor = new ParserCursor(0, s.length());
        Assertions.assertThat(MessageSupport.parseTokens(s, cursor)).isEqualTo(makeSet("a", "b", "c"));
    }

    @Test
    void testParseTokenHeader() {
        final Header header = new BasicHeader(HttpHeaders.TRAILER, "a, b, c, c");
        Assertions.assertThat(MessageSupport.parseTokens(header)).isEqualTo(makeSet("a", "b", "c"));
    }

    @Test
    void testParseTokenBuffer() {
        final CharArrayBuffer buf = new CharArrayBuffer(128);
        buf.append("stuff: a, b, c, c");
        final Header header = BufferedHeader.create(buf);
        Assertions.assertThat(MessageSupport.parseTokens(header)).isEqualTo(makeSet("a", "b", "c"));
    }

    @Test
    void testElementListFormatting() {
        final List<HeaderElement> elements = Arrays.asList(
                new BasicHeaderElement("name1", "value1", new BasicNameValuePair("param", "regular_stuff")),
                new BasicHeaderElement("name2", "value2", new BasicNameValuePair("param", "this\\that")),
                new BasicHeaderElement("name3", "value3", new BasicNameValuePair("param", "this,that")),
                new BasicHeaderElement("name4", "value4", new BasicNameValuePair("param", null)),
                new BasicHeaderElement("name5", null));

        final Header header = MessageSupport.headerOfElements("Some-header", elements);
        Assertions.assertThat(header).isNotNull();
        Assertions.assertThat(header.getValue()).isEqualTo("name1=value1; param=regular_stuff, name2=value2; " +
                "param=\"this\\\\that\", name3=value3; param=\"this,that\", " +
                "name4=value4; param, name5");
    }

    @Test
    void testElementArrayFormatting() {
        final HeaderElement[] elements = {
                new BasicHeaderElement("name1", "value1", new BasicNameValuePair("param", "regular_stuff")),
                new BasicHeaderElement("name2", "value2", new BasicNameValuePair("param", "this\\that")),
                new BasicHeaderElement("name3", "value3", new BasicNameValuePair("param", "this,that")),
                new BasicHeaderElement("name4", "value4", new BasicNameValuePair("param", null)),
                new BasicHeaderElement("name5", null)};

        final Header header = MessageSupport.header("Some-Header", elements);
        Assertions.assertThat(header).isNotNull();
        Assertions.assertThat(header.getValue()).isEqualTo("name1=value1; param=regular_stuff, name2=value2; " +
                "param=\"this\\\\that\", name3=value3; param=\"this,that\", " +
                "name4=value4; param, name5");
    }

    @Test
    void testParseElementsBufferWithConsumer() {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        buf.append("name1 = value1; name2; name3=\"value3\" , name4=value4; " +
                "name5=value5, name6= ; name7 = value7; name8 = \" value8\"");
        final List<HeaderElement> elements = new ArrayList<>();
        final ParserCursor cursor = new ParserCursor(0, buf.length());
        MessageSupport.parseElements(buf, cursor, elements::add);
        // there are 3 elements
        Assertions.assertThat(elements.size()).isEqualTo(3);
        // 1st element
        Assertions.assertThat(elements.get(0).getName()).isEqualTo("name1");
        Assertions.assertThat(elements.get(0).getValue()).isEqualTo("value1");
        // 1st element has 2 getParameters()
        Assertions.assertThat(elements.get(0).getParameters().length).isEqualTo(2);
        Assertions.assertThat(elements.get(0).getParameters()[0].getName()).isEqualTo("name2");
        Assertions.assertThat(elements.get(0).getParameters()[0].getValue()).isNull();
        Assertions.assertThat(elements.get(0).getParameters()[1].getName()).isEqualTo("name3");
        Assertions.assertThat(elements.get(0).getParameters()[1].getValue()).isEqualTo("value3");
        // 2nd element
        Assertions.assertThat(elements.get(1).getName()).isEqualTo("name4");
        Assertions.assertThat(elements.get(1).getValue()).isEqualTo("value4");
        // 2nd element has 1 parameter
        Assertions.assertThat(elements.get(1).getParameters().length).isEqualTo(1);
        Assertions.assertThat(elements.get(1).getParameters()[0].getName()).isEqualTo("name5");
        Assertions.assertThat(elements.get(1).getParameters()[0].getValue()).isEqualTo("value5");
        // 3rd element
        Assertions.assertThat(elements.get(2).getName()).isEqualTo("name6");
        Assertions.assertThat(elements.get(2).getValue()).isEqualTo("");
        // 3rd element has 2 getParameters()
        Assertions.assertThat(elements.get(2).getParameters().length).isEqualTo(2);
        Assertions.assertThat(elements.get(2).getParameters()[0].getName()).isEqualTo("name7");
        Assertions.assertThat(elements.get(2).getParameters()[0].getValue()).isEqualTo("value7");
        Assertions.assertThat(elements.get(2).getParameters()[1].getName()).isEqualTo("name8");
        Assertions.assertThat(elements.get(2).getParameters()[1].getValue()).isEqualTo(" value8");
    }

    @Test
    void testParseElementsHeaderWithConsumer() {
        final Header header = new BasicHeader("Some-Header",
                "name1 = value1; name2; name3=\"value3\" , name4=value4; " +
                "name5=value5, name6= ; name7 = value7; name8 = \" value8\"");
        final List<HeaderElement> elements = new ArrayList<>();
        MessageSupport.parseElements(header, elements::add);
        // there are 3 elements
        Assertions.assertThat(elements.size()).isEqualTo(3);
        // 1st element
        Assertions.assertThat(elements.get(0).getName()).isEqualTo("name1");
        Assertions.assertThat(elements.get(0).getValue()).isEqualTo("value1");
        // 1st element has 2 getParameters()
        Assertions.assertThat(elements.get(0).getParameters().length).isEqualTo(2);
        Assertions.assertThat(elements.get(0).getParameters()[0].getName()).isEqualTo("name2");
        Assertions.assertThat(elements.get(0).getParameters()[0].getValue()).isNull();
        Assertions.assertThat(elements.get(0).getParameters()[1].getName()).isEqualTo("name3");
        Assertions.assertThat(elements.get(0).getParameters()[1].getValue()).isEqualTo("value3");
        // 2nd element
        Assertions.assertThat(elements.get(1).getName()).isEqualTo("name4");
        Assertions.assertThat(elements.get(1).getValue()).isEqualTo("value4");
        // 2nd element has 1 parameter
        Assertions.assertThat(elements.get(1).getParameters().length).isEqualTo(1);
        Assertions.assertThat(elements.get(1).getParameters()[0].getName()).isEqualTo("name5");
        Assertions.assertThat(elements.get(1).getParameters()[0].getValue()).isEqualTo("value5");
        // 3rd element
        Assertions.assertThat(elements.get(2).getName()).isEqualTo("name6");
        Assertions.assertThat(elements.get(2).getValue()).isEqualTo("");
        // 3rd element has 2 getParameters()
        Assertions.assertThat(elements.get(2).getParameters().length).isEqualTo(2);
        Assertions.assertThat(elements.get(2).getParameters()[0].getName()).isEqualTo("name7");
        Assertions.assertThat(elements.get(2).getParameters()[0].getValue()).isEqualTo("value7");
        Assertions.assertThat(elements.get(2).getParameters()[1].getName()).isEqualTo("name8");
        Assertions.assertThat(elements.get(2).getParameters()[1].getValue()).isEqualTo(" value8");
    }

    @Test
    void testParseElementsHeader() {
        final Header header = new BasicHeader("Some-Header",
                "name1 = value1; name2; name3=\"value3\" , name4=value4; " +
                        "name5=value5, name6= ; name7 = value7; name8 = \" value8\"");
        final List<HeaderElement> elements = MessageSupport.parseElements(header);
        // there are 3 elements
        Assertions.assertThat(elements.size()).isEqualTo(3);
        // 1st element
        Assertions.assertThat(elements.get(0).getName()).isEqualTo("name1");
        Assertions.assertThat(elements.get(0).getValue()).isEqualTo("value1");
        // 1st element has 2 getParameters()
        Assertions.assertThat(elements.get(0).getParameters().length).isEqualTo(2);
        Assertions.assertThat(elements.get(0).getParameters()[0].getName()).isEqualTo("name2");
        Assertions.assertThat(elements.get(0).getParameters()[0].getValue()).isNull();
        Assertions.assertThat(elements.get(0).getParameters()[1].getName()).isEqualTo("name3");
        Assertions.assertThat(elements.get(0).getParameters()[1].getValue()).isEqualTo("value3");
        // 2nd element
        Assertions.assertThat(elements.get(1).getName()).isEqualTo("name4");
        Assertions.assertThat(elements.get(1).getValue()).isEqualTo("value4");
        // 2nd element has 1 parameter
        Assertions.assertThat(elements.get(1).getParameters().length).isEqualTo(1);
        Assertions.assertThat(elements.get(1).getParameters()[0].getName()).isEqualTo("name5");
        Assertions.assertThat(elements.get(1).getParameters()[0].getValue()).isEqualTo("value5");
        // 3rd element
        Assertions.assertThat(elements.get(2).getName()).isEqualTo("name6");
        Assertions.assertThat(elements.get(2).getValue()).isEqualTo("");
        // 3rd element has 2 getParameters()
        Assertions.assertThat(elements.get(2).getParameters().length).isEqualTo(2);
        Assertions.assertThat(elements.get(2).getParameters()[0].getName()).isEqualTo("name7");
        Assertions.assertThat(elements.get(2).getParameters()[0].getValue()).isEqualTo("value7");
        Assertions.assertThat(elements.get(2).getParameters()[1].getName()).isEqualTo("name8");
        Assertions.assertThat(elements.get(2).getParameters()[1].getValue()).isEqualTo(" value8");
    }

    @Test
    void testParamListFormatting() {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        MessageSupport.formatParameters(buf, Arrays.asList(
                new BasicNameValuePair("param", "regular_stuff"),
                new BasicNameValuePair("param", "this\\that"),
                new BasicNameValuePair("param", "this,that")
        ));
        Assertions.assertThat(buf.toString()).isEqualTo("param=regular_stuff; param=\"this\\\\that\"; param=\"this,that\"");
    }

    @Test
    void testParamArrayFormatting() {
        final CharArrayBuffer buf = new CharArrayBuffer(64);
        MessageSupport.formatParameters(buf,
                new BasicNameValuePair("param", "regular_stuff"),
                new BasicNameValuePair("param", "this\\that"),
                new BasicNameValuePair("param", "this,that")
        );
        Assertions.assertThat(buf.toString()).isEqualTo("param=regular_stuff; param=\"this\\\\that\"; param=\"this,that\"");
    }

    @Test
    void testParseParams() {
        final String s =
                "test; test1 =  stuff   ; test2 =  \"stuff; stuff\"; test3=stuff,123";
        final CharArrayBuffer buffer = new CharArrayBuffer(16);
        buffer.append(s);
        final ParserCursor cursor = new ParserCursor(0, s.length());

        final List<NameValuePair> params = new ArrayList<>();
        MessageSupport.parseParameters(buffer, cursor, params::add);
        Assertions.assertThat(params.get(0).getName()).isEqualTo("test");
        Assertions.assertThat(params.get(0).getValue()).isNull();
        Assertions.assertThat(params.get(1).getName()).isEqualTo("test1");
        Assertions.assertThat(params.get(1).getValue()).isEqualTo("stuff");
        Assertions.assertThat(params.get(2).getName()).isEqualTo("test2");
        Assertions.assertThat(params.get(2).getValue()).isEqualTo("stuff; stuff");
        Assertions.assertThat(params.get(3).getName()).isEqualTo("test3");
        Assertions.assertThat(params.get(3).getValue()).isEqualTo("stuff");
        Assertions.assertThat(cursor.getPos()).isEqualTo(s.length() - 4);
        Assertions.assertThat(cursor.atEnd()).isFalse();
    }

    static String copyHeader(final CharSequence charSequence, final ParserCursor cursor) {
        final CharArrayBuffer buf = new CharArrayBuffer(10);
        int pos = cursor.getPos();
        while (pos < cursor.getUpperBound()) {
            final char ch = charSequence.charAt(pos);
            buf.append(ch);
            pos++;
        }
        cursor.updatePos(pos);
        return buf.toString();
    }

    static String copyToken(final CharSequence charSequence, final ParserCursor cursor) {
        final CharArrayBuffer buf = new CharArrayBuffer(10);
        int pos = cursor.getPos();
        while (pos < cursor.getUpperBound()) {
            final char ch = charSequence.charAt(pos);
            if (ch == ',') {
                break;
            }
            buf.append(ch);
            pos++;
        }
        cursor.updatePos(pos);
        return buf.substringTrimmed(0, buf.length());
    }

    @Test
    void testParseHeaders() {
        final HttpMessage message = new BasicHttpRequest(Method.GET, "/");
        message.addHeader("Some-Header", "this");
        message.addHeader("Some-Header", "that");
        message.addHeader("Some-Header", " this, that,  what not");

        final List<String> headerValues = new LinkedList<>();
        MessageSupport.parseHeaders(message, "Some-header", (charSequence, cursor) -> {
            final String headerValue = copyHeader(charSequence, cursor);
            headerValues.add(headerValue);
        });
        Assertions.assertThat(headerValues).isEqualTo(Arrays.asList("this", "that", " this, that,  what not"));

        final List<String> tokens = new LinkedList<>();
        MessageSupport.parseElementList(message, "Some-header", (charSequence, cursor) -> {
            final String token = copyToken(charSequence, cursor);
            tokens.add(token);
        });
        Assertions.assertThat(tokens).isEqualTo(Arrays.asList("this", "that", "this", "that", "what not"));
    }

    @Test
    void testParseHeadersElementWhitespace() throws Exception {
        final HttpMessage message = new BasicHttpRequest(Method.GET, "/");
        message.addHeader("Some-Header", "HTTP/1.0");
        message.addHeader("Some-Header", "   HTTP/1.1  ");
        message.addHeader("Some-Header", " HTTP/2   , HTTP/2.0   , HTTP/0.9  ");

        final List<String> versions = new LinkedList<>();
        MessageSupport.parseElementList(message, "Some-header", (charSequence, cursor) -> {
            final String ver = copyToken(charSequence, cursor);
            versions.add(ver);
        });
        Assertions.assertThat(versions).isEqualTo(Arrays.asList(
                        "HTTP/1.0",
                        "HTTP/1.1",
                        "HTTP/2",
                        "HTTP/2.0",
                "HTTP/0.9"));
    }

    @Test
    void testParseHeadersStrict() throws Exception {
        final HttpMessage message = new BasicHttpRequest(Method.GET, "/");
        message.addHeader("Some-Header", "this");
        message.addHeader("Some-Header", "that");
        message.addHeader("Some-Header", " this, that,  what not");

        final List<String> headerValues = new LinkedList<>();
        MessageSupport.parseHeadersStrict(message, "Some-header", (charSequence, cursor) -> {
            final String headerValue = copyHeader(charSequence, cursor);
            headerValues.add(headerValue);
        });
        Assertions.assertThat(headerValues).isEqualTo(Arrays.asList("this", "that", " this, that,  what not"));

        final List<String> tokens = new LinkedList<>();
        MessageSupport.parseElementListStrict(message, "Some-header", (charSequence, cursor) -> {
            final String token = copyToken(charSequence, cursor);
            tokens.add(token);
        });
        Assertions.assertThat(tokens).isEqualTo(Arrays.asList("this", "that", "this", "that", "what not"));

        Assertions.assertThatThrownBy(() ->
                MessageSupport.parseElementListStrict(
                        message,
                        "Some-header",
                        (charSequence, cursor) -> {
                    final String token = copyToken(charSequence, cursor);
                    if (token.equalsIgnoreCase("what not")) {
                        throw new ParseException("How awful!");
                    }
                        })).isInstanceOf(ParseException.class);
    }

    @Test
    void testParseHeadersStrictElementWhitespace() throws Exception {
        final HttpMessage message = new BasicHttpRequest(Method.GET, "/");
        message.addHeader("Some-Header", "HTTP/1.0");
        message.addHeader("Some-Header", "   HTTP/1.1  ");
        message.addHeader("Some-Header", " HTTP/2   , HTTP/2.0   , HTTP/0.9  ");

        final List<ProtocolVersion> versions = new LinkedList<>();
        MessageSupport.parseElementListStrict(message, "Some-header", (charSequence, cursor) -> {
            final ProtocolVersion ver = HttpVersion.parse(charSequence, cursor, Tokenizer.delimiters(','));
            versions.add(ver);
        });
        Assertions.assertThat(versions).isEqualTo(Arrays.asList(
                HttpVersion.HTTP_1_0,
                HttpVersion.HTTP_1_1,
                HttpVersion.HTTP_2_0,
                HttpVersion.HTTP_2_0,
                HttpVersion.HTTP_0_9));
    }

    @Test
    void testParseHeadersStrictInvalidElement() throws Exception {
        final HttpMessage message = new BasicHttpRequest(Method.GET, "/");
        message.addHeader("Some-Header", "HTTP/1.0");
        message.addHeader("Some-Header", "   HTTP/1.1  HTTP/1.1");

        Assertions.assertThatThrownBy(() -> {
                    MessageSupport.parseElementListStrict(message, "Some-header", (charSequence, cursor) -> {
                        HttpVersion.parse(charSequence, cursor, Tokenizer.delimiters(','));
                    });
        }).isInstanceOf(ProtocolException.class);
    }

    @Test
    void testAddContentHeaders() {
        final HttpEntity entity = HttpEntities.create("some stuff with trailers", StandardCharsets.US_ASCII,
                new BasicHeader("z", "this"), new BasicHeader("b", "that"), new BasicHeader("a", "this and that"));
        final HttpMessage message = new BasicHttpResponse(200);
        MessageSupport.addTrailerHeader(message, entity);
        MessageSupport.addContentTypeHeader(message, entity);

        final Header h1 = message.getFirstHeader(HttpHeaders.TRAILER);
        final Header h2 = message.getFirstHeader(HttpHeaders.CONTENT_TYPE);

        Assertions.assertThat(h1).isNotNull();
        Assertions.assertThat(h1.getValue()).isEqualTo("z, b, a");
        Assertions.assertThat(h2).isNotNull();
        Assertions.assertThat(h2.getValue()).isEqualTo("text/plain; charset=US-ASCII");
    }

    @Test
    void testContentHeadersAlreadyPresent() {
        final HttpEntity entity = HttpEntities.create("some stuff with trailers", StandardCharsets.US_ASCII,
                new BasicHeader("z", "this"), new BasicHeader("b", "that"), new BasicHeader("a", "this and that"));
        final HttpMessage message = new BasicHttpResponse(200);
        message.addHeader(HttpHeaders.TRAILER, "a, a, a");
        message.addHeader(HttpHeaders.CONTENT_TYPE, "text/plain; charset=ascii");

        MessageSupport.addTrailerHeader(message, entity);
        MessageSupport.addContentTypeHeader(message, entity);

        final Header h1 = message.getFirstHeader(HttpHeaders.TRAILER);
        final Header h2 = message.getFirstHeader(HttpHeaders.CONTENT_TYPE);

        Assertions.assertThat(h1).isNotNull();
        Assertions.assertThat(h1.getValue()).isEqualTo("a, a, a");
        Assertions.assertThat(h2).isNotNull();
        Assertions.assertThat(h2.getValue()).isEqualTo("text/plain; charset=ascii");
    }

    @Test
    void testHopByHopHeaders() {
        Assertions.assertThat(MessageSupport.isHopByHop("Connection")).isTrue();
        Assertions.assertThat(MessageSupport.isHopByHop("connection")).isTrue();
        Assertions.assertThat(MessageSupport.isHopByHop("coNNection")).isTrue();
        Assertions.assertThat(MessageSupport.isHopByHop("Content-Type")).isFalse();
        Assertions.assertThat(MessageSupport.isHopByHop("huh")).isFalse();
    }

    @Test
    void testHopByHopHeadersConnectionSpecific() {
        final HttpResponse response = BasicResponseBuilder.create(HttpStatus.SC_OK)
                .addHeader(HttpHeaders.CONNECTION, "blah, blah, this, that")
                .addHeader(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.toString())
                .build();
        final Set<String> hopByHopConnectionSpecific = MessageSupport.hopByHopConnectionSpecific(response);
        Assertions.assertThat(hopByHopConnectionSpecific.contains("Connection")).isTrue();
        Assertions.assertThat(hopByHopConnectionSpecific.contains("connection")).isTrue();
        Assertions.assertThat(hopByHopConnectionSpecific.contains("coNNection")).isTrue();
        Assertions.assertThat(hopByHopConnectionSpecific.contains("Content-Type")).isFalse();
        Assertions.assertThat(hopByHopConnectionSpecific.contains("blah")).isTrue();
        Assertions.assertThat(hopByHopConnectionSpecific.contains("Blah")).isTrue();
        Assertions.assertThat(hopByHopConnectionSpecific.contains("This")).isTrue();
        Assertions.assertThat(hopByHopConnectionSpecific.contains("That")).isTrue();
    }

    @Test
    void testMessageWithContentLength() throws Exception {
        final HttpMessage message = new BasicHttpResponse(200);
        message.addHeader("Content-Length", "100");
        Assertions.assertThat(MessageSupport.getContentLength(message)).isEqualTo(100);
    }

    @Test
    void testMessageWithInvalidContentLength() {
        final HttpMessage message = new BasicHttpResponse(200);
        message.addHeader("Content-Length", "whatever");
        Assertions.assertThatThrownBy(() ->
                MessageSupport.getContentLength(message)).isInstanceOf(ProtocolException.class);
    }

    @Test
    void testMessageWithNegativeContentLength() {
        final HttpMessage message = new BasicHttpResponse(200);
        message.addHeader("Content-Length", "-10");
        Assertions.assertThatThrownBy(() ->
                MessageSupport.getContentLength(message)).isInstanceOf(ProtocolException.class);
    }

    @Test
    void testMessageWithPlusSignedContentLength() {
        final HttpMessage message = new BasicHttpResponse(200);
        message.addHeader("Content-Length", "+5");
        Assertions.assertThatThrownBy(() -> MessageSupport.getContentLength(message))
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    void testMessageWithEmptyContentLength() {
        final HttpMessage message = new BasicHttpResponse(200);
        message.addHeader("Content-Length", "");
        Assertions.assertThatThrownBy(() -> MessageSupport.getContentLength(message))
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    void testIsSignedOrEmpty() {
        Assertions.assertThat(MessageSupport.isSignedOrEmpty(null)).isTrue();
        Assertions.assertThat(MessageSupport.isSignedOrEmpty("")).isTrue();
        Assertions.assertThat(MessageSupport.isSignedOrEmpty("+5")).isTrue();
        Assertions.assertThat(MessageSupport.isSignedOrEmpty("-5")).isTrue();
        Assertions.assertThat(MessageSupport.isSignedOrEmpty("-0")).isTrue();
        Assertions.assertThat(MessageSupport.isSignedOrEmpty("5")).isFalse();
        Assertions.assertThat(MessageSupport.isSignedOrEmpty("0005")).isFalse();
        Assertions.assertThat(MessageSupport.isSignedOrEmpty("a+")).isFalse();
    }

    @Test
    void testMessageWithMinusZeroContentLength() {
        final HttpMessage message = new BasicHttpResponse(200);
        message.addHeader("Content-Length", "-0");
        Assertions.assertThatThrownBy(() -> MessageSupport.getContentLength(message))
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    void testMessageWithOverflowContentLength() {
        final HttpMessage message = new BasicHttpResponse(200);
        message.addHeader("Content-Length", "99999999999999999999");
        Assertions.assertThatThrownBy(() -> MessageSupport.getContentLength(message))
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    void testMessageWithLeadingZerosContentLength() throws Exception {
        final HttpMessage message = new BasicHttpResponse(200);
        message.addHeader("Content-Length", "005");
        Assertions.assertThat(MessageSupport.getContentLength(message)).isEqualTo(5);
    }

    @Test
    void testMessageWithMultipleContentLength() throws Exception {
        final HttpMessage message = new BasicHttpResponse(200);
        message.addHeader("Content-Length", "100");
        message.addHeader("Content-Length", "100");
        Assertions.assertThatThrownBy(() ->
                MessageSupport.getContentLength(message)).isInstanceOf(ProtocolException.class);
    }

}

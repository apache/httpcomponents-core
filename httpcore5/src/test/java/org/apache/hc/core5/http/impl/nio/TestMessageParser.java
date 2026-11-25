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

package org.apache.hc.core5.http.impl.nio;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.impl.MockMessage;
import org.apache.hc.core5.http.message.BasicLineParser;
import org.apache.hc.core5.http.message.LineParser;
import org.apache.hc.core5.http.nio.SessionInputBuffer;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AbstractMessageParser}.
 */
class TestMessageParser {

    static class MockParser extends AbstractMessageParser<MockMessage> {

        public MockParser(final Http1Config http1Config, final LineParser lineParser) {
            super(http1Config, lineParser);
        }

        @Override
        protected MockMessage createMessage(final CharArrayBuffer buffer) throws HttpException {
            return new MockMessage();
        }

    }

    private static ReadableByteChannel newChannel(final String s, final Charset charset) {
        return Channels.newChannel(new ByteArrayInputStream(s.getBytes(charset)));
    }

    private static ReadableByteChannel newChannel(final String s) {
        return newChannel(s, StandardCharsets.US_ASCII);
    }

    @Test
    void testBasicHeaderParsing() throws Exception {
        final MockParser parser = new MockParser(Http1Config.DEFAULT, BasicLineParser.INSTANCE);

        final String s = "first-line\r\n" +
            "header1: stuff\r\n" +
            "header2:  stuff \r\n" +
            "header3: stuff\r\n" +
            "     and more stuff\r\n" +
            "\t and even more stuff\r\n" +
            "     \r\n" +
            "\r\n";
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 1024);
        final ReadableByteChannel channel1 = newChannel(s);
        inbuf.fill(channel1);

        final MockMessage message = parser.parse(inbuf, true);
        Assertions.assertThat(message).isNotNull();

        final Header[] headers = message.getHeaders();
        Assertions.assertThat(headers).isNotNull().hasSize(3);
        Assertions.assertThat(headers[0])
                .extracting(h -> Assertions.tuple(h.getName(), h.getValue()))
                .isEqualTo(Assertions.tuple("header1", "stuff"));
        Assertions.assertThat(headers[1])
                .extracting(h -> Assertions.tuple(h.getName(), h.getValue()))
                .isEqualTo(Assertions.tuple("header2", "stuff"));
        Assertions.assertThat(headers[2])
                .extracting(h -> Assertions.tuple(h.getName(), h.getValue()))
                .isEqualTo(Assertions.tuple("header3", "stuff and more stuff and even more stuff"));
    }

    @Test
    void testBasicHeaderParsingChunks() throws Exception {
        final MockParser parser = new MockParser(Http1Config.DEFAULT, BasicLineParser.INSTANCE);

        final String s1 = "first-line\r\n" +
                "header1: stuff\r\n" +
                "header2:  ";
        final String s2 = "stuff \r\n" +
                "header3: stuff\r\n" +
                "     and more stuff\r\n" +
                "\t and even more stuff\r\n";
        final String s3 = "     \r\n" +
                "\r\n";
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 1024);
        final ReadableByteChannel channel1 = newChannel(s1);
        inbuf.fill(channel1);

        final MockMessage message1 = parser.parse(inbuf, false);
        Assertions.assertThat(message1).isNull();

        final ReadableByteChannel channel2 = newChannel(s2);
        inbuf.fill(channel2);

        final MockMessage message2 = parser.parse(inbuf, false);
        Assertions.assertThat(message2).isNull();

        final ReadableByteChannel channel3 = newChannel(s3);
        inbuf.fill(channel3);

        final MockMessage message3 = parser.parse(inbuf, false);
        Assertions.assertThat(message3).isNotNull();

        final Header[] headers = message3.getHeaders();
        Assertions.assertThat(headers).isNotNull().hasSize(3);
        Assertions.assertThat(headers[0])
                .extracting(h -> Assertions.tuple(h.getName(), h.getValue()))
                .isEqualTo(Assertions.tuple("header1", "stuff"));
        Assertions.assertThat(headers[1])
                .extracting(h -> Assertions.tuple(h.getName(), h.getValue()))
                .isEqualTo(Assertions.tuple("header2", "stuff"));
        Assertions.assertThat(headers[2])
                .extracting(h -> Assertions.tuple(h.getName(), h.getValue()))
                .isEqualTo(Assertions.tuple("header3", "stuff and more stuff and even more stuff"));
    }

    @Test
    void testParsingHeaderWithParams() throws Exception {
        final MockParser parser = new MockParser(Http1Config.DEFAULT, BasicLineParser.INSTANCE);

        final String s = "first-message-line\r\n" +
                "header1: stuff; param1 = value1; param2 = \"value 2\" \r\n" +
                "\r\n";
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 1024);
        final ReadableByteChannel channel1 = newChannel(s);
        inbuf.fill(channel1);

        final MockMessage message = parser.parse(inbuf, true);
        Assertions.assertThat(message).isNotNull();

        final Header[] headers = message.getHeaders();
        Assertions.assertThat(headers)
                .isNotNull()
                .hasSize(1);
        Assertions.assertThat(headers[0])
                .extracting(h -> Assertions.tuple(h.getName(), h.getValue()))
                .isEqualTo(Assertions.tuple("header1", "stuff; param1 = value1; param2 = \"value 2\""));
    }

    @Test
    void testParsingInvalidHeaders() throws Exception {
        final MockParser parser = new MockParser(Http1Config.DEFAULT, BasicLineParser.INSTANCE);

        final String s1 = "first-message-line\r\n" +
                "    stuff\r\n" +
                "header1: stuff\r\n" +
                "\r\n";
        final SessionInputBuffer inbuf1 = new SessionInputBufferImpl(1024, 1024);
        final ReadableByteChannel channel1 = newChannel(s1);
        inbuf1.fill(channel1);

        Assertions.assertThatThrownBy(() -> parser.parse(inbuf1, true))
                .isInstanceOf(ProtocolException.class);

        final String s2 = "first-message-line\r\n" +
                "  :  stuff\r\n" +
                "header1: stuff\r\n" +
                "\r\n";
        final SessionInputBuffer inbuf2 = new SessionInputBufferImpl(1024, 1024);
        final ReadableByteChannel channel2 = newChannel(s2);
        inbuf2.fill(channel2);

        Assertions.assertThatThrownBy(() -> parser.parse(inbuf2, true))
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    void testParsingMalformedFirstHeader() throws Exception {
        final MockParser parser = new MockParser(Http1Config.DEFAULT, BasicLineParser.INSTANCE);

        final String s = "first-message-line\r\n" +
                "    header1: stuff\r\n" +
                "header2: stuff \r\n";
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 1024);
        final ReadableByteChannel channel1 = newChannel(s);
        inbuf.fill(channel1);

        Assertions.assertThatThrownBy(() -> parser.parse(inbuf, true))
                .isInstanceOf(ProtocolException.class)
                .hasMessage("Header line starts with whitespace");
    }

    @Test
    void testEmptyDataStream() throws Exception {
        final MockParser parser = new MockParser(Http1Config.DEFAULT, BasicLineParser.INSTANCE);

        final String s = "first-message-line\r\n" +
                "";
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 1024);
        final ReadableByteChannel channel1 = newChannel(s);
        inbuf.fill(channel1);

        final MockMessage message = parser.parse(inbuf, true);
        Assertions.assertThat(message).isNotNull();

        final Header[] headers = message.getHeaders();

        Assertions.assertThat(headers).isNotNull();
        Assertions.assertThat(headers.length).isEqualTo(0);
    }

    @Test
    void testMaxHeaderCount() throws Exception {
        final MockParser parser = new TestMessageParser.MockParser(
                Http1Config.custom()
                        .setMaxHeaderCount(2)
                        .build(),
                BasicLineParser.INSTANCE);

        final String s = "first-message-line\r\n" +
                "header1: stuff\r\n" +
                "header2: stuff \r\n" +
                "header3: stuff\r\n" +
                "\r\n";
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 1024);
        final ReadableByteChannel channel1 = newChannel(s);
        inbuf.fill(channel1);

        Assertions.assertThatThrownBy(() -> parser.parse(inbuf, true))
                .isInstanceOf(IOException.class);
    }

    @Test
    void testMaxHeaderCountForFoldedHeader() throws Exception {
        final TestMessageParser.MockParser parser = new TestMessageParser.MockParser(
                Http1Config.custom()
                        .setMaxHeaderCount(2)
                        .setMaxLineLength(15)
                        .build(),
                BasicLineParser.INSTANCE);

        final String s = "first-message-line\r\n" +
                "header1: stuff\r\n" +
                " stuff \r\n" +
                " stuff\r\n" +
                "\r\n";
        final SessionInputBuffer inbuf = new SessionInputBufferImpl(1024, 1024);
        final ReadableByteChannel channel1 = newChannel(s);
        inbuf.fill(channel1);

        Assertions.assertThatThrownBy(() -> parser.parse(inbuf, true))
                .isInstanceOf(IOException.class);
    }

}


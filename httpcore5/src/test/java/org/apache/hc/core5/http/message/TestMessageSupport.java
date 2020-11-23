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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.junit.Assert;
import org.junit.Test;

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
        final Header header = MessageSupport.format(HttpHeaders.TRAILER, makeSet("z", "b", "a"));
        Assert.assertNotNull(header);
        Assert.assertEquals("z, b, a", header.getValue());
    }

    @Test
    public void testTokenSetFormattingSameName() throws Exception {
        final Header header = MessageSupport.format(HttpHeaders.TRAILER, makeSet("a", "a", "a"));
        Assert.assertNotNull(header);
        Assert.assertEquals("a", header.getValue());
    }

    @Test
    public void testTokensFormattingSameName() throws Exception {
        final Header header = MessageSupport.format(HttpHeaders.TRAILER, "a", "a", "a");
        Assert.assertNotNull(header);
        Assert.assertEquals("a, a, a", header.getValue());
    }

    @Test
    public void testTrailerNoTrailers() throws Exception {
        final Header header = MessageSupport.format(HttpHeaders.TRAILER);
        Assert.assertNull(header);
    }

    @Test
    public void testParseTokens() throws Exception {
        final String s = "a, b, c, c";
        final ParserCursor cursor = new ParserCursor(0, s.length());
        Assert.assertEquals(makeSet("a", "b", "c"), MessageSupport.parseTokens(s, cursor));
    }

    @Test
    public void testParseTokenHeader() throws Exception {
        final Header header = new BasicHeader(HttpHeaders.TRAILER, "a, b, c, c");
        Assert.assertEquals(makeSet("a", "b", "c"), MessageSupport.parseTokens(header));
    }

    @Test
    public void testParseTokenBufferedHeader() throws Exception {
        final CharArrayBuffer buf = new CharArrayBuffer(128);
        buf.append("stuff: a, b, c, c");
        final Header header = BufferedHeader.create(buf);
        Assert.assertEquals(makeSet("a", "b", "c"), MessageSupport.parseTokens(header));
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

        Assert.assertNotNull(h1);
        Assert.assertEquals("z, b, a", h1.getValue());
        Assert.assertNotNull(h2);
        Assert.assertEquals("text/plain; charset=US-ASCII", h2.getValue());
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

        Assert.assertNotNull(h1);
        Assert.assertEquals("a, a, a", h1.getValue());
        Assert.assertNotNull(h2);
        Assert.assertEquals("text/plain; charset=ascii", h2.getValue());
    }

}

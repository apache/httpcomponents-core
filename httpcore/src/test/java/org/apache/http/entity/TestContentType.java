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

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.ParseException;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
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
        Assert.assertEquals(Consts.ASCII, contentType.getCharset());
        Assert.assertEquals("text/plain; charset=US-ASCII", contentType.toString());
    }

    @Test
    public void testWithCharset() throws Exception {
        ContentType contentType = ContentType.create("text/plain", "US-ASCII");
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals(Consts.ASCII, contentType.getCharset());
        Assert.assertEquals("text/plain; charset=US-ASCII", contentType.toString());
        contentType = contentType.withCharset(Charset.forName("UTF-8"));
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals("UTF-8", contentType.getCharset().name());
        Assert.assertEquals("text/plain; charset=UTF-8", contentType.toString());
    }

    @Test
    public void testWithCharsetString() throws Exception {
        ContentType contentType = ContentType.create("text/plain", "US-ASCII");
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals(Consts.ASCII, contentType.getCharset());
        Assert.assertEquals("text/plain; charset=US-ASCII", contentType.toString());
        contentType = contentType.withCharset("UTF-8");
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals(Consts.UTF_8, contentType.getCharset());
        Assert.assertEquals("text/plain; charset=UTF-8", contentType.toString());
    }

    @Test
    public void testLowCaseText() throws Exception {
        final ContentType contentType = ContentType.create("Text/Plain", "ascii");
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals(Consts.ASCII, contentType.getCharset());
    }

    @Test
    public void testCreateInvalidInput() throws Exception {
        try {
            ContentType.create(null, (String) null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        try {
            ContentType.create("  ", (String) null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        try {
            ContentType.create("stuff;", (String) null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        try {
            ContentType.create("text/plain", ",");
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testParse() throws Exception {
        final ContentType contentType = ContentType.parse("text/plain; charset=\"ascii\"");
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals(Consts.ASCII, contentType.getCharset());
        Assert.assertEquals("text/plain; charset=ascii", contentType.toString());
    }

    @Test
    public void testParseMultiparam() throws Exception {
        final ContentType contentType = ContentType.parse("text/plain; charset=\"ascii\"; " +
                "p0 ; p1 = \"blah-blah\"  ; p2 = \" yada yada \" ");
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals(Consts.ASCII, contentType.getCharset());
        Assert.assertEquals("text/plain; charset=ascii; p0; p1=blah-blah; p2=\" yada yada \"",
                contentType.toString());
        Assert.assertEquals(null, contentType.getParameter("p0"));
        Assert.assertEquals("blah-blah", contentType.getParameter("p1"));
        Assert.assertEquals(" yada yada ", contentType.getParameter("p2"));
        Assert.assertEquals(null, contentType.getParameter("p3"));
    }

    @Test
    public void testParseEmptyCharset() throws Exception {
        final ContentType contentType = ContentType.parse("text/plain; charset=\" \"");
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals(null, contentType.getCharset());
    }

    @Test
    public void testParseInvalidInput() throws Exception {
        try {
            ContentType.parse(null);
            Assert.fail("IllegalArgumentException should have been thrown");
        } catch (final IllegalArgumentException ex) {
            // expected
        }
        try {
            ContentType.parse(";");
            Assert.fail("ParseException should have been thrown");
        } catch (final ParseException ex) {
            // expected
        }
    }

    @Test
    public void testExtractNullInput() throws Exception {
        Assert.assertNull(ContentType.get(null));
    }

    @Test
    public void testExtractNullContentType() throws Exception {
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType((Header)null);
        Assert.assertNull(ContentType.get(httpentity));
    }

    @Test
    public void testExtract() throws Exception {
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType(new BasicHeader("Content-Type", "text/plain; charset = UTF-8"));
        final ContentType contentType = ContentType.get(httpentity);
        Assert.assertNotNull(contentType);
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals(Consts.UTF_8, contentType.getCharset());
    }

    @Test
    public void testExtractNoCharset() throws Exception {
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType(new BasicHeader("Content-Type", "text/plain; param=yadayada"));
        final ContentType contentType = ContentType.get(httpentity);
        Assert.assertNotNull(contentType);
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertNull(contentType.getCharset());
    }

    @Test(expected = UnsupportedCharsetException.class)
    public void testExtractInvalidCharset() throws Exception {
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType(new BasicHeader("Content-Type", "text/plain; charset = stuff"));
        ContentType.get(httpentity);
    }

    @Test
    public void testExtracLenienttNullInput() throws Exception {
        Assert.assertNull(ContentType.getLenient(null));
    }

    @Test
    public void testExtractLenientNullContentType() throws Exception {
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType((Header) null);
        Assert.assertNull(ContentType.getLenient(httpentity));
    }

    @Test
    public void testLenientExtractInvalidCharset() throws Exception {
        final BasicHttpEntity httpentity = new BasicHttpEntity();
        httpentity.setContentType(new BasicHeader("Content-Type", "text/plain; charset = stuff"));
        final ContentType contentType = ContentType.getLenient(httpentity);
        Assert.assertNotNull(contentType);
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals(null, contentType.getCharset());
    }

    @Test
    public void testWithParams() throws Exception {
        ContentType contentType = ContentType.create("text/plain",
                new BasicNameValuePair("charset", "UTF-8"),
                new BasicNameValuePair("p", "this"),
                new BasicNameValuePair("p", "that"));
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals(Consts.UTF_8, contentType.getCharset());
        Assert.assertEquals("text/plain; charset=UTF-8; p=this; p=that", contentType.toString());

        contentType = contentType.withParameters(
                new BasicNameValuePair("charset", "ascii"),
                new BasicNameValuePair("p", "this and that"));
        Assert.assertEquals("text/plain", contentType.getMimeType());
        Assert.assertEquals(Consts.ASCII, contentType.getCharset());
        Assert.assertEquals("text/plain; charset=ascii; p=\"this and that\"", contentType.toString());

        contentType = ContentType.create("text/blah").withParameters(
                new BasicNameValuePair("p", "blah"));
        Assert.assertEquals("text/blah", contentType.getMimeType());
        Assert.assertEquals(null, contentType.getCharset());
        Assert.assertEquals("text/blah; p=blah", contentType.toString());

        contentType = ContentType.create("text/blah", Consts.ISO_8859_1).withParameters(
                new BasicNameValuePair("p", "blah"));
        Assert.assertEquals("text/blah", contentType.getMimeType());
        Assert.assertEquals(Consts.ISO_8859_1, contentType.getCharset());
        Assert.assertEquals("text/blah; charset=ISO-8859-1; p=blah", contentType.toString());
    }

}

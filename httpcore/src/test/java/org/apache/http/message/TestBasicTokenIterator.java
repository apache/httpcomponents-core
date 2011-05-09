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

package org.apache.http.message;

import java.util.NoSuchElementException;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.ParseException;
import org.apache.http.TokenIterator;
import org.junit.Assert;
import org.junit.Test;


/**
 * Tests for {@link BasicTokenIterator}.
 *
 */
public class TestBasicTokenIterator {

    @Test
    public void testSingleHeader() {
        Header[] headers = new Header[]{
            new BasicHeader("Name", "token0,token1, token2 , token3")
        };
        HeaderIterator hit = new BasicHeaderIterator(headers, null);
        TokenIterator  ti  = new BasicTokenIterator(hit);

        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token0", "token0", ti.nextToken());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token1", "token1", ti.nextToken());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token2", "token2", ti.nextToken());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token3", "token3", ti.nextToken());
        Assert.assertFalse(ti.hasNext());


        headers = new Header[]{
            new BasicHeader("Name", "token0")
        };
        hit = new BasicHeaderIterator(headers, null);
        ti  = new BasicTokenIterator(hit);

        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token0", "token0", ti.nextToken());
        Assert.assertFalse(ti.hasNext());
    }


    @Test
    public void testMultiHeader() {
        Header[] headers = new Header[]{
            new BasicHeader("Name", "token0,token1"),
            new BasicHeader("Name", ""),
            new BasicHeader("Name", "token2"),
            new BasicHeader("Name", " "),
            new BasicHeader("Name", "token3 "),
            new BasicHeader("Name", ","),
            new BasicHeader("Name", "token4"),
        };
        HeaderIterator hit = new BasicHeaderIterator(headers, null);
        TokenIterator  ti  = new BasicTokenIterator(hit);

        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token0", "token0", ti.nextToken());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token1", "token1", ti.nextToken());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token2", "token2", ti.nextToken());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token3", "token3", ti.nextToken());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token4", "token4", ti.nextToken());
        Assert.assertFalse(ti.hasNext());
    }


    @Test
    public void testEmpty() {
        Header[] headers = new Header[]{
            new BasicHeader("Name", " "),
            new BasicHeader("Name", ""),
            new BasicHeader("Name", ","),
            new BasicHeader("Name", " ,, "),
        };
        HeaderIterator hit = new BasicHeaderIterator(headers, null);
        TokenIterator  ti  = new BasicTokenIterator(hit);

        Assert.assertFalse(ti.hasNext());


        hit = new BasicHeaderIterator(headers, "empty");
        ti  = new BasicTokenIterator(hit);

        Assert.assertFalse(ti.hasNext());
    }


    @Test
    public void testValueStart() {
        Header[] headers = new Header[]{
            new BasicHeader("Name", "token0"),
            new BasicHeader("Name", " token1"),
            new BasicHeader("Name", ",token2"),
            new BasicHeader("Name", " ,token3"),
            new BasicHeader("Name", ", token4"),
            new BasicHeader("Name", " , token5"),
        };
        HeaderIterator hit = new BasicHeaderIterator(headers, null);
        TokenIterator  ti  = new BasicTokenIterator(hit);

        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token0", "token0", ti.nextToken());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token1", "token1", ti.nextToken());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token2", "token2", ti.nextToken());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token3", "token3", ti.nextToken());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token4", "token4", ti.nextToken());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token5", "token5", ti.nextToken());
        Assert.assertFalse(ti.hasNext());
    }


    @Test
    public void testValueEnd() {
        Header[] headers = new Header[]{
            new BasicHeader("Name", "token0"),
            new BasicHeader("Name", "token1 "),
            new BasicHeader("Name", "token2,"),
            new BasicHeader("Name", "token3 ,"),
            new BasicHeader("Name", "token4, "),
            new BasicHeader("Name", "token5 , "),
        };
        HeaderIterator hit = new BasicHeaderIterator(headers, null);
        TokenIterator  ti  = new BasicTokenIterator(hit);

        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token0", "token0", ti.nextToken());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token1", "token1", ti.nextToken());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token2", "token2", ti.nextToken());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token3", "token3", ti.nextToken());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token4", "token4", ti.nextToken());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token5", "token5", ti.nextToken());
        Assert.assertFalse(ti.hasNext());
    }


    @Test
    public void testTokenChar() {
        Header[] headers = new Header[]{
            new BasicHeader("Name", "token0")
        };
        HeaderIterator     hit = new BasicHeaderIterator(headers, null);
        BasicTokenIterator bti = new BasicTokenIterator(hit);

        Assert.assertTrue ("letter"   , bti.isTokenChar('j'));
        Assert.assertFalse("control"  , bti.isTokenChar('\b'));
        Assert.assertFalse("separator", bti.isTokenChar('?'));
        Assert.assertTrue ("other"    , bti.isTokenChar('-'));
    }


    @Test
    public void testInvalid() {
        Header[] headers = new Header[]{
            new BasicHeader("in", "token0=token1"),
            new BasicHeader("no", "token0 token1"),
            new BasicHeader("pre", "<token0,token1"),
            new BasicHeader("post", "token0,token1="),
        };
        HeaderIterator hit = new BasicHeaderIterator(headers, "in");
        TokenIterator  ti  = new BasicTokenIterator(hit);

        // constructor located token0
        Assert.assertTrue(ti.hasNext());
        try {
            ti.nextToken();
            Assert.fail("invalid infix character not detected");
        } catch (ParseException px) {
            // expected
        }


        // constructor located token0
        hit = new BasicHeaderIterator(headers, "no");
        ti  = new BasicTokenIterator(hit);
        Assert.assertTrue(ti.hasNext());
        try {
            ti.nextToken();
            Assert.fail("missing token separator not detected");
        } catch (ParseException px) {
            // expected
        }


        // constructor seeks for the first token
        hit = new BasicHeaderIterator(headers, "pre");
        try {
            new BasicTokenIterator(hit);
            Assert.fail("invalid prefix character not detected");
        } catch (ParseException px) {
            // expected
        }


        hit = new BasicHeaderIterator(headers, "post");
        ti  = new BasicTokenIterator(hit);

        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token0", "token0", ti.nextToken());
        Assert.assertTrue(ti.hasNext());
        // failure after the last must not go unpunished
        try {
            ti.nextToken();
            Assert.fail("invalid postfix character not detected");
        } catch (ParseException px) {
            // expected
        }
    }


    @Test
    public void testWrongPublic() {

        try {
            new BasicTokenIterator(null);
            Assert.fail("null argument not detected");
        } catch (IllegalArgumentException iax) {
            // expected
        }

        Header[] headers = new Header[]{
            new BasicHeader("Name", " "),
            new BasicHeader("Name", ""),
            new BasicHeader("Name", ","),
            new BasicHeader("Name", " ,, "),
        };
        HeaderIterator hit = new BasicHeaderIterator(headers, null);
        TokenIterator  ti  = new BasicTokenIterator(hit);

        try {
            // call next() instead of nextToken() to get that covered, too
            ti.next();
            Assert.fail("next after end not detected");
        } catch (NoSuchElementException nsx) {
            // expected
        }

        try {
            ti.remove();
            Assert.fail("unsupported remove not detected");
        } catch (UnsupportedOperationException uox) {
            // expected
        }
    }


    @Test
    public void testWrongProtected() {

        Header[] headers = new Header[]{
            new BasicHeader("Name", "token1,token2")
        };
        HeaderIterator     hit = new BasicHeaderIterator(headers, null);
        BasicTokenIterator bti = new BasicTokenIterator(hit);

        try {
            bti.findTokenStart(-1);
            Assert.fail("tokenStart: negative index not detected");
        } catch (IllegalArgumentException iax) {
            // expected
        }

        try {
            bti.findTokenSeparator(-1);
            Assert.fail("tokenSeparator: negative index not detected");
        } catch (IllegalArgumentException iax) {
            // expected
        }

        try {
            bti.findTokenEnd(-1);
            Assert.fail("tokenEnd: negative index not detected");
        } catch (IllegalArgumentException iax) {
            // expected
        }
    }

}

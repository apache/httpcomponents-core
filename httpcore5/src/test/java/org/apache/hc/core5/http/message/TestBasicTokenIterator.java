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

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.hc.core5.http.Header;
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
        Iterator<Header> hit = new BasicHeaderIterator(headers, null);
        Iterator<String>  ti  = new BasicTokenIterator(hit);

        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token0", "token0", ti.next());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token1", "token1", ti.next());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token2", "token2", ti.next());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token3", "token3", ti.next());
        Assert.assertFalse(ti.hasNext());


        headers = new Header[]{
            new BasicHeader("Name", "token0")
        };
        hit = new BasicHeaderIterator(headers, null);
        ti  = new BasicTokenIterator(hit);

        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token0", "token0", ti.next());
        Assert.assertFalse(ti.hasNext());
    }


    @Test
    public void testMultiHeader() {
        final Header[] headers = new Header[]{
            new BasicHeader("Name", "token0,token1"),
            new BasicHeader("Name", ""),
            new BasicHeader("Name", "token2"),
            new BasicHeader("Name", " "),
            new BasicHeader("Name", "token3 "),
            new BasicHeader("Name", ","),
            new BasicHeader("Name", "token4"),
        };
        final Iterator<Header> hit = new BasicHeaderIterator(headers, null);
        final Iterator<String>  ti  = new BasicTokenIterator(hit);

        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token0", "token0", ti.next());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token1", "token1", ti.next());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token2", "token2", ti.next());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token3", "token3", ti.next());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token4", "token4", ti.next());
        Assert.assertFalse(ti.hasNext());
    }


    @Test
    public void testEmpty() {
        final Header[] headers = new Header[]{
            new BasicHeader("Name", " "),
            new BasicHeader("Name", ""),
            new BasicHeader("Name", ","),
            new BasicHeader("Name", " ,, "),
        };
        Iterator<Header> hit = new BasicHeaderIterator(headers, null);
        Iterator<String>  ti  = new BasicTokenIterator(hit);

        Assert.assertFalse(ti.hasNext());


        hit = new BasicHeaderIterator(headers, "empty");
        ti  = new BasicTokenIterator(hit);

        Assert.assertFalse(ti.hasNext());
    }


    @Test
    public void testValueStart() {
        final Header[] headers = new Header[]{
            new BasicHeader("Name", "token0"),
            new BasicHeader("Name", " token1"),
            new BasicHeader("Name", ",token2"),
            new BasicHeader("Name", " ,token3"),
            new BasicHeader("Name", ", token4"),
            new BasicHeader("Name", " , token5"),
        };
        final Iterator<Header> hit = new BasicHeaderIterator(headers, null);
        final Iterator<String>  ti  = new BasicTokenIterator(hit);

        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token0", "token0", ti.next());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token1", "token1", ti.next());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token2", "token2", ti.next());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token3", "token3", ti.next());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token4", "token4", ti.next());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token5", "token5", ti.next());
        Assert.assertFalse(ti.hasNext());
    }


    @Test
    public void testValueEnd() {
        final Header[] headers = new Header[]{
            new BasicHeader("Name", "token0"),
            new BasicHeader("Name", "token1 "),
            new BasicHeader("Name", "token2,"),
            new BasicHeader("Name", "token3 ,"),
            new BasicHeader("Name", "token4, "),
            new BasicHeader("Name", "token5 , "),
        };
        final Iterator<Header> hit = new BasicHeaderIterator(headers, null);
        final Iterator<String>  ti  = new BasicTokenIterator(hit);

        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token0", "token0", ti.next());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token1", "token1", ti.next());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token2", "token2", ti.next());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token3", "token3", ti.next());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token4", "token4", ti.next());
        Assert.assertTrue(ti.hasNext());
        Assert.assertEquals("token5", "token5", ti.next());
        Assert.assertFalse(ti.hasNext());
    }

    @Test
    public void testWrongPublic() {
        Assert.assertThrows(NullPointerException.class, () -> new BasicTokenIterator(null));

        final Header[] headers = new Header[]{
            new BasicHeader("Name", " "),
            new BasicHeader("Name", ""),
            new BasicHeader("Name", ","),
            new BasicHeader("Name", " ,, "),
        };
        final Iterator<Header> hit = new BasicHeaderIterator(headers, null);
        final Iterator<String>  ti  = new BasicTokenIterator(hit);

        Assert.assertThrows(NoSuchElementException.class, () -> ti.next());
        Assert.assertThrows(UnsupportedOperationException.class, () -> ti.remove());
    }

}

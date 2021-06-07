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
import org.apache.hc.core5.http.HeaderElement;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link BasicHeaderElementIterator}.
 *
 */
public class TestBasicHeaderElementIterator {

    @Test
    public void testMultiHeader() {
        final Header[] headers = new Header[]{
                new BasicHeader("Name", "value0"),
                new BasicHeader("Name", "value1")
        };

        final Iterator<HeaderElement> hei = new BasicHeaderElementIterator(
                new BasicHeaderIterator(headers, "Name"));

        Assert.assertTrue(hei.hasNext());
        HeaderElement elem = hei.next();
        Assert.assertEquals("The two header values must be equal",
                "value0", elem.getName());

        Assert.assertTrue(hei.hasNext());
        elem = hei.next();
        Assert.assertEquals("The two header values must be equal",
                "value1", elem.getName());

        Assert.assertFalse(hei.hasNext());
        Assert.assertThrows(NoSuchElementException.class, () -> hei.next());

        Assert.assertFalse(hei.hasNext());
        Assert.assertThrows(NoSuchElementException.class, () -> hei.next());
    }

    @Test
    public void testMultiHeaderSameLine() {
        final Header[] headers = new Header[]{
                new BasicHeader("name", "value0,value1"),
                new BasicHeader("nAme", "cookie1=1,cookie2=2")
        };

        final Iterator<HeaderElement> hei = new BasicHeaderElementIterator(
                new BasicHeaderIterator(headers, "Name"));

        HeaderElement elem = hei.next();
        Assert.assertEquals("The two header values must be equal",
                "value0", elem.getName());
        elem = hei.next();
        Assert.assertEquals("The two header values must be equal",
                "value1", elem.getName());
        elem = hei.next();
        Assert.assertEquals("The two header values must be equal",
                "cookie1", elem.getName());
        Assert.assertEquals("The two header values must be equal",
                "1", elem.getValue());

        elem = hei.next();
        Assert.assertEquals("The two header values must be equal",
                "cookie2", elem.getName());
        Assert.assertEquals("The two header values must be equal",
                "2", elem.getValue());
    }

    @Test
    public void testFringeCases() {
        final Header[] headers = new Header[]{
                new BasicHeader("Name", null),
                new BasicHeader("Name", "    "),
                new BasicHeader("Name", ",,,")
        };

        final Iterator<HeaderElement> hei = new BasicHeaderElementIterator(
                new BasicHeaderIterator(headers, "Name"));

        Assert.assertFalse(hei.hasNext());
        Assert.assertThrows(NoSuchElementException.class, () -> hei.next());
        Assert.assertFalse(hei.hasNext());
        Assert.assertThrows(NoSuchElementException.class, () -> hei.next());
    }

}

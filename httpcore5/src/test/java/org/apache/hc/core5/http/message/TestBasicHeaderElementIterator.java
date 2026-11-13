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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BasicHeaderElementIterator}.
 *
 */
class TestBasicHeaderElementIterator {

    @Test
    void testMultiHeader() {
        final Header[] headers = new Header[]{
                new BasicHeader("Name", "value0"),
                new BasicHeader("Name", "value1")
        };

        final Iterator<HeaderElement> hei = new BasicHeaderElementIterator(
                new BasicHeaderIterator(headers, "Name"));

        Assertions.assertThat(hei.hasNext()).isTrue();
        HeaderElement elem = hei.next();
        Assertions.assertThat(elem.getName()).withFailMessage("The two header values must be equal").isEqualTo("value0");

        Assertions.assertThat(hei.hasNext()).isTrue();
        elem = hei.next();
        Assertions.assertThat(elem.getName()).withFailMessage("The two header values must be equal").isEqualTo("value1");

        Assertions.assertThat(hei.hasNext()).isFalse();
        Assertions.assertThatThrownBy(() ->
                        hei.next())
                .isInstanceOf(NoSuchElementException.class);

        Assertions.assertThat(hei.hasNext()).isFalse();
        Assertions.assertThatThrownBy(() ->
                        hei.next())
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void testMultiHeaderSameLine() {
        final Header[] headers = new Header[]{
                new BasicHeader("name", "value0,value1"),
                new BasicHeader("nAme", "cookie1=1,cookie2=2")
        };

        final Iterator<HeaderElement> hei = new BasicHeaderElementIterator(
                new BasicHeaderIterator(headers, "Name"));

        HeaderElement elem = hei.next();
        Assertions.assertThat(elem.getName()).withFailMessage("The two header values must be equal").isEqualTo("value0");
        elem = hei.next();
        Assertions.assertThat(elem.getName()).withFailMessage("The two header values must be equal").isEqualTo("value1");
        elem = hei.next();
        Assertions.assertThat(elem.getName()).withFailMessage("The two header values must be equal").isEqualTo("cookie1");
        Assertions.assertThat(elem.getValue()).withFailMessage("The two header values must be equal").isEqualTo("1");

        elem = hei.next();
        Assertions.assertThat(elem.getName()).withFailMessage("The two header values must be equal").isEqualTo("cookie2");
        Assertions.assertThat(elem.getValue()).withFailMessage("The two header values must be equal").isEqualTo("2");
    }

    @Test
    void testFringeCases() {
        final Header[] headers = new Header[]{
                new BasicHeader("Name", null),
                new BasicHeader("Name", "    "),
                new BasicHeader("Name", ",,,")
        };

        final Iterator<HeaderElement> hei = new BasicHeaderElementIterator(
                new BasicHeaderIterator(headers, "Name"));

        Assertions.assertThat(hei.hasNext()).isFalse();
        Assertions.assertThatThrownBy(() ->
                        hei.next())
                .isInstanceOf(NoSuchElementException.class);
        Assertions.assertThat(hei.hasNext()).isFalse();
        Assertions.assertThatThrownBy(() ->
                        hei.next())
                .isInstanceOf(NoSuchElementException.class);
    }

}

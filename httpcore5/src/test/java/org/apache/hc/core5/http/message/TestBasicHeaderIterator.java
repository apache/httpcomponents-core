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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 * Tests for {@link Iterator} of {@link org.apache.hc.core5.http.Header}s.
 *
 */
class TestBasicHeaderIterator {

    @Test
    void testAllSame() {
        final Header[] headers = new Header[]{
            new BasicHeader("Name", "value0"),
            new BasicHeader("nAme", "value1, value1.1"),
            new BasicHeader("naMe", "value2=whatever"),
            new BasicHeader("namE", "value3;tag=nil"),
        };

        // without filter
        Iterator<Header> hit = new BasicHeaderIterator(headers, null);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("0").isEqualTo(headers[0]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("1").isEqualTo(headers[1]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("2").isEqualTo(headers[2]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("3").isEqualTo(headers[3]);
        Assertions.assertThat(hit.hasNext()).isFalse();

        // with filter
        hit = new BasicHeaderIterator(headers, "name");
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("0").isEqualTo(headers[0]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("1").isEqualTo(headers[1]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("2").isEqualTo(headers[2]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("3").isEqualTo(headers[3]);
        Assertions.assertThat(hit.hasNext()).isFalse();
    }


    @Test
    void testFirstLastOneNone() {
        final Header[] headers = new Header[]{
                new BasicHeader("match", "value0"),
                new BasicHeader("mismatch", "value1, value1.1"),
                new BasicHeader("single", "value2=whatever"),
                new BasicHeader("match", "value3;tag=nil"),
        };

        // without filter
        Iterator<Header> hit = new BasicHeaderIterator(headers, null);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("0").isEqualTo(headers[0]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("1").isEqualTo(headers[1]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("2").isEqualTo(headers[2]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("3").isEqualTo(headers[3]);
        Assertions.assertThat(hit.hasNext()).isFalse();

        // with filter, first & last
        hit = new BasicHeaderIterator(headers, "match");
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).isEqualTo(headers[0]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).isEqualTo(headers[3]);
        Assertions.assertThat(hit.hasNext()).isFalse();

        // with filter, one match
        hit = new BasicHeaderIterator(headers, "single");
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).isEqualTo(headers[2]);
        Assertions.assertThat(hit.hasNext()).isFalse();

        // with filter, no match
        hit = new BasicHeaderIterator(headers, "way-off");
        Assertions.assertThat(hit.hasNext()).isFalse();
    }


    @Test
    void testInterspersed() {
        final Header[] headers = new Header[]{
            new BasicHeader("yellow", "00"),
            new BasicHeader("maroon", "01"),
            new BasicHeader("orange", "02"),
            new BasicHeader("orange", "03"),
            new BasicHeader("orange", "04"),
            new BasicHeader("yellow", "05"),
            new BasicHeader("maroon", "06"),
            new BasicHeader("maroon", "07"),
            new BasicHeader("maroon", "08"),
            new BasicHeader("yellow", "09"),
            new BasicHeader("maroon", "0a"),
            new BasicHeader("yellow", "0b"),
            new BasicHeader("orange", "0c"),
            new BasicHeader("yellow", "0d"),
            new BasicHeader("orange", "0e"),
        };

        // without filter
        Iterator<Header> hit = new BasicHeaderIterator(headers, null);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("0").isEqualTo(headers[0]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("1").isEqualTo(headers[1]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("2").isEqualTo(headers[2]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("3").isEqualTo(headers[3]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("4").isEqualTo(headers[4]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("5").isEqualTo(headers[5]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("6").isEqualTo(headers[6]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("7").isEqualTo(headers[7]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("8").isEqualTo(headers[8]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("9").isEqualTo(headers[9]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("a").isEqualTo(headers[10]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("b").isEqualTo(headers[11]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("c").isEqualTo(headers[12]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("d").isEqualTo(headers[13]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("e").isEqualTo(headers[14]);
        Assertions.assertThat(hit.hasNext()).isFalse();

        // yellow 0, 5, 9, 11, 13
        hit = new BasicHeaderIterator(headers, "Yellow");
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).isEqualTo(headers[0]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("5").isEqualTo(headers[5]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("9").isEqualTo(headers[9]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("b").isEqualTo(headers[11]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("d").isEqualTo(headers[13]);
        Assertions.assertThat(hit.hasNext()).isFalse();

        // maroon 1, 6, 7, 8, 10
        hit = new BasicHeaderIterator(headers, "marOOn");
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("1").isEqualTo(headers[1]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("6").isEqualTo(headers[6]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("7").isEqualTo(headers[7]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("8").isEqualTo(headers[8]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("a").isEqualTo(headers[10]);
        Assertions.assertThat(hit.hasNext()).isFalse();

        // orange 2, 3, 4, 12, 14
        hit = new BasicHeaderIterator(headers, "OranGe");
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).isEqualTo(headers[2]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).isEqualTo(headers[3]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("4").isEqualTo(headers[4]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("b").isEqualTo(headers[12]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("e").isEqualTo(headers[14]);
        Assertions.assertThat(hit.hasNext()).isFalse();
    }


    @Test
    void testInvalid() {
        Assertions.assertThatThrownBy(() ->
                        new BasicHeaderIterator(null, "whatever"))
                .isInstanceOf(NullPointerException.class);
        // this is not invalid
        final Iterator<Header> hit = new BasicHeaderIterator(new Header[0], "whatever");
        Assertions.assertThat(hit.hasNext()).isFalse();

        // but this is
        Assertions.assertThatThrownBy(() ->
                        hit.next())
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void testRemaining() {
        // to satisfy Clover and take coverage to 100%

        final Header[] headers = new Header[]{
            new BasicHeader("Name", "value0"),
            new BasicHeader("nAme", "value1, value1.1"),
            new BasicHeader("naMe", "value2=whatever"),
            new BasicHeader("namE", "value3;tag=nil"),
        };

        // without filter, using plain next()
        final Iterator<Header> hit = new BasicHeaderIterator(headers, null);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("0").isEqualTo(headers[0]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("1").isEqualTo(headers[1]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("2").isEqualTo(headers[2]);
        Assertions.assertThat(hit.hasNext()).isTrue();
        Assertions.assertThat(hit.next()).withFailMessage("3").isEqualTo(headers[3]);
        Assertions.assertThat(hit.hasNext()).isFalse();

        final Iterator<Header> hit2 = new BasicHeaderIterator(headers, null);
        Assertions.assertThat(hit2.hasNext()).isTrue();
        Assertions.assertThatThrownBy(() ->
                        hit2.remove())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

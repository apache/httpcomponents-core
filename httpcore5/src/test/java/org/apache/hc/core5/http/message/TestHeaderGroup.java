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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Iterator;

import org.apache.hc.core5.http.Header;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HeaderGroup}.
 *
 */
class TestHeaderGroup {

    @Test
    void testConstructor() {
        final HeaderGroup headergroup = new HeaderGroup();
        Assertions.assertThat(headergroup.getHeaders()).isNotNull();
        Assertions.assertThat(headergroup.getHeaders().length).isEqualTo(0);
    }

    @Test
    void testClear() {
        final HeaderGroup headergroup = new HeaderGroup();
        headergroup.addHeader(new BasicHeader("name", "value"));
        Assertions.assertThat(headergroup.getHeaders().length).isEqualTo(1);
        headergroup.clear();
        Assertions.assertThat(headergroup.getHeaders().length).isEqualTo(0);
    }

    @Test
    void testAddRemoveHeader() {
        final HeaderGroup headerGroup = new HeaderGroup();
        final Header header = new BasicHeader("name", "value");
        headerGroup.addHeader(header);
        headerGroup.addHeader(null);
        Assertions.assertThat(headerGroup.getHeaders().length).isEqualTo(1);
        Assertions.assertThat(headerGroup.removeHeader(header)).isTrue();
        Assertions.assertThat(headerGroup.removeHeader(null)).isFalse();
        Assertions.assertThat(headerGroup.getHeaders().length).isEqualTo(0);
    }

    @Test
    void testAddRemoveHeaders() {
        final HeaderGroup headergroup = new HeaderGroup();
        final Header header = new BasicHeader("name", "value");
        headergroup.addHeader(header);
        headergroup.addHeader(header);
        Assertions.assertThat(headergroup.getHeaders().length).isEqualTo(2);
        Assertions.assertThat(headergroup.removeHeaders((Header) null)).isFalse();
        Assertions.assertThat(headergroup.removeHeaders(header)).isTrue();
        Assertions.assertThat(headergroup.removeHeaders((Header) null)).isFalse();
        Assertions.assertThat(headergroup.getHeaders().length).isEqualTo(0);
    }

    @Test
    void testAddRemoveHeaderWithDifferentButEqualHeaders() {
        final HeaderGroup headergroup = new HeaderGroup();
        final Header header = new BasicHeader("name", "value");
        final Header header2 = new BasicHeader("name", "value");
        headergroup.addHeader(header);
        Assertions.assertThat(headergroup.getHeaders().length).isEqualTo(1);
        Assertions.assertThat(headergroup.removeHeader(header2)).isTrue();
        Assertions.assertThat(headergroup.getHeaders().length).isEqualTo(0);
    }

    @Test
    void testUpdateHeader() {
        final HeaderGroup headergroup = new HeaderGroup();
        final Header header1 = new BasicHeader("name1", "value1");
        final Header header2 = new BasicHeader("name2", "value2");
        final Header header3 = new BasicHeader("name3", "value3");
        headergroup.addHeader(header1);
        headergroup.addHeader(header2);
        headergroup.addHeader(header3);
        headergroup.setHeader(new BasicHeader("name2", "newvalue"));
        headergroup.setHeader(new BasicHeader("name4", "value4"));
        headergroup.setHeader(null);
        Assertions.assertThat(headergroup.getHeaders().length).isEqualTo(4);
        Assertions.assertThat(headergroup.getFirstHeader("name2").getValue()).isEqualTo("newvalue");
    }

    @Test
    void testSetHeaders() {
        final HeaderGroup headergroup = new HeaderGroup();
        final Header header1 = new BasicHeader("name1", "value1");
        final Header header2 = new BasicHeader("name2", "value2");
        final Header header3 = new BasicHeader("name3", "value3");
        headergroup.addHeader(header1);
        headergroup.setHeaders(header2, header3);
        Assertions.assertThat(headergroup.getHeaders().length).isEqualTo(2);
        Assertions.assertThat(headergroup.getHeaders("name1").length).isEqualTo(0);
        Assertions.assertThat(headergroup.containsHeader("name1")).isFalse();
        Assertions.assertThat(headergroup.getHeaders("name2").length).isEqualTo(1);
        Assertions.assertThat(headergroup.containsHeader("name2")).isTrue();
        Assertions.assertThat(headergroup.getHeaders("name3").length).isEqualTo(1);
        Assertions.assertThat(headergroup.containsHeader("name3")).isTrue();
        headergroup.setHeaders();
        Assertions.assertThat(headergroup.getHeaders().length).isEqualTo(0);
    }

    @Test
    void testFirstLastHeaders() {
        final HeaderGroup headergroup = new HeaderGroup();
        final Header header1 = new BasicHeader("name", "value1");
        final Header header2 = new BasicHeader("name", "value2");
        final Header header3 = new BasicHeader("name", "value3");
        headergroup.setHeaders(header1, header2, header3);

        Assertions.assertThat(headergroup.getFirstHeader("whatever")).isNull();
        Assertions.assertThat(headergroup.getLastHeader("whatever")).isNull();

        Assertions.assertThat(headergroup.getFirstHeader("name").getValue()).isEqualTo("value1");
        Assertions.assertThat(headergroup.getLastHeader("name").getValue()).isEqualTo("value3");
    }

    @Test
    void testCondensedHeader() {
        final HeaderGroup headergroup = new HeaderGroup();
        Assertions.assertThat(headergroup.getCondensedHeader("name")).isNull();

        final Header header1 = new BasicHeader("name", "value1");
        final Header header2 = new BasicHeader("name", "value2");
        final Header header3 = new BasicHeader("name", "value3");
        headergroup.setHeaders(header1, header2, header3);

        Assertions.assertThat(headergroup.getCondensedHeader("name").getValue()).isEqualTo("value1, value2, value3");

        headergroup.setHeaders(header1);
        Assertions.assertThat(headergroup.getCondensedHeader("name")).isEqualTo(header1);
    }

    @Test
    void testEmptyListIterator() {
        final HeaderGroup headergroup = new HeaderGroup();
        final Iterator<Header> i = headergroup.headerIterator();
        Assertions.assertThat(i).isNotNull();
        Assertions.assertThat(i.hasNext()).isFalse();
    }

    @Test
    void testEmptyListIteratorByName() {
        final HeaderGroup headergroup = new HeaderGroup();
        final Iterator<Header> i = headergroup.headerIterator("some-header");
        Assertions.assertThat(i).isNotNull();
        Assertions.assertThat(i.hasNext()).isFalse();
    }

    @Test
    void testNonEmptyListIteratorByName() {
        final HeaderGroup headergroup = new HeaderGroup();
        headergroup.setHeaders(
                new BasicHeader("a", "a-one"),
                new BasicHeader("b", "b-one"),
                new BasicHeader("a", "a-two"),
                new BasicHeader("b", "b-two"),
                new BasicHeader("b", "b-three"));
        final Iterator<Header> it1 = headergroup.headerIterator("a");
        Assertions.assertThat(it1).isNotNull();
        Assertions.assertThat(it1.hasNext()).isTrue();
        Assertions.assertThat(it1.next().getValue()).isEqualTo("a-one");
        Assertions.assertThat(it1.hasNext()).isTrue();
        Assertions.assertThat(it1.next().getValue()).isEqualTo("a-two");
        Assertions.assertThat(it1.hasNext()).isFalse();

        final Iterator<Header> it2 = headergroup.headerIterator("b");
        Assertions.assertThat(it2).isNotNull();
        Assertions.assertThat(it2.hasNext()).isTrue();
        Assertions.assertThat(it2.next().getValue()).isEqualTo("b-one");
        Assertions.assertThat(it2.hasNext()).isTrue();
        Assertions.assertThat(it2.next().getValue()).isEqualTo("b-two");
        Assertions.assertThat(it2.hasNext()).isTrue();
        Assertions.assertThat(it2.next().getValue()).isEqualTo("b-three");
        Assertions.assertThat(it2.hasNext()).isFalse();
    }


    @Test
    void testIteratorByNameRemoveBeforeNext() {
        final HeaderGroup headerGroup = new HeaderGroup();
        final Header headerA = new BasicHeader("a", "a-one");
        final Header headerB = new BasicHeader("b", "b-one");
        headerGroup.setHeaders(headerA, headerB);

        final Iterator<Header> iterator = headerGroup.headerIterator("b");

        Assertions.assertThatThrownBy(iterator::remove).isInstanceOf(IllegalStateException.class);
        Assertions.assertThat(headerGroup.getHeaders())
                .containsExactly(new Header[]{headerA, headerB});
    }

    @Test
    void testHeaderRemove() {
        final HeaderGroup headergroup = new HeaderGroup();
        final Header header1 = new BasicHeader("name", "value1");
        final Header header2 = new BasicHeader("name", "value2");
        final Header header3 = new BasicHeader("name", "value3");
        headergroup.setHeaders(header1, header2, header3);
        final Iterator<Header> i = headergroup.headerIterator();
        Assertions.assertThat(i).isNotNull();
        Assertions.assertThat(i.hasNext()).isTrue();
        i.next();
        Assertions.assertThat(i.hasNext()).isTrue();
        i.next();
        i.remove();
        Assertions.assertThat(headergroup.getHeaders().length).isEqualTo(2);
        Assertions.assertThat(i.hasNext()).isTrue();
        i.next();
        i.remove();
        Assertions.assertThat(headergroup.getHeaders().length).isEqualTo(1);
        Assertions.assertThat(i.hasNext()).isFalse();
    }

    @Test
    void testSerialization() throws Exception {
        final HeaderGroup orig = new HeaderGroup();
        final Header header1 = new BasicHeader("name", "value1");
        final Header header2 = new BasicHeader("name", "value2");
        final Header header3 = new BasicHeader("name", "value3");
        orig.setHeaders(header1, header2, header3);
        final ByteArrayOutputStream outbuffer = new ByteArrayOutputStream();
        try (ObjectOutputStream outStream = new ObjectOutputStream(outbuffer)) {
            outStream.writeObject(orig);
        }
        final byte[] raw = outbuffer.toByteArray();
        final ByteArrayInputStream inBuffer = new ByteArrayInputStream(raw);
        final ObjectInputStream inStream = new ObjectInputStream(inBuffer);
        final HeaderGroup clone = (HeaderGroup) inStream.readObject();
        final Header[] headers1 = orig.getHeaders();
        final Header[] headers2 = clone.getHeaders();
        Assertions.assertThat(headers1).isNotNull();
        Assertions.assertThat(headers2).isNotNull();
        Assertions.assertThat(headers2.length).isEqualTo(headers1.length);
        for (int i = 0; i < headers1.length; i++) {
            Assertions.assertThat(headers2[i].getName()).isEqualTo(headers1[i].getName());
            Assertions.assertThat(headers2[i].getValue()).isEqualTo(headers1[i].getValue());
        }
    }

}

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

package org.apache.hc.core5.net;


import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestWWWFormCodec {

    private static final String CH_HELLO = "\u0047\u0072\u00FC\u0065\u007A\u0069\u005F\u007A\u00E4\u006D\u00E4";
    private static final String RU_HELLO = "\u0412\u0441\u0435\u043C\u005F\u043F\u0440\u0438\u0432\u0435\u0442";

    private static List<NameValuePair> parse(final String params) {
        return WWWFormCodec.parse(params, StandardCharsets.UTF_8);
    }

    @Test
    void testParse() {
        Assertions.assertThat(parse(""))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Collections.emptyList());
        Assertions.assertThat(parse("Name0"))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("Name0", null)));
        Assertions.assertThat(parse("Name1=Value1"))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("Name1", "Value1")));
        Assertions.assertThat(parse("Name2="))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("Name2", "")));
        Assertions.assertThat(parse(" Name3  "))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("Name3", null)));
        Assertions.assertThat(parse("Name4=Value%204%21"))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("Name4", "Value 4!")));
        Assertions.assertThat(parse("Name4=Value%2B4%21"))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("Name4", "Value+4!")));
        Assertions.assertThat(parse("Name4=Value%204%21%20%214"))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("Name4", "Value 4! !4")));
        Assertions.assertThat(parse("Name5=aaa&Name6=bbb"))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("Name5", "aaa"), Assertions.tuple("Name6", "bbb")));
        Assertions.assertThat(parse("Name7=aaa&Name7=b%2Cb&Name7=ccc"))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("Name7", "aaa"), Assertions.tuple("Name7", "b,b"), Assertions.tuple("Name7", "ccc")));
        Assertions.assertThat(parse("Name8=xx%2C%20%20yy%20%20%2Czz"))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("Name8", "xx,  yy  ,zz")));
        Assertions.assertThat(parse("price=10%20%E2%82%AC"))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("price", "10 \u20AC")));
        Assertions.assertThat(parse("a=b\"c&d=e"))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("a", "b\"c"), Assertions.tuple("d", "e")));
        Assertions.assertThat(parse("russian=" + PercentCodec.encode(RU_HELLO, StandardCharsets.UTF_8) +
                        "&swiss=" + PercentCodec.encode(CH_HELLO, StandardCharsets.UTF_8)))
                .extracting(nvp -> Assertions.tuple(nvp.getName(), nvp.getValue()))
                .isEqualTo(Arrays.asList(Assertions.tuple("russian", RU_HELLO), Assertions.tuple("swiss", CH_HELLO)));
    }

    private static String format(final NameValuePair... nvps) {
        return WWWFormCodec.format(Arrays.asList(nvps), StandardCharsets.UTF_8);
    }

    @Test
    void testFormat() {
        Assertions.assertThat(format(new BasicNameValuePair("Name0", null))).isEqualTo("Name0");
        Assertions.assertThat(format(new BasicNameValuePair("Name1", "Value1"))).isEqualTo("Name1=Value1");
        Assertions.assertThat(format(new BasicNameValuePair("Name2", ""))).isEqualTo("Name2=");
        Assertions.assertThat(format(new BasicNameValuePair("Name4", "Value 4&"))).isEqualTo("Name4=Value+4%26");
        Assertions.assertThat(format(new BasicNameValuePair("Name4", "Value+4&"))).isEqualTo("Name4=Value%2B4%26");
        Assertions.assertThat(format(new BasicNameValuePair("Name4", "Value 4& =4"))).isEqualTo("Name4=Value+4%26+%3D4");
        Assertions.assertThat(format(
                new BasicNameValuePair("Name5", "aaa"),
                new BasicNameValuePair("Name6", "bbb"))).isEqualTo("Name5=aaa&Name6=bbb");
        Assertions.assertThat(format(
                new BasicNameValuePair("Name7", "aaa"),
                new BasicNameValuePair("Name7", "b,b"),
                new BasicNameValuePair("Name7", "ccc")
        )).isEqualTo("Name7=aaa&Name7=b%2Cb&Name7=ccc");
        Assertions.assertThat(format(new BasicNameValuePair("Name8", "xx,  yy  ,zz"))).isEqualTo("Name8=xx%2C++yy++%2Czz");
        Assertions.assertThat(format(
                new BasicNameValuePair("russian", RU_HELLO),
                new BasicNameValuePair("swiss", CH_HELLO))).isEqualTo("russian=" + PercentCodec.encode(RU_HELLO, StandardCharsets.UTF_8) +
                "&swiss=" + PercentCodec.encode(CH_HELLO, StandardCharsets.UTF_8));
    }

}

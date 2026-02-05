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
import java.util.List;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.NameValuePairListMatcher;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestWWWFormCodec {

    private static final String CH_HELLO = "\u0047\u0072\u00FC\u0065\u007A\u0069\u005F\u007A\u00E4\u006D\u00E4";
    private static final String RU_HELLO = "\u0412\u0441\u0435\u043C\u005F\u043F\u0440\u0438\u0432\u0435\u0442";

    private static List<NameValuePair> parse(final String params) {
        return WWWFormCodec.parse(params, StandardCharsets.UTF_8);
    }

    @Test
    void testParse() {
        NameValuePairListMatcher.assertEmpty(parse(""));
        NameValuePairListMatcher.assertEqualsTo(parse("Name0"), new BasicNameValuePair("Name0", null));
        NameValuePairListMatcher.assertEqualsTo(parse("Name1=Value1"), new BasicNameValuePair("Name1", "Value1"));
        NameValuePairListMatcher.assertEqualsTo(parse("Name2="), new BasicNameValuePair("Name2", ""));
        NameValuePairListMatcher.assertEqualsTo(parse(" Name3  "), new BasicNameValuePair("Name3", null));
        NameValuePairListMatcher.assertEqualsTo(parse("Name4=Value%204%21"), new BasicNameValuePair("Name4", "Value 4!"));
        NameValuePairListMatcher.assertEqualsTo(parse("Name4=Value%2B4%21"), new BasicNameValuePair("Name4", "Value+4!"));
        NameValuePairListMatcher.assertEqualsTo(parse("Name4=Value%204%21%20%214"), new BasicNameValuePair("Name4", "Value 4! !4"));
        NameValuePairListMatcher.assertEqualsTo(parse("Name5=aaa&Name6=bbb"),
                new BasicNameValuePair("Name5", "aaa"),
                new BasicNameValuePair("Name6", "bbb"));
        NameValuePairListMatcher.assertEqualsTo(parse("Name7=aaa&Name7=b%2Cb&Name7=ccc"),
                new BasicNameValuePair("Name7", "aaa"),
                new BasicNameValuePair("Name7", "b,b"),
                new BasicNameValuePair("Name7", "ccc"));
        NameValuePairListMatcher.assertEqualsTo(parse("Name8=xx%2C%20%20yy%20%20%2Czz"), new BasicNameValuePair("Name8", "xx,  yy  ,zz"));
        NameValuePairListMatcher.assertEqualsTo(parse("price=10%20%E2%82%AC"), new BasicNameValuePair("price", "10 \u20AC"));
        NameValuePairListMatcher.assertEqualsTo(parse("a=b\"c&d=e"),
                new BasicNameValuePair("a", "b\"c"),
                new BasicNameValuePair("d", "e"));
        NameValuePairListMatcher.assertEqualsTo(parse("russian=" + PercentCodec.encode(RU_HELLO, StandardCharsets.UTF_8) +
                        "&swiss=" + PercentCodec.encode(CH_HELLO, StandardCharsets.UTF_8)),
                new BasicNameValuePair("russian", RU_HELLO),
                new BasicNameValuePair("swiss", CH_HELLO));
    }

    private static String format(final NameValuePair... nvps) {
        return WWWFormCodec.format(Arrays.asList(nvps), StandardCharsets.UTF_8);
    }

    @Test
    void testFormat() {
        Assertions.assertEquals("Name0", format(new BasicNameValuePair("Name0", null)));
        Assertions.assertEquals("Name1=Value1", format(new BasicNameValuePair("Name1", "Value1")));
        Assertions.assertEquals("Name2=", format(new BasicNameValuePair("Name2", "")));
        Assertions.assertEquals("Name4=Value+4%26", format(new BasicNameValuePair("Name4", "Value 4&")));
        Assertions.assertEquals("Name4=Value%2B4%26", format(new BasicNameValuePair("Name4", "Value+4&")));
        Assertions.assertEquals("Name4=Value+4%26+%3D4", format(new BasicNameValuePair("Name4", "Value 4& =4")));
        Assertions.assertEquals("Name5=aaa&Name6=bbb", format(
                new BasicNameValuePair("Name5", "aaa"),
                new BasicNameValuePair("Name6", "bbb")));
        Assertions.assertEquals("Name7=aaa&Name7=b%2Cb&Name7=ccc", format(
                new BasicNameValuePair("Name7", "aaa"),
                new BasicNameValuePair("Name7", "b,b"),
                new BasicNameValuePair("Name7", "ccc")
        ));
        Assertions.assertEquals("Name8=xx%2C++yy++%2Czz", format(new BasicNameValuePair("Name8", "xx,  yy  ,zz")));
        Assertions.assertEquals("russian=" + PercentCodec.encode(RU_HELLO, StandardCharsets.UTF_8) +
                "&swiss=" + PercentCodec.encode(CH_HELLO, StandardCharsets.UTF_8), format(
                new BasicNameValuePair("russian", RU_HELLO),
                new BasicNameValuePair("swiss", CH_HELLO)));
    }

}

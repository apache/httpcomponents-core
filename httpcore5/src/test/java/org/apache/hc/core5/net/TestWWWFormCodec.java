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
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;

public class TestWWWFormCodec {

    private static final String CH_HELLO = "\u0047\u0072\u00FC\u0065\u007A\u0069\u005F\u007A\u00E4\u006D\u00E4";
    private static final String RU_HELLO = "\u0412\u0441\u0435\u043C\u005F\u043F\u0440\u0438\u0432\u0435\u0442";

    private static List<NameValuePair> parse(final String params) {
        return WWWFormCodec.parse(params, StandardCharsets.UTF_8);
    }

    @Test
    public void testParse() throws Exception {
        MatcherAssert.assertThat(parse(""), NameValuePairListMatcher.isEmpty());
        MatcherAssert.assertThat(parse("Name0"),
                NameValuePairListMatcher.equalsTo(new BasicNameValuePair("Name0", null)));
        MatcherAssert.assertThat(parse("Name1=Value1"),
                NameValuePairListMatcher.equalsTo(new BasicNameValuePair("Name1", "Value1")));
        MatcherAssert.assertThat(parse("Name2="),
                NameValuePairListMatcher.equalsTo(new BasicNameValuePair("Name2", "")));
        MatcherAssert.assertThat(parse(" Name3  "),
                NameValuePairListMatcher.equalsTo(new BasicNameValuePair("Name3", null)));
        MatcherAssert.assertThat(parse("Name4=Value%204%21"),
                NameValuePairListMatcher.equalsTo(new BasicNameValuePair("Name4", "Value 4!")));
        MatcherAssert.assertThat(parse("Name4=Value%2B4%21"),
                NameValuePairListMatcher.equalsTo(new BasicNameValuePair("Name4", "Value+4!")));
        MatcherAssert.assertThat(parse("Name4=Value%204%21%20%214"),
                NameValuePairListMatcher.equalsTo(new BasicNameValuePair("Name4", "Value 4! !4")));
        MatcherAssert.assertThat(parse("Name5=aaa&Name6=bbb"),
                NameValuePairListMatcher.equalsTo(
                        new BasicNameValuePair("Name5", "aaa"),
                        new BasicNameValuePair("Name6", "bbb")));
        MatcherAssert.assertThat(parse("Name7=aaa&Name7=b%2Cb&Name7=ccc"),
                NameValuePairListMatcher.equalsTo(
                        new BasicNameValuePair("Name7", "aaa"),
                        new BasicNameValuePair("Name7", "b,b"),
                        new BasicNameValuePair("Name7", "ccc")));
        MatcherAssert.assertThat(parse("Name8=xx%2C%20%20yy%20%20%2Czz"),
                NameValuePairListMatcher.equalsTo(new BasicNameValuePair("Name8", "xx,  yy  ,zz")));
        MatcherAssert.assertThat(parse("price=10%20%E2%82%AC"),
                NameValuePairListMatcher.equalsTo(new BasicNameValuePair("price", "10 \u20AC")));
        MatcherAssert.assertThat(parse("a=b\"c&d=e"),
                NameValuePairListMatcher.equalsTo(
                        new BasicNameValuePair("a", "b\"c"),
                        new BasicNameValuePair("d", "e")));
        MatcherAssert.assertThat(parse("russian=" + PercentCodec.encode(RU_HELLO, StandardCharsets.UTF_8) +
                        "&swiss=" + PercentCodec.encode(CH_HELLO, StandardCharsets.UTF_8)),
                NameValuePairListMatcher.equalsTo(
                        new BasicNameValuePair("russian", RU_HELLO),
                        new BasicNameValuePair("swiss", CH_HELLO)));
    }

    private static String format(final NameValuePair... nvps) {
        return WWWFormCodec.format(Arrays.asList(nvps), StandardCharsets.UTF_8);
    }

    @Test
    public void testFormat() throws Exception {
        MatcherAssert.assertThat(format(new BasicNameValuePair("Name0", null)), CoreMatchers.equalTo("Name0"));
        MatcherAssert.assertThat(format(new BasicNameValuePair("Name1", "Value1")), CoreMatchers.equalTo("Name1=Value1"));
        MatcherAssert.assertThat(format(new BasicNameValuePair("Name2", "")), CoreMatchers.equalTo("Name2="));
        MatcherAssert.assertThat(format(new BasicNameValuePair("Name4", "Value 4&")),
                CoreMatchers.equalTo("Name4=Value+4%26"));
        MatcherAssert.assertThat(format(new BasicNameValuePair("Name4", "Value+4&")),
                CoreMatchers.equalTo("Name4=Value%2B4%26"));
        MatcherAssert.assertThat(format(new BasicNameValuePair("Name4", "Value 4& =4")),
                CoreMatchers.equalTo("Name4=Value+4%26+%3D4"));
        MatcherAssert.assertThat(format(
                new BasicNameValuePair("Name5", "aaa"),
                new BasicNameValuePair("Name6", "bbb")), CoreMatchers.equalTo("Name5=aaa&Name6=bbb"));
        MatcherAssert.assertThat(format(
                new BasicNameValuePair("Name7", "aaa"),
                new BasicNameValuePair("Name7", "b,b"),
                new BasicNameValuePair("Name7", "ccc")
        ), CoreMatchers.equalTo("Name7=aaa&Name7=b%2Cb&Name7=ccc"));
        MatcherAssert.assertThat(format(new BasicNameValuePair("Name8", "xx,  yy  ,zz")),
                CoreMatchers.equalTo("Name8=xx%2C++yy++%2Czz"));
        MatcherAssert.assertThat(format(
                new BasicNameValuePair("russian", RU_HELLO),
                new BasicNameValuePair("swiss", CH_HELLO)),
                CoreMatchers.equalTo("russian=" + PercentCodec.encode(RU_HELLO, StandardCharsets.UTF_8) +
                        "&swiss=" + PercentCodec.encode(CH_HELLO, StandardCharsets.UTF_8)));
    }

}

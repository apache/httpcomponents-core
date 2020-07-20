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

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;

/**
 * Unit tests for {@link PercentCodec}.
 */
public class TestPercentCodec {

    @Test
    public void testCoding() {
        final StringBuilder buf = new StringBuilder();
        PercentCodec.encode(buf, "blah!", StandardCharsets.UTF_8);
        PercentCodec.encode(buf, " ~ ", StandardCharsets.UTF_8);
        PercentCodec.encode(buf, "huh?", StandardCharsets.UTF_8);
        MatcherAssert.assertThat(buf.toString(), CoreMatchers.equalTo("blah%21%20~%20huh%3F"));
    }

    @Test
    public void testDecoding() {
        MatcherAssert.assertThat(PercentCodec.decode("blah%21%20~%20huh%3F", StandardCharsets.UTF_8),
                CoreMatchers.equalTo("blah! ~ huh?"));
        MatcherAssert.assertThat(PercentCodec.decode("blah%21+~%20huh%3F", StandardCharsets.UTF_8),
                CoreMatchers.equalTo("blah!+~ huh?"));
        MatcherAssert.assertThat(PercentCodec.decode("blah%21+~%20huh%3F", StandardCharsets.UTF_8, true),
                CoreMatchers.equalTo("blah! ~ huh?"));
    }

    @Test
    public void testDecodingPartialContent() {
        MatcherAssert.assertThat(PercentCodec.decode("blah%21%20%", StandardCharsets.UTF_8),
                CoreMatchers.equalTo("blah! %"));
        MatcherAssert.assertThat(PercentCodec.decode("blah%21%20%a", StandardCharsets.UTF_8),
                CoreMatchers.equalTo("blah! %a"));
        MatcherAssert.assertThat(PercentCodec.decode("blah%21%20%wa", StandardCharsets.UTF_8),
                CoreMatchers.equalTo("blah! %wa"));
    }

}

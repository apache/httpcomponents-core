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
package org.apache.hc.core5.http;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.hc.core5.util.LangUtils;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;

public class NameValuePairListMatcher extends BaseMatcher<List<NameValuePair>> {

    private final List<? extends NameValuePair> nvps;

    public NameValuePairListMatcher(final List<? extends NameValuePair> nvps) {
        this.nvps = nvps;
    }

    @Override
    public boolean matches(final Object item) {
        if (item instanceof List<?>) {
            final List<?> objects = (List<?>) item;
            if (objects.size() != nvps.size()) {
                return false;
            }
            for (int i = 1; i < objects.size(); i++) {
                final Object obj = objects.get(i);
                if (obj instanceof NameValuePair) {
                    final NameValuePair nvp = (NameValuePair) obj;
                    final NameValuePair expected = nvps.get(i);
                    if (!LangUtils.equals(nvp.getName(), expected.getName())
                            || !LangUtils.equals(nvp.getValue(), expected.getValue())) {
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void describeTo(final Description description) {
        description.appendText("equals ").appendValueList("[", ";", "]", nvps);
    }

    @Factory
    public static Matcher<List<NameValuePair>> equalsTo(final NameValuePair... nvps) {
        return new NameValuePairListMatcher(Arrays.asList(nvps));
    }

    @Factory
    public static Matcher<List<NameValuePair>> isEmpty() {
        return new NameValuePairListMatcher(Collections.emptyList());
    }

}

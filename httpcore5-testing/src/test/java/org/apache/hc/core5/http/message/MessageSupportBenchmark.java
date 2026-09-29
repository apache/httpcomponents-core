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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.RandomAccess;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(3)
public class MessageSupportBenchmark {

    @State(Scope.Thread)
    public static class FormatState {

        @Param({"array", "linked"})
        public String listType;

        @Param({"4", "8", "16", "32", "64", "256"})
        public int entryCount;

        private List<String> tokens;
        private List<HeaderElement> elements;
        private List<NameValuePair> parameters;
        private CharArrayBuffer tokenBuffer;
        private CharArrayBuffer elementBuffer;
        private CharArrayBuffer parameterBuffer;

        @Setup
        public void setup() {
            final List<String> tokenValues = new ArrayList<>(entryCount);
            final List<HeaderElement> elementValues = new ArrayList<>(entryCount);
            final List<NameValuePair> parameterValues = new ArrayList<>(entryCount);

            for (int i = 0; i < entryCount; i++) {
                tokenValues.add("token-" + i);
                elementValues.add(new BasicHeaderElement("element-" + i, "value-" + i));
                parameterValues.add(new BasicNameValuePair("param-" + i, "value-" + i));
            }

            if ("linked".equals(listType)) {
                tokens = new LinkedList<>(tokenValues);
                elements = new LinkedList<>(elementValues);
                parameters = new LinkedList<>(parameterValues);
            } else {
                tokens = tokenValues;
                elements = elementValues;
                parameters = parameterValues;
            }

            final int capacity = entryCount * 32 + 64;
            tokenBuffer = new CharArrayBuffer(capacity);
            elementBuffer = new CharArrayBuffer(capacity);
            parameterBuffer = new CharArrayBuffer(capacity);
        }

    }

    @Benchmark
    public int formatTokensIndexed(final FormatState state) {
        state.tokenBuffer.clear();
        formatTokensIndexed(state.tokenBuffer, state.tokens, null);
        return state.tokenBuffer.length();
    }

    @Benchmark
    public int formatTokensIterator(final FormatState state) {
        state.tokenBuffer.clear();
        formatTokensIterator(state.tokenBuffer, state.tokens, null);
        return state.tokenBuffer.length();
    }

    @Benchmark
    public int formatTokensAdaptive(final FormatState state) {
        state.tokenBuffer.clear();
        formatTokensAdaptive(state.tokenBuffer, state.tokens, null);
        return state.tokenBuffer.length();
    }

    @Benchmark
    public int formatElementsIndexed(final FormatState state) {
        state.elementBuffer.clear();
        formatElementsIndexed(state.elementBuffer, state.elements);
        return state.elementBuffer.length();
    }

    @Benchmark
    public int formatElementsIterator(final FormatState state) {
        state.elementBuffer.clear();
        formatElementsIterator(state.elementBuffer, state.elements);
        return state.elementBuffer.length();
    }

    @Benchmark
    public int formatElementsAdaptive(final FormatState state) {
        state.elementBuffer.clear();
        formatElementsAdaptive(state.elementBuffer, state.elements);
        return state.elementBuffer.length();
    }

    @Benchmark
    public int formatParametersIndexed(final FormatState state) {
        state.parameterBuffer.clear();
        formatParametersIndexed(state.parameterBuffer, state.parameters);
        return state.parameterBuffer.length();
    }

    @Benchmark
    public int formatParametersIterator(final FormatState state) {
        state.parameterBuffer.clear();
        formatParametersIterator(state.parameterBuffer, state.parameters);
        return state.parameterBuffer.length();
    }

    @Benchmark
    public int formatParametersAdaptive(final FormatState state) {
        state.parameterBuffer.clear();
        formatParametersAdaptive(state.parameterBuffer, state.parameters);
        return state.parameterBuffer.length();
    }

    private static void formatTokensIndexed(
            final CharArrayBuffer dst,
            final List<String> tokens,
            final UnaryOperator<String> transformation) {
        Args.notNull(dst, "Destination");
        if (tokens == null) {
            return;
        }
        for (int i = 0; i < tokens.size(); i++) {
            final String token = tokens.get(i);
            final String element = transformation != null ? transformation.apply(token) : token;
            if (i > 0) {
                dst.append(", ");
            }
            dst.append(element);
        }
    }

    private static void formatTokensIterator(
            final CharArrayBuffer dst,
            final List<String> tokens,
            final UnaryOperator<String> transformation) {
        Args.notNull(dst, "Destination");
        if (tokens == null) {
            return;
        }
        final Iterator<String> iterator = tokens.iterator();
        boolean first = true;
        while (iterator.hasNext()) {
            final String token = iterator.next();
            final String element = transformation != null ? transformation.apply(token) : token;
            if (!first) {
                dst.append(", ");
            }
            dst.append(element);
            first = false;
        }
    }

    private static void formatTokensAdaptive(
            final CharArrayBuffer dst,
            final List<String> tokens,
            final UnaryOperator<String> transformation) {
        Args.notNull(dst, "Destination");
        if (tokens == null) {
            return;
        }
        if (tokens instanceof RandomAccess) {
            for (int i = 0; i < tokens.size(); i++) {
                final String token = tokens.get(i);
                final String element = transformation != null ? transformation.apply(token) : token;
                if (i > 0) {
                    dst.append(", ");
                }
                dst.append(element);
            }
        } else {
            final Iterator<String> iterator = tokens.iterator();
            boolean first = true;
            while (iterator.hasNext()) {
                final String token = iterator.next();
                final String element = transformation != null ? transformation.apply(token) : token;
                if (!first) {
                    dst.append(", ");
                }
                dst.append(element);
                first = false;
            }
        }
    }

    private static void formatElementsIndexed(
            final CharArrayBuffer dst,
            final List<HeaderElement> elements) {
        Args.notNull(dst, "Destination");
        if (elements == null) {
            return;
        }
        for (int i = 0; i < elements.size(); i++) {
            final HeaderElement element = elements.get(i);
            if (i > 0) {
                dst.append(", ");
            }
            BasicHeaderValueFormatter.INSTANCE.formatHeaderElement(dst, element, false);
        }
    }

    private static void formatElementsIterator(
            final CharArrayBuffer dst,
            final List<HeaderElement> elements) {
        Args.notNull(dst, "Destination");
        if (elements == null) {
            return;
        }
        final Iterator<HeaderElement> iterator = elements.iterator();
        boolean first = true;
        while (iterator.hasNext()) {
            final HeaderElement element = iterator.next();
            if (!first) {
                dst.append(", ");
            }
            BasicHeaderValueFormatter.INSTANCE.formatHeaderElement(dst, element, false);
            first = false;
        }
    }

    private static void formatElementsAdaptive(
            final CharArrayBuffer dst,
            final List<HeaderElement> elements) {
        Args.notNull(dst, "Destination");
        if (elements == null) {
            return;
        }
        if (elements instanceof RandomAccess) {
            for (int i = 0; i < elements.size(); i++) {
                final HeaderElement element = elements.get(i);
                if (i > 0) {
                    dst.append(", ");
                }
                BasicHeaderValueFormatter.INSTANCE.formatHeaderElement(dst, element, false);
            }
        } else {
            final Iterator<HeaderElement> iterator = elements.iterator();
            boolean first = true;
            while (iterator.hasNext()) {
                final HeaderElement element = iterator.next();
                if (!first) {
                    dst.append(", ");
                }
                BasicHeaderValueFormatter.INSTANCE.formatHeaderElement(dst, element, false);
                first = false;
            }
        }
    }

    private static void formatParametersIndexed(
            final CharArrayBuffer dst,
            final List<NameValuePair> params) {
        Args.notNull(dst, "Destination");
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.size(); i++) {
            final NameValuePair param = params.get(i);
            if (i > 0) {
                dst.append("; ");
            }
            BasicHeaderValueFormatter.INSTANCE.formatNameValuePair(dst, param, false);
        }
    }

    private static void formatParametersIterator(
            final CharArrayBuffer dst,
            final List<NameValuePair> params) {
        Args.notNull(dst, "Destination");
        if (params == null) {
            return;
        }
        final Iterator<NameValuePair> iterator = params.iterator();
        boolean first = true;
        while (iterator.hasNext()) {
            final NameValuePair param = iterator.next();
            if (!first) {
                dst.append("; ");
            }
            BasicHeaderValueFormatter.INSTANCE.formatNameValuePair(dst, param, false);
            first = false;
        }
    }

    private static void formatParametersAdaptive(
            final CharArrayBuffer dst,
            final List<NameValuePair> params) {
        Args.notNull(dst, "Destination");
        if (params == null) {
            return;
        }
        if (params instanceof RandomAccess) {
            for (int i = 0; i < params.size(); i++) {
                final NameValuePair param = params.get(i);
                if (i > 0) {
                    dst.append("; ");
                }
                BasicHeaderValueFormatter.INSTANCE.formatNameValuePair(dst, param, false);
            }
        } else {
            final Iterator<NameValuePair> iterator = params.iterator();
            boolean first = true;
            while (iterator.hasNext()) {
                final NameValuePair param = iterator.next();
                if (!first) {
                    dst.append("; ");
                }
                BasicHeaderValueFormatter.INSTANCE.formatNameValuePair(dst, param, false);
                first = false;
            }
        }
    }

}
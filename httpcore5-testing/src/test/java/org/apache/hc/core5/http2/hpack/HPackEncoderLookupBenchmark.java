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
package org.apache.hc.core5.http2.hpack;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

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
@State(Scope.Thread)
public class HPackEncoderLookupBenchmark {

    @Param({"32", "64", "80", "256", "1024", "4096"})
    private int entryCount;

    private List<HPackEntry> entries;
    private String missingValue;

    @Setup
    public void setup() {
        final OutboundDynamicTable table = new OutboundDynamicTable(Integer.MAX_VALUE);

        for (int i = 0; i < entryCount; i++) {
            table.add(new HPackHeader("x-request-id", "req-" + i));
        }

        entries = table.getByName("x-request-id");
        missingValue = "not-present";
    }

    @Benchmark
    public int findFullMatchIndexed() {
        return findFullMatchIndexed(entries, missingValue);
    }

    @Benchmark
    public int findFullMatchIterator() {
        return findFullMatchIterator(entries, missingValue);
    }

    private static int findFullMatchIndexed(
            final List<HPackEntry> entries,
            final String value) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < entries.size(); i++) {
            final HPackEntry entry = entries.get(i);
            if (Objects.equals(value, entry.getHeader().getValue())) {
                return entry.getIndex();
            }
        }
        return 0;
    }

    private static int findFullMatchIterator(
            final List<HPackEntry> entries,
            final String value) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        for (final HPackEntry entry : entries) {
            if (Objects.equals(value, entry.getHeader().getValue())) {
                return entry.getIndex();
            }
        }
        return 0;
    }

}
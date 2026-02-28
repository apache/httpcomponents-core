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
package org.apache.hc.core5.pool;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class TestConnPoolGenerativeContracts {

    private static int intProp(final String name, final int def) {
        final String v = System.getProperty(name);
        return v != null ? Integer.parseInt(v) : def;
    }

    private static long longProp(final String name, final long def) {
        final String v = System.getProperty(name);
        return v != null ? Long.parseLong(v) : def;
    }

    @ParameterizedTest
    @EnumSource(PoolTestSupport.PoolType.class)
    @org.junit.jupiter.api.Timeout(value = 30, unit = TimeUnit.SECONDS)
    void fuzzContract(final PoolTestSupport.PoolType poolType) throws Exception {
        final int steps = intProp("hc.pool.fuzz.steps", 250);
        final int routeCount = intProp("hc.pool.fuzz.routes", 5);

        final String singleSeedProp = System.getProperty("hc.pool.fuzz.seed");
        final int seeds = intProp("hc.pool.fuzz.seeds", 3);
        final long baseSeed = longProp("hc.pool.fuzz.baseSeed", 1L);

        if (singleSeedProp != null) {
            final long seed = Long.parseLong(singleSeedProp);
            final ManagedConnPool<String, PoolTestSupport.DummyConn> pool = poolType.createPool(2, 8);
            ConnPoolContractFuzzer.run(poolType, pool, seed, steps, routeCount);
            return;
        }

        for (int i = 0; i < seeds; i++) {
            final long seed = baseSeed + i;
            final ManagedConnPool<String, PoolTestSupport.DummyConn> pool = poolType.createPool(2, 8);
            ConnPoolContractFuzzer.run(poolType, pool, seed, steps, routeCount);
        }
    }

}
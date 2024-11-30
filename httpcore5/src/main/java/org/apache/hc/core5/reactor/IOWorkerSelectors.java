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
package org.apache.hc.core5.reactor;

import java.util.concurrent.atomic.AtomicInteger;

final class IOWorkerSelectors {

    static IOWorkerSelector newSelector(final int workerCount, final int start) {
        return isPowerOfTwo(workerCount) ? new PowerOfTwoSelector(start) : new GenericSelector(start);
    }

    static IOWorkerSelector newSelector(final int workerCount) {
        return newSelector(workerCount, 0);
    }

    static boolean isPowerOfTwo(final int n) {
        return (n & -n) == n;
    }

    static final class PowerOfTwoSelector implements IOWorkerSelector {

        private final AtomicInteger idx;

        PowerOfTwoSelector(final int n) {
            this.idx = new AtomicInteger(n);
        }

        @Override
        public int select(final IOWorkerStats[] dispatchers) {
            return idx.getAndIncrement() & (dispatchers.length - 1);
        }

    }

    static final class GenericSelector implements IOWorkerSelector {

        private final AtomicInteger idx;

        GenericSelector(final int n) {
            this.idx = new AtomicInteger(n);
        }

        @Override
        public int select(final IOWorkerStats[] dispatchers) {
            return (idx.getAndIncrement() & Integer.MAX_VALUE) % dispatchers.length;
        }

    }

}

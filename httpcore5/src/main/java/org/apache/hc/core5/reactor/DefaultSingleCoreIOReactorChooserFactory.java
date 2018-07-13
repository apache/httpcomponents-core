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

public class DefaultSingleCoreIOReactorChooserFactory implements SingleCoreIOReactorChooserFactory {

    public static DefaultSingleCoreIOReactorChooserFactory INSTANCE = new DefaultSingleCoreIOReactorChooserFactory();

    @Override
    public SingleCoreIOReactorChooser newChooser(final SingleCoreIOReactor[] dispatchers) {
        if (isPowerOfTwo(dispatchers.length)) {
            return new PowerOfTwoSingleCoreIOReactorChooser(dispatchers);
        } else {
            return new GenericSingleCoreIOReactorChooser(dispatchers);
        }
    }

    private static boolean isPowerOfTwo(final int val) {
        return (val & -val) == val;
    }

    private static final class PowerOfTwoSingleCoreIOReactorChooser implements SingleCoreIOReactorChooser{
        private final AtomicInteger idx = new AtomicInteger(0);
        private final SingleCoreIOReactor[] dispatchers;

        PowerOfTwoSingleCoreIOReactorChooser(final SingleCoreIOReactor[] dispatchers) {
            this.dispatchers = dispatchers;
        }

        @Override
        public SingleCoreIOReactor next() {
            return dispatchers[idx.getAndIncrement() & (dispatchers.length - 1) ];
        }
    }

    private static final class GenericSingleCoreIOReactorChooser implements SingleCoreIOReactorChooser{
        private final AtomicInteger idx = new AtomicInteger(0);
        private final SingleCoreIOReactor[] dispatchers;

        GenericSingleCoreIOReactorChooser(final SingleCoreIOReactor[] dispatchers) {
            this.dispatchers = dispatchers;
        }

        @Override
        public SingleCoreIOReactor next() {
            return dispatchers[idx.getAndIncrement() % dispatchers.length];
        }
    }
}

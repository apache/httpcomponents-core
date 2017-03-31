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

package org.apache.hc.core5.util;

import java.util.concurrent.TimeUnit;

/**
 * Wraps a time (long) and TimeUnit.
 *
 * @since 5.0
 */
public class TimeValue {

    private final long duration;

    private final TimeUnit timeUnit;

    public TimeValue(final long duration, final TimeUnit timeUnit) {
        super();
        this.duration = duration;
        this.timeUnit = timeUnit;
    }

    public long convert(final TimeUnit sourceUnit) {
        return timeUnit.convert(duration, sourceUnit);
    }

    public long getDuration() {
        return duration;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public void sleep() throws InterruptedException {
        timeUnit.sleep(duration);
    }

    public void timedJoin(final Thread thread) throws InterruptedException {
        timeUnit.timedJoin(thread, duration);
    }

    public void timedWait(final Object obj) throws InterruptedException {
        timeUnit.timedWait(obj, duration);
    }

    public long toDays() {
        return timeUnit.toDays(duration);
    }

    public long toHours() {
        return timeUnit.toHours(duration);
    }

    public long toMicros() {
        return timeUnit.toMicros(duration);
    }

    public long toMillis() {
        return timeUnit.toMillis(duration);
    }

    public long toMinutes() {
        return timeUnit.toMinutes(duration);
    }

    public long toNanos() {
        return timeUnit.toNanos(duration);
    }

    public long toSeconds() {
        return timeUnit.toSeconds(duration);
    }

    @Override
    public String toString() {
        return String.format("%,d %s", Long.valueOf(duration), timeUnit);
    }
}

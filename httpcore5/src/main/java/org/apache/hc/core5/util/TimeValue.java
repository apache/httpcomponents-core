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

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * Represents a time value as a {@code long} time and {@link TimeUnit}.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class TimeValue {

    static final int UNDEFINED = -1;
    public static final TimeValue NEG_ONE_MILLISECONDS = TimeValue.of(UNDEFINED, TimeUnit.MILLISECONDS);
    public static final TimeValue NEG_ONE_SECONDS = TimeValue.of(UNDEFINED, TimeUnit.SECONDS);
    public static final TimeValue ZERO_MILLISECONDS = TimeValue.of(0, TimeUnit.MILLISECONDS);

    /**
     * Returns the given {@code long} value as an {@code int} where long values out of int range are returned as
     * {@link Integer#MIN_VALUE} and {@link Integer#MAX_VALUE}.
     *
     * <p>
     * For example: {@code TimeValue.asBoundInt(Long.MAX_VALUE)} returns {@code Integer.MAX_VALUE}.
     * </p>
     *
     * @param value
     *            a long value to convert
     * @return an int value bound within {@link Integer#MIN_VALUE} and {@link Integer#MAX_VALUE}.
     */
    public static int asBoundInt(final long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        } else if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    /**
     * Returns the given {@code timeValue} if it is not {@code null}, if {@code null} then returns the given
     * {@code defaultValue}.
     *
     * @param timeValue
     *            may be {@code null}
     * @param defaultValue
     *            may be {@code null}
     * @return {@code timeValue} or {@code defaultValue}
     */
    public static TimeValue defaultsTo(final TimeValue timeValue, final TimeValue defaultValue) {
        return timeValue != null ? timeValue : defaultValue;
    }

    /**
     * Returns the given {@code timeValue} if it is not {@code null}, if {@code null} then returns {@link #ZERO_MILLISECONDS}.
     *
     * @param timeValue
     *            may be {@code null}
     * @return {@code timeValue} or {@link #ZERO_MILLISECONDS}
     */
    public static TimeValue defaultsToZeroMillis(final TimeValue timeValue) {
        return defaultsTo(timeValue, ZERO_MILLISECONDS);
    }

    /**
     * Returns the given {@code timeValue} if it is not {@code null}, if {@code null} then returns
     * {@link #NEG_ONE_SECONDS}.
     *
     * @param timeValue
     *            may be {@code null}
     * @return {@code timeValue} or {@link #NEG_ONE_SECONDS}
     */
    public static TimeValue defaultsToNegativeOneMillisecond(final TimeValue timeValue) {
        return defaultsTo(timeValue, NEG_ONE_MILLISECONDS);
    }

    /**
     * Returns the given {@code timeValue} if it is not {@code null}, if {@code null} then returns
     * {@link #NEG_ONE_SECONDS}.
     *
     * @param timeValue
     *            may be {@code null}
     * @return {@code timeValue} or {@link #NEG_ONE_SECONDS}
     */
    public static TimeValue defaultsToNegativeOneSecond(final TimeValue timeValue) {
        return defaultsTo(timeValue, NEG_ONE_SECONDS);
    }

    /**
     * Creates a TimeValue.
     *
     * @param duration
     *            the time duration in the given {@code timeUnit}.
     * @param timeUnit
     *            the time unit for the given durarion.
     * @return a Timeout
     */
    public static TimeValue of(final long duration, final TimeUnit timeUnit) {
        return new TimeValue(duration, timeUnit);
    }

    public static TimeValue ofDays(final long days) {
        return of(days, TimeUnit.DAYS);
    }

    public static TimeValue ofHours(final long hours) {
        return of(hours, TimeUnit.HOURS);
    }

    public static TimeValue ofMillis(final long millis) {
        return of(millis, TimeUnit.MILLISECONDS);
    }

    public static TimeValue ofMinutes(final long minutes) {
        return of(minutes, TimeUnit.MINUTES);
    }

    public static TimeValue ofSeconds(final long seconds) {
        return of(seconds, TimeUnit.SECONDS);
    }

    public static boolean isPositive(final TimeValue timeValue) {
        return timeValue != null && timeValue.getDuration() > 0;
    }

    public static boolean isNonNegative(final TimeValue timeValue) {
        return timeValue != null && timeValue.getDuration() >= 0;
    }

    private final long duration;

    private final TimeUnit timeUnit;

    TimeValue(final long duration, final TimeUnit timeUnit) {
        super();
        this.duration = duration;
        this.timeUnit = Args.notNull(timeUnit, "timeUnit");
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

    public int toMillisIntBound() {
        return asBoundInt(toMillis());
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

    public int toSecondsIntBound() {
        return asBoundInt(toSeconds());
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof TimeValue) {
            final TimeValue that = (TimeValue) obj;
            return this.duration == that.duration &&
                    LangUtils.equals(this.timeUnit, that.timeUnit);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, Long.valueOf(duration));
        hash = LangUtils.hashCode(hash, timeUnit);
        return hash;
    }

    @Override
    public String toString() {
        return String.format("%,d %s", Long.valueOf(duration), timeUnit);
    }
}

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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * A deadline based on a UNIX time, the elapsed since 00:00:00 Coordinated Universal Time (UTC), Thursday, 1 January
 * 1970.
 *
 * @since 5.0
 */
public class Deadline {

    /**
     * The format used for parsing and formatting dates.
     */
    public static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    /**
     * A special internal value that marks a deadline as the longest possible.
     */
    private static final Instant INTERNAL_MAX_VALUE = Instant.MAX;

    /**
     * A special internal value that marks a deadline as the longest possible.
     */
    private static final long INTERNAL_MAX_VALUE_MILLIS = Instant.ofEpochMilli(Long.MAX_VALUE).toEpochMilli();

    /**
     * A special internal value that marks a deadline as the shortest possible.
     */
    private static final Instant INTERNAL_MIN_VALUE = Instant.MIN;

    /**
     * A special internal value that marks a deadline as the shortest possible.
     */
    private static final long INTERNAL_MIN_VALUE_MILLIS = Instant.EPOCH.toEpochMilli();

    /**
     * The maximum (longest-lived) deadline.
     */
    public static Deadline MAX_VALUE = new Deadline(INTERNAL_MAX_VALUE);

    /**
     * The minimum (shortest-lived) deadline.
     */
    public static Deadline MIN_VALUE = new Deadline(INTERNAL_MIN_VALUE);

    private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DATE_FORMAT);

    /**
     * Calculates a deadline with a given time in milliseconds plus a given time value. Non-positive time values
     * represent an indefinite timeout without a deadline.
     *
     * @param timeMillis A time in UNIX milliseconds, usually the current time.
     * @param timeValue time value to add to {@code timeMillis}.
     * @return a deadline representing the current time plus the given time value.
     */
    public static Deadline calculate(final long timeMillis, final TimeValue timeValue) {
        if (TimeValue.isPositive(timeValue)) {
            // TODO handle unlikely overflow
            final long deadline = timeMillis + timeValue.toMilliseconds();
            return deadline < 0 ? Deadline.MAX_VALUE : Deadline.fromUnixMilliseconds(deadline);
        }
        return Deadline.MAX_VALUE;
    }

    /**
     * Calculates a deadline from now plus a given time value. Non-positive time values
     * represent an indefinite timeout without a deadline.
     *
     * @param timeValue time value to add to {@code timeMillis}.
     * @return a deadline representing the current time plus the given time value.
     */
    public static Deadline calculate(final TimeValue timeValue) {
        return calculate(System.currentTimeMillis(), timeValue);
    }

    /**
     * Creates a deadline from an instant.
     *
     * @param value an instant.
     * @return a deadline.
     * @since 5.2
     */
    public static Deadline from(final Instant value) {
        if (value.equals(INTERNAL_MAX_VALUE)) {
            return MAX_VALUE;
        }
        if (value.equals(INTERNAL_MIN_VALUE)) {
            return MIN_VALUE;
        }
        return new Deadline(value);
    }

    /**
     * Creates a deadline from a UNIX time in milliseconds.
     *
     * @param value a UNIX time in milliseconds.
     * @return a deadline.
     */
    public static Deadline fromUnixMilliseconds(final long value) {
        if (value == INTERNAL_MAX_VALUE_MILLIS) {
            return MAX_VALUE;
        }
        if (value == INTERNAL_MIN_VALUE_MILLIS) {
            return MIN_VALUE;
        }
        return new Deadline(value);
    }

    /**
     * Creates a deadline from a string in the format {@value #DATE_FORMAT}.
     *
     * @param source a string in the format {@value #DATE_FORMAT}.
     * @return a deadline from a string in the format {@value #DATE_FORMAT}.
     * @throws ParseException if the specified source string cannot be parsed.
     */
    public static Deadline parse(final String source) throws ParseException {
        return fromUnixMilliseconds(simpleDateFormat.parse(source).getTime());
    }

    private volatile boolean frozen;

    private volatile Instant lastCheck;

    /*
     * Internal representation is an Instant.
     */
    private final Instant value;

    /**
     * Constructs a new instance with the given UNIX time in milliseconds.
     *
     * @param epochMilli UNIX time in milliseconds.
     */
    private Deadline(final long epochMilli) {
        this(Instant.ofEpochMilli(epochMilli));
    }

    /**
     * Constructs a new instance with the given UNIX time in milliseconds.
     *
     * @param instant UNIX time in milliseconds.
     * @since 5.2
     */
    private Deadline(final Instant instant) {
        this.value = instant;
        setLastCheck();
    }

    @Override
    public boolean equals(final Object obj) {
        // Only take into account the deadline value.
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Deadline other = (Deadline) obj;
        return value.equals(other.value);
    }

    /**
     * Formats this deadline.
     *
     * @param overdueTimeUnit the time unit to show how much over the deadline we are.
     * @return a formatted string.
     */
    public String format(final TimeUnit overdueTimeUnit) {
        return String.format("Deadline: %s, %s overdue", formatTarget(), remainingTimeValue());
    }

    /**
     * Formats the deadline value as a string in the format {@value #DATE_FORMAT}.
     *
     * @return a formatted string in the format {@value #DATE_FORMAT}.
     */
    public String formatTarget() {
        return simpleDateFormat.format(value.toEpochMilli());
    }

    public Deadline freeze() {
        frozen = true;
        return this;
    }

    /**
     * Package private for testing.
     *
     * @return the last time we checked the current time.
     */
    long getLastCheck() {
        return lastCheck.toEpochMilli();
    }

    /**
     * Gets the UNIX time deadline value.
     *
     * @return the UNIX time deadline value.
     */
    public long getValue() {
        return value.toEpochMilli();
    }

    /**
     * Gets the deadline Instant.
     *
     * @return the deadline Instant.
     * @since 5.2
     */
    public Instant getInstant() {
        return value;
    }

    @Override
    public int hashCode() {
        // Only take into account the deadline value.
        return Objects.hash(value);
    }

    /**
     * Returns whether this deadline occurs before the given time in milliseconds.
     *
     * @param millis the time to compare.
     * @return whether this deadline occurs before the given time in milliseconds.
     */
    public boolean isBefore(final long millis) {
        return getValue() < millis;
    }

    /**
     * Returns whether this deadline occurs before the given instant.
     *
     * @param instant the time to compare.
     * @return whether this deadline occurs before the given instant.
     */
    public boolean isBefore(final Instant instant) {
        return value.isBefore(instant);
    }

    /**
     * Returns whether the deadline has expired.
     *
     * @return whether the deadline has expired.
     */
    public boolean isExpired() {
        setLastCheck();
        return value.isBefore(lastCheck);
    }

    /**
     * Returns whether this deadline is the maximum deadline.
     *
     * @return whether this deadline is the maximum deadline.
     */
    public boolean isMax() {
        return value == INTERNAL_MAX_VALUE;
    }

    /**
     * Returns whether this deadline is the minimum deadline.
     *
     * @return whether this deadline is the minimum deadline.
     */
    public boolean isMin() {
        return value == INTERNAL_MIN_VALUE;
    }

    /**
     * Returns whether this deadline has not expired.
     *
     * @return whether this deadline has not expired.
     */
    public boolean isNotExpired() {
        setLastCheck();
        return !isExpired();
    }

    /**
     * Returns the smaller of this and another {@code Deadline}.
     *
     * @param other another deadline.
     * @return the smaller of {@code this} and {@code other}.
     */
    public Deadline min(final Deadline other) {
        return value.compareTo(other.value) < 0 ? this : other;
    }

    /**
     * Returns the difference in milliseconds between the deadline and now.
     *
     * @return the different in milliseconds between the deadline and now.
     */
    public long remaining() {
        setLastCheck();
        return remainingDuration().toMillis();
    }

    /**
     * Returns the difference in milliseconds between the deadline and now.
     *
     * @return the different in milliseconds between the deadline and now.
     */
    public Duration remainingDuration() {
        setLastCheck();
        return Duration.between(lastCheck, value);
    }

    /**
     * Returns the difference as a TimeValue between the deadline and now.
     *
     * @return Returns the different as a TimeValue between the deadline and now.
     */
    public TimeValue remainingTimeValue() {
        return TimeValue.of(remaining(), TimeUnit.MILLISECONDS);
    }

    private void setLastCheck() {
        if (!frozen) {
            this.lastCheck = Instant.now();
        }}

    @Override
    public String toString() {
        return formatTarget();
    }

}
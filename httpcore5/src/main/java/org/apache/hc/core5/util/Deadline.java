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

import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * A deadline based on a UNIX time, the elapsed since 00:00:00 Coordinated Universal Time (UTC), Thursday, 1 January
 * 1970.
 *
 * @since 5.0
 */
public class Deadline {

    /**
     * A special internal value that marks a deadline as the longest possible.
     */
    private static final long INTERNAL_MAX_VALUE = Long.MAX_VALUE;

    /**
     * A special internal value that marks a deadline as the shortest possible.
     */
    private static final long INTERNAL_MIN_VALUE = 0;

    /**
     * The maximum (longest-lived) deadline.
     */
    public static Deadline MAX_VALUE = new Deadline(INTERNAL_MAX_VALUE);

    /**
     * The minimum (shortest-lived) deadline.
     */
    public static Deadline MIN_VALUE = new Deadline(INTERNAL_MIN_VALUE);

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
            final long deadline = timeMillis + timeValue.toMillis();
            return deadline < 0 ? Deadline.MAX_VALUE : Deadline.fromUnixMillis(deadline);
        }
        return Deadline.MAX_VALUE;
    }

    /**
     * Creates a deadline from a UNIX time in milliseconds.
     *
     * @param value a UNIX time in milliseconds.
     * @return a new deadline.
     */
    public static Deadline fromUnixMillis(final long value) {
        if (value == INTERNAL_MAX_VALUE) {
            return MAX_VALUE;
        }
        if (value == INTERNAL_MIN_VALUE) {
            return MIN_VALUE;
        }
        return new Deadline(value);
    }

    private volatile long lastCheck;

    /*
     * Internal representation is a UNIX time.
     */
    private final long value;

    private Deadline(final long deadline) {
        super();
        this.value = deadline;
    }

    /**
     * Returns the difference in milliseconds between the deadline and the last time we checked it expired.
     *
     * @return the different in milliseconds between the deadline and the last time we checked it expired.
     */
    public long difference() {
        return lastCheck - value;
    }

    /**
     * Returns the difference as a TimeValue between the deadline and the last time we checked it expired.
     *
     * @return Returns the different as a TimeValue between the deadline and the last time we checked it expired.
     */
    private TimeValue differenceTimeValue() {
        return TimeValue.of(difference(), TimeUnit.MILLISECONDS);
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
        return value == other.value;
    }

    /**
     * Formats this deadline.
     *
     * @param overdueTimeUnit the time unit to show how much over the deadline we are.
     * @return a formatted string.
     */
    public String format(final TimeUnit overdueTimeUnit) {
        return String.format("Deadline: %s, %s overdue", formatTarget(), differenceTimeValue());
    }

    /**
     * Formats the deadline value as a string like "1969-12-31T17:00:01.000 MST".
     *
     * @return a formatted string.
     */
    public String formatTarget() {
        // (1) Deadline in a format similar to ISO8601 (YYYY-MM-DDThh:mm:ss.sTZD)
        // We use the TimeZone zone ID because we cannot get it from String.format() in
        // Java 7 and the short names have been deprecated.
        // (2) String could be cached in ivar.
        return String.format("%1$tFT%1$tT.%1$tL " + TimeZone.getDefault().getID(), value);
    }

    /**
     * Gets the UNIX time deadline value.
     *
     * @return the UNIX time deadline value.
     */
    public long getTarget() {
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
        return value < millis;
    }

    /**
     * Returns whether this deadline occurs before the given current time.
     *
     * @return whether this deadline occurs before the given current time.
     */
    public boolean isBeforeNow() {
        this.lastCheck = System.currentTimeMillis();
        return value < this.lastCheck;
    }

    /**
     * Returns whether the deadline has expired.
     *
     * @return whether the deadline has expired.
     */
    public boolean isExpired() {
        this.lastCheck = System.currentTimeMillis();
        return this.lastCheck > value;
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
     * Returns the smaller of this and another {@code Deadline}.
     *
     * @param other another deadline.
     * @return the smaller of {@code this} and {@code other}.
     */
    public Deadline min(final Deadline other) {
        return value <= other.value ? this : other;
    }

    @Override
    public String toString() {
        return formatTarget();
    }

}

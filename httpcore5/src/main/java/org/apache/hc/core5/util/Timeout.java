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
 * Represents a timeout value as a {@code long} time and {@link TimeUnit}.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class Timeout extends TimeValue {

    /**
     * A disabled timeout represented as 0 {@code MILLISECONDS}.
     */
    public static final Timeout DISABLED = new Timeout(0, TimeUnit.MILLISECONDS);

    /**
     * An undefined timeout represented as -1 {@code MILLISECONDS}.
     */
    public static final Timeout UNDEFINED_MILLISECONDS = new Timeout(UNDEFINED, TimeUnit.MILLISECONDS);

    /**
     * An undefined timeout represented as -1 {@code SECONDS}.
     */
    public static final Timeout UNDEFINED_SECONDS = new Timeout(UNDEFINED, TimeUnit.SECONDS);

    /**
     * Creates a Timeout.
     *
     * @param duration
     *            the time duration in the given {@code timeUnit}.
     * @param timeUnit
     *            the time unit for the given durarion.
     * @return a Timeout
     */
    public static Timeout of(final long duration, final TimeUnit timeUnit) {
        return new Timeout(duration, timeUnit);
    }

    /**
     * Creates a Timeout.
     *
     * @param days
     *            the duration in days and the given {@code timeUnit}.
     * @return a Timeout
     */
    public static Timeout ofDays(final long days) {
        return of(days, TimeUnit.DAYS);
    }

    /**
     * Creates a Timeout.
     *
     * @param hours
     *            the duration in hours and the given {@code timeUnit}.
     * @return a Timeout
     */
    public static Timeout ofHours(final long hours) {
        return of(hours, TimeUnit.HOURS);
    }

    /**
     * Creates a Timeout.
     *
     * @param milliseconds
     *            the duration in milliseconds and the given {@code timeUnit}.
     * @return a Timeout
     */
    public static Timeout ofMillis(final long milliseconds) {
        return of(milliseconds, TimeUnit.MILLISECONDS);
    }

    /**
     * Creates a Timeout.
     *
     * @param minutes
     *            the duration in minutes and the given {@code timeUnit}.
     * @return a Timeout
     */
    public static Timeout ofMinutes(final long minutes) {
        return of(minutes, TimeUnit.MINUTES);
    }

    /**
     * Creates a Timeout.
     *
     * @param seconds
     *            the duration in seconds and the given {@code timeUnit}.
     * @return a Timeout
     */
    public static Timeout ofSeconds(final long seconds) {
        return of(seconds, TimeUnit.SECONDS);
    }

    private static long validateDuration(final long duration) {
        if (duration < UNDEFINED) {
            throw new IllegalArgumentException("Duration may not be less than " + UNDEFINED);
        }
        return duration;
    }

    Timeout(final long duration, final TimeUnit timeUnit) {
        super(validateDuration(duration), Args.notNull(timeUnit, "timeUnit"));
    }

    /**
     * Whether this timeout is disabled.
     *
     * @return Whether this timeout is disabled.
     */
    public boolean isDisabled() {
        return getDuration() == 0;
    }

    /**
     * Whether this timeout is enabled.
     *
     * @return Whether this timeout is disabled.
     */
    public boolean isEnabled() {
        return !isDisabled() && !(isUndefinedMilliseconds() || isUndefinedSeconds());
    }

    /**
     * Whether this timeout is undefined.
     *
     * @return Whether this timeout is undefined.
     */
    public boolean isUndefinedMilliseconds() {
        return getDuration() == UNDEFINED && getTimeUnit() == TimeUnit.MILLISECONDS;
    }

    /**
     * Whether this timeout is undefined.
     *
     * @return Whether this timeout is undefined.
     */
    public boolean isUndefinedSeconds() {
        return getDuration() == UNDEFINED && getTimeUnit() == TimeUnit.SECONDS;
    }

}

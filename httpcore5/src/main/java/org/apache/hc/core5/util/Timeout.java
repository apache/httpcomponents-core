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
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * Represents a timeout value as a non-negative {@code long} time and {@link TimeUnit}.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class Timeout extends TimeValue {

    /**
     * A zero milliseconds {@link Timeout}.
     */
    public static final Timeout ZERO_MILLISECONDS = Timeout.of(0, TimeUnit.MILLISECONDS);

    /**
     * A one milliseconds {@link Timeout}.
     */
    public static final Timeout ONE_MILLISECOND = Timeout.of(1, TimeUnit.MILLISECONDS);

    /**
     * A disabled timeout represented as 0 {@code MILLISECONDS}.
     */
    public static final Timeout DISABLED = ZERO_MILLISECONDS;

    /**
     * Returns the given {@code timeout} if it is not {@code null}, if {@code null} then returns {@link #DISABLED}.
     *
     * @param timeout may be {@code null}
     * @return {@code timeValue} or {@link #DISABLED}
     */
    public static Timeout defaultsToDisabled(final Timeout timeout) {
        return defaultsTo(timeout, DISABLED);
    }

    /**
     * Creates a Timeout from a Duration.
     *
     * @param duration the time duration.
     * @return a Timeout.
     * @since 5.2
     */
    public static Timeout of(final Duration duration) {
        final long seconds = duration.getSeconds();
        final long nanoOfSecond = duration.getNano();
        if (seconds == 0) {
            // no conversion
            return of(nanoOfSecond, TimeUnit.NANOSECONDS);
        } else if (nanoOfSecond == 0) {
            // no conversion
            return of(seconds, TimeUnit.SECONDS);
        }
        // conversion attempts
        try {
            return of(duration.toNanos(), TimeUnit.NANOSECONDS);
        } catch (final ArithmeticException e) {
            try {
                return of(duration.toMillis(), TimeUnit.MILLISECONDS);
            } catch (final ArithmeticException e1) {
                // backstop
                return of(seconds, TimeUnit.SECONDS);
            }
        }
    }

    /**
     * Creates a Timeout.
     *
     * @param duration the time duration.
     * @param timeUnit the time unit for the given duration.
     * @return a Timeout
     */
    public static Timeout of(final long duration, final TimeUnit timeUnit) {
        return new Timeout(duration, timeUnit);
    }

    /**
     * Creates a Timeout.
     *
     * @param days the duration in days and the given {@code timeUnit}.
     * @return a Timeout
     */
    public static Timeout ofDays(final long days) {
        return of(days, TimeUnit.DAYS);
    }

    /**
     * Creates a Timeout.
     *
     * @param hours the duration in hours and the given {@code timeUnit}.
     * @return a Timeout
     */
    public static Timeout ofHours(final long hours) {
        return of(hours, TimeUnit.HOURS);
    }

    /**
     * Creates a Timeout.
     *
     * @param microseconds the duration in seconds and the given {@code timeUnit}.
     * @return a Timeout
     */
    public static Timeout ofMicroseconds(final long microseconds) {
        return of(microseconds, TimeUnit.MICROSECONDS);
    }

    /**
     * Creates a Timeout.
     *
     * @param milliseconds the duration in milliseconds and the given {@code timeUnit}.
     * @return a Timeout
     */
    public static Timeout ofMilliseconds(final long milliseconds) {
        return of(milliseconds, TimeUnit.MILLISECONDS);
    }

    /**
     * Creates a Timeout.
     *
     * @param minutes the duration in minutes and the given {@code timeUnit}.
     * @return a Timeout
     */
    public static Timeout ofMinutes(final long minutes) {
        return of(minutes, TimeUnit.MINUTES);
    }

    /**
     * Creates a Timeout.
     *
     * @param nanoseconds the duration in seconds and the given {@code timeUnit}.
     * @return a Timeout
     */
    public static Timeout ofNanoseconds(final long nanoseconds) {
        return of(nanoseconds, TimeUnit.NANOSECONDS);
    }

    /**
     * Creates a Timeout.
     *
     * @param seconds the duration in seconds and the given {@code timeUnit}.
     * @return a Timeout
     */
    public static Timeout ofSeconds(final long seconds) {
        return of(seconds, TimeUnit.SECONDS);
    }

    /**
     * Parses a Timeout in the format {@code <Integer><SPACE><TimeUnit>}, for example {@code "1,200 MILLISECONDS"}
     *
     * @param value the TimeValue to parse
     * @return a new TimeValue
     * @throws ParseException if the number cannot be parsed
     */
    public static Timeout parse(final String value) throws ParseException {
        return TimeValue.parse(value).toTimeout();
    }

    Timeout(final long duration, final TimeUnit timeUnit) {
        super(Args.notNegative(duration, "duration"), Args.notNull(timeUnit, "timeUnit"));
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
        return !isDisabled();
    }

}

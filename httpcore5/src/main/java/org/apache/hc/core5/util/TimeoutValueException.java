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

import java.util.concurrent.TimeoutException;

/**
 * A specialization of {@link TimeoutException} that carries a {@link Timeout} deadline and the actual value.
 *
 * @since 5.0
 */
public class TimeoutValueException extends TimeoutException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new exception for the given timeout deadline and actual timeout.
     *
     * @param timeoutDeadline How long was the expected timeout in milliseconds.
     * @param timeoutActual How long we actually waited in milliseconds.
     * @return a new TimeoutValueException.
     */
    public static TimeoutValueException fromMilliseconds(final long timeoutDeadline, final long timeoutActual) {
        return new TimeoutValueException(Timeout.ofMilliseconds(min0(timeoutDeadline)),
                Timeout.ofMilliseconds(min0(timeoutActual)));
    }

    /**
     * Returns the given {@code value} if positive, otherwise returns 0.
     *
     * @param value any timeout
     * @return the given {@code value} if positive, otherwise returns 0.
     */
    private static long min0(final long value) {
        return value < 0 ? 0 : value;
    }

    private final Timeout actual;

    private final Timeout deadline;

    /**
     * Creates a new exception for the given timeout deadline and actual timeout.
     *
     * @param deadline How long was the expected timeout.
     * @param actual How long we actually waited.
     */
    public TimeoutValueException(final Timeout deadline, final Timeout actual) {
        super(String.format("Timeout deadline: %s, actual: %s", deadline, actual));
        this.actual = actual;
        this.deadline = deadline;
    }

    /**
     * Gets how long was the expected timeout in milliseconds.
     *
     * @return how long was the expected timeout in milliseconds.
     */
    public Timeout getActual() {
        return actual;
    }

    /**
     * Gets how long we actually waited in milliseconds.
     *
     * @return how long we actually waited in milliseconds.
     */
    public Timeout getDeadline() {
        return deadline;
    }

}

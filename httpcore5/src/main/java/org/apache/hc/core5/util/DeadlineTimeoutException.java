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
import java.util.concurrent.TimeoutException;

/**
 * A specialization of {@link TimeoutException} that carries a deadline and an actual value, both as UNIX times.
 *
 * @since 5.0
 */
public class DeadlineTimeoutException extends TimeoutException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new exception for the given timeout deadline and actual timeout.
     *
     * @param deadline When was the deadline in UNIX time.
     * @return a new TimeoutValueException.
     */
    public static DeadlineTimeoutException from(final Deadline deadline) {
        return new DeadlineTimeoutException(deadline);
    }

    private final Deadline deadline;

    /**
     * Creates a new exception for the given timeout deadline and actual timeout.
     *
     * @param deadline When was the deadline in UNIX time.
     */
    private DeadlineTimeoutException(final Deadline deadline) {
        // Deadline in a format like ISO8601: YYYY-MM-DDThh:mm:ss.sTZD
        // We use the TimeZone zone ID because we cannot get from String.format() in
        // Java 7 and the short names have been deprecated.
        super(deadline.format(TimeUnit.MILLISECONDS));
        this.deadline = deadline;
    }

    /**
     * The expected deadline for this timeout since the start of UNIX time.
     *
     * @return The expected deadline for this timeout since the start of UNIX time.
     */
    public Deadline getDeadline() {
        return deadline;
    }

}

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

package org.apache.http.nio.params;

import org.apache.http.params.HttpParams;

/**
 * Utility class for accessing I/O reactor parameters in {@link HttpParams}.
 *
 * @since 4.0
 *
 * @see NIOReactorPNames
 */
public final class NIOReactorParams implements NIOReactorPNames {

    private NIOReactorParams() {
        super();
    }

    /**
     * Obtains the value of {@link NIOReactorPNames#CONTENT_BUFFER_SIZE} parameter.
     * If not set, defaults to <code>4096</code>.
     *
     * @param params HTTP parameters.
     * @return content buffer size.
     */
    public static int getContentBufferSize(final HttpParams params) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        return params.getIntParameter(CONTENT_BUFFER_SIZE, 4096);
    }

    /**
     * Sets value of the {@link NIOReactorPNames#CONTENT_BUFFER_SIZE} parameter.
     *
     * @param params HTTP parameters.
     * @param size content buffer size.
     */
    public static void setContentBufferSize(final HttpParams params, int size) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        params.setIntParameter(CONTENT_BUFFER_SIZE, size);
    }

    /**
     * Obtains the value of {@link NIOReactorPNames#SELECT_INTERVAL} parameter.
     * If not set, defaults to <code>1000</code>.
     *
     * @param params HTTP parameters.
     * @return I/O select interval in milliseconds.
     */
    public static long getSelectInterval(final HttpParams params) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        return params.getLongParameter(SELECT_INTERVAL, 1000);
    }

    /**
     * Sets value of the {@link NIOReactorPNames#SELECT_INTERVAL} parameter.
     *
     * @param params HTTP parameters.
     * @param ms I/O select interval in milliseconds.
     */
    public static void setSelectInterval(final HttpParams params, long ms) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        params.setLongParameter(SELECT_INTERVAL, ms);
    }

    /**
     * Obtains the value of {@link NIOReactorPNames#GRACE_PERIOD} parameter.
     * If not set, defaults to <code>500</code>.
     *
     * @param params HTTP parameters.
     * @return shutdown grace period in milliseconds.
     */
    public static long getGracePeriod(final HttpParams params) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        return params.getLongParameter(GRACE_PERIOD, 500);
    }

    /**
     * Sets value of the {@link NIOReactorPNames#GRACE_PERIOD} parameter.
     *
     * @param params HTTP parameters.
     * @param ms shutdown grace period in milliseconds.
     */
    public static void setGracePeriod(final HttpParams params, long ms) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        params.setLongParameter(GRACE_PERIOD, ms);
    }

    /**
     * Obtains the value of {@link NIOReactorPNames#INTEREST_OPS_QUEUEING} parameter.
     * If not set, defaults to <code>false</code>.
     *
     * @param params HTTP parameters.
     * @return interest ops queuing flag.
     *
     * @since 4.1
     */
    public static boolean getInterestOpsQueueing(final HttpParams params) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        return params.getBooleanParameter(INTEREST_OPS_QUEUEING, false);
    }

    /**
     * Sets value of the {@link NIOReactorPNames#INTEREST_OPS_QUEUEING} parameter.
     *
     * @param params HTTP parameters.
     * @param interestOpsQueueing interest ops queuing.
     *
     * @since 4.1
     */
    public static void setInterestOpsQueueing(
            final HttpParams params, boolean interestOpsQueueing) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        params.setBooleanParameter(INTEREST_OPS_QUEUEING, interestOpsQueueing);
    }

}

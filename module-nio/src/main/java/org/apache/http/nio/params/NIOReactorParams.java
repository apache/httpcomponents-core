/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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
 * 
 * @version $Revision$
 * 
 * @since 4.0
 *
 * @see NIOReactorPNames
 */
public final class NIOReactorParams implements NIOReactorPNames {

    private NIOReactorParams() {
        super();
    }

    public static int getContentBufferSize(final HttpParams params) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        return params.getIntParameter(CONTENT_BUFFER_SIZE, 1024);
    }
    
    public static void setContentBufferSize(final HttpParams params, int size) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        params.setIntParameter(CONTENT_BUFFER_SIZE, size);
    }

    public static long getSelectInterval(final HttpParams params) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        return params.getLongParameter(SELECT_INTERVAL, 1000);
    }
    
    public static void setSelectInterval(final HttpParams params, long ms) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        params.setLongParameter(SELECT_INTERVAL, ms);
    }

    public static long getGracePeriod(final HttpParams params) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        return params.getLongParameter(GRACE_PERIOD, 500);
    }
    
    public static void setGracePeriod(final HttpParams params, long ms) {
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        params.setLongParameter(GRACE_PERIOD, ms);
    }

}

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

package org.apache.hc.core5.http2.impl;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http2.H2TransportMetrics;

/**
 * Default implementation of {@link H2TransportMetrics}.
 *
 * @since 5.0
 */
public class BasicH2TransportMetrics extends BasicHttpTransportMetrics implements H2TransportMetrics {

    private final AtomicLong framesTransferred;

    public BasicH2TransportMetrics() {
        this.framesTransferred = new AtomicLong(0);
    }

    @Override
    public long getFramesTransferred() {
        return framesTransferred.get();
    }

    public void incrementFramesTransferred() {
        framesTransferred.incrementAndGet();
    }

}

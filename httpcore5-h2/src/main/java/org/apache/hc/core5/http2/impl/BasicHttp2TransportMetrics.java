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

import org.apache.hc.core5.annotation.NotThreadSafe;
import org.apache.hc.core5.http.impl.BasicHttpTransportMetrics;
import org.apache.hc.core5.http2.io.Http2TransportMetrics;

/**
 * Default implementation of {@link Http2TransportMetrics}.
 *
 * @since 5.0
 */
@NotThreadSafe
public class BasicHttp2TransportMetrics extends BasicHttpTransportMetrics implements Http2TransportMetrics {

    private long framesTransferred;

    @Override
    public long getFramesTransferred() {
        return framesTransferred;
    }

    public void incrementFramesTransferred() {
        framesTransferred++;
    }

    @Override
    public void reset() {
        super.reset();
        this.framesTransferred = 0;
    }

}

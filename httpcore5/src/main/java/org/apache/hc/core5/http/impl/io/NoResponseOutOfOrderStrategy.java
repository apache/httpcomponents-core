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

package org.apache.hc.core5.http.impl.io;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.ResponseOutOfOrderStrategy;

import java.io.InputStream;

/**
 * An implementation of {@link ResponseOutOfOrderStrategy} which does not check for early responses.
 *
 * Early response detection requires 1ms blocking reads and incurs a hefty performance cost for
 * large uploads.
 *
 * @see MonitoringResponseOutOfOrderStrategy
 * @since 5.1
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public final class NoResponseOutOfOrderStrategy implements ResponseOutOfOrderStrategy {

    public static final NoResponseOutOfOrderStrategy INSTANCE = new NoResponseOutOfOrderStrategy();

    @Override
    public boolean isEarlyResponseDetected(
            final ClassicHttpRequest request,
            final HttpClientConnection connection,
            final InputStream inputStream,
            final long totalBytesSent,
            final long nextWriteSize) {
        return false;
    }
}

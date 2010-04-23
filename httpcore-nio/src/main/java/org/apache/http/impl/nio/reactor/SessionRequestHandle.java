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

package org.apache.http.impl.nio.reactor;

import org.apache.http.nio.reactor.SessionRequest;

/**
 * Session request handle class used by I/O reactor implementations to keep
 * a reference to a {@link SessionRequest} along with the time the request
 * was made.
 * @since 4.0
 */
public class SessionRequestHandle {

    private final SessionRequestImpl sessionRequest;
    private final long requestTime;

    public SessionRequestHandle(final SessionRequestImpl sessionRequest) {
        super();
        if (sessionRequest == null) {
            throw new IllegalArgumentException("Session request may not be null");
        }
        this.sessionRequest = sessionRequest;
        this.requestTime = System.currentTimeMillis();
    }

    public SessionRequestImpl getSessionRequest() {
        return this.sessionRequest;
    }

    public long getRequestTime() {
        return this.requestTime;
    }

}

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

package org.apache.hc.core5.testing.classic;

import java.net.SocketException;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.HttpConnection;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class LoggingExceptionListener implements ExceptionListener {

    public final static LoggingExceptionListener INSTANCE = new LoggingExceptionListener();

    private final Logger connLog = LoggerFactory.getLogger("org.apache.hc.core5.http.connection");

    @Override
    public void onError(final Exception ex) {
        if (ex instanceof SocketException) {
            connLog.debug(ex.getMessage());
        } else {
            connLog.error(ex.getMessage(), ex);
        }
    }

    @Override
    public void onError(final HttpConnection conn, final Exception ex) {
        if (ex instanceof ConnectionClosedException) {
            connLog.debug(ex.getMessage());
        } else if (ex instanceof SocketException) {
            connLog.debug(ex.getMessage());
        } else {
            connLog.error(ex.getMessage(), ex);
        }
    }
}

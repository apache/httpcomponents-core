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

package org.apache.hc.core5.testing.nio;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.reactor.IOSessionListener;
import org.apache.hc.core5.testing.classic.LoggingSupport;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class LoggingIOSessionListener implements IOSessionListener {

    public final static LoggingIOSessionListener INSTANCE = new LoggingIOSessionListener();

    private final Logger connLog = LoggerFactory.getLogger("org.apache.hc.core5.http.connection");

    private LoggingIOSessionListener() {
    }

    @Override
    public void tlsStarted(final IOSession session) {
        if (connLog.isDebugEnabled()) {
            connLog.debug(LoggingSupport.getId(session) + " TLS session started: " + session);
        }
    }

    @Override
    public void tlsInbound(final IOSession session) {
        if (connLog.isDebugEnabled()) {
            connLog.debug(LoggingSupport.getId(session) + " TLS inbound: " + session);
        }
    }

    @Override
    public void tlsOutbound(final IOSession session) {
        if (connLog.isDebugEnabled()) {
            connLog.debug(LoggingSupport.getId(session) + " TLS outbound: " + session);
        }
    }

    @Override
    public void connected(final IOSession session) {
        if (connLog.isDebugEnabled()) {
            connLog.debug(LoggingSupport.getId(session) + " connected: " + session);
        }
    }

    @Override
    public void inputReady(final IOSession session) {
        if (connLog.isDebugEnabled()) {
            connLog.debug(LoggingSupport.getId(session) + " input ready: " + session);
        }
    }

    @Override
    public void outputReady(final IOSession session) {
        if (connLog.isDebugEnabled()) {
            connLog.debug(LoggingSupport.getId(session) + " output ready: " + session);
        }
    }

    @Override
    public void timeout(final IOSession session) {
        if (connLog.isDebugEnabled()) {
            connLog.debug(LoggingSupport.getId(session) + " timeout: " + session);
        }
    }

    @Override
    public void exception(final IOSession session, final Exception ex) {
        if (ex instanceof ConnectionClosedException) {
            return;
        }
        connLog.error(LoggingSupport.getId(session) + " " + ex.getMessage(), ex);
    }

    @Override
    public void disconnected(final IOSession session) {
        if (connLog.isDebugEnabled()) {
            connLog.debug(LoggingSupport.getId(session) + " disconnected: " + session);
        }
    }

}

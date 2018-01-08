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

import java.util.Iterator;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.testing.classic.LoggingSupport;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class LoggingHttp1StreamListener implements Http1StreamListener {

    enum Type { CLIENT, SERVER }

    public final static LoggingHttp1StreamListener INSTANCE_CLIENT = new LoggingHttp1StreamListener(Type.CLIENT);
    public final static LoggingHttp1StreamListener INSTANCE_SERVER = new LoggingHttp1StreamListener(Type.SERVER);

    private final Type type;
    private final Logger connLog = LoggerFactory.getLogger("org.apache.hc.core5.http.connection");
    private final Logger headerLog = LoggerFactory.getLogger("org.apache.hc.core5.http.headers");

    private LoggingHttp1StreamListener(final Type type) {
        this.type = type;
    }

    private String requestDirection() {
        return type == Type.CLIENT ? " >> " : " << ";
    }

    private String responseDirection() {
        return type == Type.CLIENT ? " << " : " >> ";
    }

    @Override
    public void onRequestHead(final HttpConnection connection, final HttpRequest request) {
        if (headerLog.isDebugEnabled()) {
            headerLog.debug(LoggingSupport.getId(connection) + requestDirection() + new RequestLine(request));
            for (final Iterator<Header> it = request.headerIterator(); it.hasNext(); ) {
                headerLog.debug(LoggingSupport.getId(connection) + requestDirection() + it.next());
            }
        }
    }

    @Override
    public void onResponseHead(final HttpConnection connection, final HttpResponse response) {
        if (headerLog.isDebugEnabled()) {
            headerLog.debug(LoggingSupport.getId(connection) + responseDirection() + new StatusLine(response));
            for (final Iterator<Header> it = response.headerIterator(); it.hasNext(); ) {
                headerLog.debug(LoggingSupport.getId(connection) + responseDirection() + it.next());
            }
        }
    }

    @Override
    public void onExchangeComplete(final HttpConnection connection, final boolean keepAlive) {
        if (connLog.isDebugEnabled()) {
            if (keepAlive) {
                connLog.debug(LoggingSupport.getId(connection) + " Connection is kept alive");
            } else {
                connLog.debug(LoggingSupport.getId(connection) + " Connection is not kept alive");
            }
        }
    }

}
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class InternalHttp1StreamListener implements Http1StreamListener {

    enum Type { CLIENT, SERVER }

    private final String id;
    private final Type type;
    private final Logger sessionLog;
    private final Logger headerLog = LogManager.getLogger("org.apache.hc.core5.http.headers");

    public InternalHttp1StreamListener(final String id, final Type type, final Logger sessionLog) {
        this.id = id;
        this.type = type;
        this.sessionLog = sessionLog;
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
            headerLog.debug(id + requestDirection() + new RequestLine(request));
            for (final Iterator<Header> it = request.headerIterator(); it.hasNext(); ) {
                headerLog.debug(id + requestDirection() + it.next());
            }
        }
    }

    @Override
    public void onResponseHead(final HttpConnection connection, final HttpResponse response) {
        if (headerLog.isDebugEnabled()) {
            headerLog.debug(id + responseDirection() + new StatusLine(response));
            for (final Iterator<Header> it = response.headerIterator(); it.hasNext(); ) {
                headerLog.debug(id + responseDirection() + it.next());
            }
        }
    }

    @Override
    public void onExchangeComplete(final HttpConnection connection, final boolean keepAlive) {
        if (sessionLog.isDebugEnabled()) {
            if (keepAlive) {
                sessionLog.debug(id + " Connection is kept alive");
            } else {
                sessionLog.debug(id + " Connection is not kept alive");
            }
        }
    }

}

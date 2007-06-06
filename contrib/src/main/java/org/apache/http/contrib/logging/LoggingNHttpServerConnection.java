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

package org.apache.http.contrib.logging;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpRequestFactory;
import org.apache.http.HttpResponse;
import org.apache.http.impl.nio.DefaultNHttpServerConnection;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.params.HttpParams;

public class LoggingNHttpServerConnection extends DefaultNHttpServerConnection {

    private final Log log;
    private final Log headerlog;
    
    public LoggingNHttpServerConnection(
            final IOSession session,
            final HttpRequestFactory requestFactory,
            final ByteBufferAllocator allocator,
            final HttpParams params) {
        super(session, requestFactory, allocator, params);
        this.log = LogFactory.getLog(DefaultNHttpServerConnection.class);
        this.headerlog = LogFactory.getLog("org.apache.http.headers");
    }

    public void submitResponse(final HttpResponse response) throws IOException, HttpException {
        if (this.log.isDebugEnabled()) {
            this.log.debug("HTTP connection " + this + ": "  + response.getStatusLine().toString());
        }
        super.submitResponse(response);
        if (this.headerlog.isDebugEnabled()) {
            this.headerlog.debug("<< " + response.getStatusLine().toString());
            Header[] headers = response.getAllHeaders();
            for (int i = 0; i < headers.length; i++) {
                this.headerlog.debug("<< " + headers[i].toString());
            }
        }
    }
    
}
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
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponseFactory;
import org.apache.http.impl.nio.DefaultNHttpClientConnection;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.params.HttpParams;

public class LoggingNHttpClientIOTarget extends DefaultNHttpClientConnection {

    private final Log log;
    private final Log headerlog;
    
    public LoggingNHttpClientIOTarget(
        final IOSession session,
        final HttpResponseFactory responseFactory,
        final ByteBufferAllocator allocator,
        final HttpParams params) {
        super(session, responseFactory, allocator, params);
        this.log = LogFactory.getLog(getClass());
        this.headerlog = LogFactory.getLog("org.apache.http.headers");
    }

    public void close() throws IOException {
        this.log.debug("Close connection");        
        super.close();
    }

    public void shutdown() throws IOException {
        this.log.debug("Shutdown connection");        
        super.shutdown();
    }

    public void submitRequest(final HttpRequest request) throws IOException, HttpException {
        if (this.log.isDebugEnabled()) {
            this.log.debug("HTTP connection " + this + ": "  + request.getRequestLine().toString());
        }
        super.submitRequest(request);
        if (this.headerlog.isDebugEnabled()) {
            this.headerlog.debug(">> " + request.getRequestLine().toString());
            Header[] headers = request.getAllHeaders();
            for (int i = 0; i < headers.length; i++) {
                this.headerlog.debug(">> " + headers[i].toString());
            }
        }
    }

    public void consumeInput(final NHttpClientHandler handler) {
        this.log.debug("Consume input");        
        super.consumeInput(handler);
    }

    public void produceOutput(final NHttpClientHandler handler) {
        this.log.debug("Produce output");        
        super.produceOutput(handler);
    }
    
}
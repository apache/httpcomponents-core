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
import org.apache.http.HttpConnectionMetrics;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.NHttpClientIOTarget;
import org.apache.http.protocol.HttpContext;

public class LoggingNHttpClientIOTarget implements NHttpClientIOTarget  {

    private final NHttpClientIOTarget target;
    private final Log log;
    private final Log headerlog;
    
    public LoggingNHttpClientIOTarget(final NHttpClientIOTarget target) {
        super();
        this.target = target;
        this.log = LogFactory.getLog(target.getClass());
        this.headerlog = LogFactory.getLog("org.apache.http.headers");
    }

    public void requestInput() {
        this.target.requestInput();
    }

    public void requestOutput() {
        this.target.requestOutput();
    }

    public void suspendInput() {
        this.target.suspendInput();
    }

    public void suspendOutput() {
        this.target.suspendOutput();
    }

    public void close() throws IOException {
        this.log.debug("Close connection");        
        this.target.close();
    }

    public HttpConnectionMetrics getMetrics() {
        return this.target.getMetrics();
    }

    public int getSocketTimeout() {
        return this.target.getSocketTimeout();
    }

    public boolean isOpen() {
        return this.target.isOpen();
    }

    public boolean isStale() {
        return this.target.isStale();
    }

    public void setSocketTimeout(int timeout) {
        this.target.setSocketTimeout(timeout);
    }

    public void shutdown() throws IOException {
        this.log.debug("Shutdown connection");        
        this.target.shutdown();
    }

    public HttpContext getContext() {
        return this.target.getContext();
    }

    public HttpRequest getHttpRequest() {
        return this.target.getHttpRequest();
    }

    public HttpResponse getHttpResponse() {
        return this.target.getHttpResponse();
    }

    public int getStatus() {
        return this.target.getStatus();
    }

    public boolean isRequestSubmitted() {
        return this.target.isRequestSubmitted();
    }

    public void resetInput() {
        this.target.resetInput();
    }

    public void resetOutput() {
        this.target.resetOutput();
    }

    public void submitRequest(final HttpRequest request) throws IOException, HttpException {
        if (this.log.isDebugEnabled()) {
            this.log.debug("HTTP connection " + this + ": "  + request.getRequestLine().toString());
        }
        this.target.submitRequest(request);
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
        this.target.consumeInput(handler);
    }

    public void produceOutput(final NHttpClientHandler handler) {
        this.log.debug("Produce output");        
        this.target.produceOutput(handler);
    }
    
}
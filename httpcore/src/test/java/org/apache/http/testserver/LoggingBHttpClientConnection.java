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

package org.apache.http.testserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.impl.DefaultBHttpClientConnection;
import org.apache.http.params.HttpParams;

public class LoggingBHttpClientConnection extends DefaultBHttpClientConnection {

    private static final AtomicLong COUNT = new AtomicLong();

    private final String id;
    private final Log log;
    private final Log headerlog;
    private final Wire wire;

    public LoggingBHttpClientConnection(final HttpParams params) {
        super(params);
        this.id = "http-outgoing-" + COUNT.incrementAndGet();
        this.log = LogFactory.getLog(getClass());
        this.headerlog = LogFactory.getLog("org.apache.http.headers");
        this.wire = new Wire(LogFactory.getLog("org.apache.http.wire"), this.id);
    }

    @Override
    public void close() throws IOException {
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.id + ": Close connection");
        }
        super.close();
    }

    @Override
    public void shutdown() throws IOException {
        if (this.log.isDebugEnabled()) {
            this.log.debug(this.id + ": Shutdown connection");
        }
        super.shutdown();
    }

    @Override
    protected InputStream getSocketInputStream(final Socket socket) throws IOException {
        InputStream in = super.getSocketInputStream(socket);
        if (wire.isEnabled()) {
            in = new LoggingInputStream(in, wire);
        }
        return in;
    }

    @Override
    protected OutputStream getSocketOutputStream(final Socket socket) throws IOException {
        OutputStream out = super.getSocketOutputStream(socket);
        if (wire.isEnabled()) {
            out = new LoggingOutputStream(out, wire);
        }
        return out;
    }

    @Override
    protected void onResponseReceived(final HttpResponse response) {
        if (response != null && this.headerlog.isDebugEnabled()) {
            this.headerlog.debug(this.id + " << " + response.getStatusLine().toString());
            Header[] headers = response.getAllHeaders();
            for (int i = 0; i < headers.length; i++) {
                this.headerlog.debug(this.id + " << " + headers[i].toString());
            }
        }
    }

    @Override
    protected void onRequestSubmitted(final HttpRequest request) {
        if (request != null && this.headerlog.isDebugEnabled()) {
            this.headerlog.debug(id + " >> " + request.getRequestLine().toString());
            Header[] headers = request.getAllHeaders();
            for (int i = 0; i < headers.length; i++) {
                this.headerlog.debug(this.id + " >> " + headers[i].toString());
            }
        }
    }

}

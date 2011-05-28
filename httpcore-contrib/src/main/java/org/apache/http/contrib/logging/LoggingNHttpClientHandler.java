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

package org.apache.http.contrib.logging;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpClientConnection;
import org.apache.http.nio.NHttpClientHandler;

/**
 * Decorator class intended to transparently extend an {@link NHttpClientHandler}
 * with basic event logging capabilities using Commons Logging.
 *
 */
public class LoggingNHttpClientHandler implements NHttpClientHandler {

    private final Log log;
    private final Log headerlog;
    private final NHttpClientHandler handler;

    public LoggingNHttpClientHandler(final NHttpClientHandler handler) {
        super();
        if (handler == null) {
            throw new IllegalArgumentException("HTTP client handler may not be null");
        }
        this.handler = handler;
        this.log = LogFactory.getLog(handler.getClass());
        this.headerlog = LogFactory.getLog("org.apache.http.headers");
    }

    public void connected(final NHttpClientConnection conn, final Object attachment) {
        if (this.log.isDebugEnabled()) {
            this.log.debug(conn + ": Connected (" + attachment + ")");
        }
        this.handler.connected(conn, attachment);
    }

    public void closed(final NHttpClientConnection conn) {
        if (this.log.isDebugEnabled()) {
            this.log.debug(conn + ": Closed");
        }
        this.handler.closed(conn);
    }

    public void exception(final NHttpClientConnection conn, final IOException ex) {
        this.log.error(conn + ": " + ex.getMessage(), ex);
        this.handler.exception(conn, ex);
    }

    public void exception(final NHttpClientConnection conn, final HttpException ex) {
        this.log.error(conn + ": " + ex.getMessage(), ex);
        this.handler.exception(conn, ex);
    }

    public void requestReady(final NHttpClientConnection conn) {
        if (this.log.isDebugEnabled()) {
            this.log.debug(conn + ": Request ready");
        }
        this.handler.requestReady(conn);
    }

    public void outputReady(final NHttpClientConnection conn, final ContentEncoder encoder) {
        if (this.log.isDebugEnabled()) {
            this.log.debug(conn + ": Output ready");
        }
        this.handler.outputReady(conn, encoder);
        if (this.log.isDebugEnabled()) {
            this.log.debug(conn + ": Content encoder " + encoder);
        }
    }

    public void responseReceived(final NHttpClientConnection conn) {
        HttpResponse response = conn.getHttpResponse();
        if (this.log.isDebugEnabled()) {
            this.log.debug(conn + ": " + response.getStatusLine());
        }
        this.handler.responseReceived(conn);
        if (this.headerlog.isDebugEnabled()) {
            this.headerlog.debug(conn + " << " + response.getStatusLine().toString());
            Header[] headers = response.getAllHeaders();
            for (int i = 0; i < headers.length; i++) {
                this.headerlog.debug(conn + " << " + headers[i].toString());
            }
        }
    }

    public void inputReady(final NHttpClientConnection conn, final ContentDecoder decoder) {
        if (this.log.isDebugEnabled()) {
            this.log.debug(conn + ": Input ready");
        }
        this.handler.inputReady(conn, decoder);
        if (this.log.isDebugEnabled()) {
            this.log.debug(conn + ": Content decoder " + decoder);
        }
    }

    public void timeout(final NHttpClientConnection conn) {
        if (this.log.isDebugEnabled()) {
            this.log.debug(conn + ": Timeout");
        }
        this.handler.timeout(conn);
    }

}
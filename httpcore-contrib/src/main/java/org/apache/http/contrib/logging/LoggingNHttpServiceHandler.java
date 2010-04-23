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
import org.apache.http.HttpRequest;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.nio.NHttpServiceHandler;

/**
 * Decorator class intended to transparently extend an {@link NHttpServiceHandler}
 * with basic event logging capabilities using Commons Logging.
 *
 */
public class LoggingNHttpServiceHandler implements NHttpServiceHandler {

    private final Log log;
    private final Log headerlog;
    private final NHttpServiceHandler handler;

    public LoggingNHttpServiceHandler(final NHttpServiceHandler handler) {
        super();
        if (handler == null) {
            throw new IllegalArgumentException("HTTP service handler may not be null");
        }
        this.handler = handler;
        this.log = LogFactory.getLog(handler.getClass());
        this.headerlog = LogFactory.getLog("org.apache.http.headers");
    }

    public void connected(final NHttpServerConnection conn) {
        if (this.log.isDebugEnabled()) {
            this.log.debug("HTTP connection " + conn + ": Connected");
        }
        this.handler.connected(conn);
    }

    public void closed(final NHttpServerConnection conn) {
        if (this.log.isDebugEnabled()) {
            this.log.debug("HTTP connection " + conn + ": Closed");
        }
        this.handler.closed(conn);
    }

    public void exception(final NHttpServerConnection conn, final IOException ex) {
        this.log.error("HTTP connection " + conn + ": " + ex.getMessage(), ex);
        this.handler.exception(conn, ex);
    }

    public void exception(final NHttpServerConnection conn, final HttpException ex) {
        this.log.error("HTTP connection " + conn + ": " + ex.getMessage(), ex);
        this.handler.exception(conn, ex);
    }

    public void requestReceived(final NHttpServerConnection conn) {
        HttpRequest request = conn.getHttpRequest();
        if (this.log.isDebugEnabled()) {
            this.log.debug("HTTP connection " + conn + ": " + request.getRequestLine());
        }
        this.handler.requestReceived(conn);
        if (this.headerlog.isDebugEnabled()) {
            this.headerlog.debug(">> " + request.getRequestLine().toString());
            Header[] headers = request.getAllHeaders();
            for (int i = 0; i < headers.length; i++) {
                this.headerlog.debug(">> " + headers[i].toString());
            }
        }
    }

    public void outputReady(final NHttpServerConnection conn, final ContentEncoder encoder) {
        if (this.log.isDebugEnabled()) {
            this.log.debug("HTTP connection " + conn + ": Output ready");
        }
        this.handler.outputReady(conn, encoder);
        if (this.log.isDebugEnabled()) {
            this.log.debug("HTTP connection " + conn + ": Content encoder " + encoder);
        }
    }

    public void responseReady(final NHttpServerConnection conn) {
        if (this.log.isDebugEnabled()) {
            this.log.debug("HTTP connection " + conn + ": Response ready");
        }
        this.handler.responseReady(conn);
    }

    public void inputReady(final NHttpServerConnection conn, final ContentDecoder decoder) {
        if (this.log.isDebugEnabled()) {
            this.log.debug("HTTP connection " + conn + ": Input ready");
        }
        this.handler.inputReady(conn, decoder);
        if (this.log.isDebugEnabled()) {
            this.log.debug("HTTP connection " + conn + ": Content decoder " + decoder);
        }
    }

    public void timeout(final NHttpServerConnection conn) {
        if (this.log.isDebugEnabled()) {
            this.log.debug("HTTP connection " + conn + ": Timeout");
        }
        this.handler.timeout(conn);
    }

}
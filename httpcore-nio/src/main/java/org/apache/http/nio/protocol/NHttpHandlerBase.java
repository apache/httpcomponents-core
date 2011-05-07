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

package org.apache.http.nio.protocol;

import java.io.IOException;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.nio.NHttpConnection;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpProcessor;

/**
 * @since 4.0
 */
public abstract class NHttpHandlerBase {

    protected static final String CONN_STATE = "http.nio.conn-state";

    protected final HttpProcessor httpProcessor;
    protected final ConnectionReuseStrategy connStrategy;
    protected final ByteBufferAllocator allocator;
    protected final HttpParams params;

    protected EventListener eventListener;

    public NHttpHandlerBase(
            final HttpProcessor httpProcessor,
            final ConnectionReuseStrategy connStrategy,
            final ByteBufferAllocator allocator,
            final HttpParams params) {
        super();
        if (httpProcessor == null) {
            throw new IllegalArgumentException("HTTP processor may not be null.");
        }
        if (connStrategy == null) {
            throw new IllegalArgumentException("Connection reuse strategy may not be null");
        }
        if (allocator == null) {
            throw new IllegalArgumentException("ByteBuffer allocator may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.httpProcessor = httpProcessor;
        this.connStrategy = connStrategy;
        this.allocator = allocator;
        this.params = params;
    }

    public HttpParams getParams() {
        return this.params;
    }

    public void setEventListener(final EventListener eventListener) {
        this.eventListener = eventListener;
    }

    protected void closeConnection(final NHttpConnection conn, final Throwable cause) {
        try {
            // Try to close it nicely
            conn.close();
        } catch (IOException ex) {
            try {
                // Just shut the damn thing down
                conn.shutdown();
            } catch (IOException ignore) {
            }
        }
    }

    protected void shutdownConnection(final NHttpConnection conn, final Throwable cause) {
        try {
            conn.shutdown();
        } catch (IOException ignore) {
        }
    }

    protected void handleTimeout(final NHttpConnection conn) {
        try {
            if (conn.getStatus() == NHttpConnection.ACTIVE) {
                conn.close();
                if (conn.getStatus() == NHttpConnection.CLOSING) {
                    // Give the connection some grace time to
                    // close itself nicely
                    conn.setSocketTimeout(250);
                }
                if (this.eventListener != null) {
                    this.eventListener.connectionTimeout(conn);
                }
            } else {
                conn.shutdown();
            }
        } catch (IOException ignore) {
        }
    }

    protected boolean canResponseHaveBody(
            final HttpRequest request, final HttpResponse response) {

        if (request != null && "HEAD".equalsIgnoreCase(request.getRequestLine().getMethod())) {
            return false;
        }

        int status = response.getStatusLine().getStatusCode();
        return status >= HttpStatus.SC_OK
            && status != HttpStatus.SC_NO_CONTENT
            && status != HttpStatus.SC_NOT_MODIFIED
            && status != HttpStatus.SC_RESET_CONTENT;
    }

}

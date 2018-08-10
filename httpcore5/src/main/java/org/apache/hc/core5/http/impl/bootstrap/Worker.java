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
package org.apache.hc.core5.http.impl.bootstrap;

import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.impl.io.HttpService;
import org.apache.hc.core5.http.io.HttpServerConnection;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.io.CloseMode;

class Worker implements Runnable {

    private final HttpService httpservice;
    private final HttpServerConnection conn;
    private final ExceptionListener exceptionListener;

    Worker(
            final HttpService httpservice,
            final HttpServerConnection conn,
            final ExceptionListener exceptionListener) {
        super();
        this.httpservice = httpservice;
        this.conn = conn;
        this.exceptionListener = exceptionListener;
    }

    public HttpServerConnection getConnection() {
        return this.conn;
    }

    @Override
    public void run() {
        try {
            final BasicHttpContext localContext = new BasicHttpContext();
            final HttpCoreContext context = HttpCoreContext.adapt(localContext);
            while (!Thread.interrupted() && this.conn.isOpen()) {
                this.httpservice.handleRequest(this.conn, context);
                localContext.clear();
            }
            this.conn.close();
        } catch (final Exception ex) {
            this.exceptionListener.onError(this.conn, ex);
        } finally {
            this.conn.close(CloseMode.IMMEDIATE);
        }
    }

}

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
package org.apache.http.benchmark.httpcore;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.apache.http.HttpResponseInterceptor;
import org.apache.http.benchmark.HttpServer;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.nio.DefaultServerIOEventDispatch;
import org.apache.http.impl.nio.reactor.DefaultListeningIOReactor;
import org.apache.http.nio.protocol.AsyncNHttpServiceHandler;
import org.apache.http.nio.protocol.NHttpRequestHandlerRegistry;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.ListeningIOReactor;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.util.VersionInfo;

public class HttpCoreNIOServer implements HttpServer {

    private final int port;
    private final NHttpListener listener;

    public HttpCoreNIOServer(int port) throws IOException {
        if (port <= 0) {
            throw new IllegalArgumentException("Server port may not be negative or null");
        }
        this.port = port;

        HttpParams params = new SyncBasicHttpParams();
        params
            .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 10000)
            .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 12 * 1024)
            .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
            .setParameter(CoreProtocolPNames.ORIGIN_SERVER, "HttpCore-NIO-Test/1.1");

        HttpProcessor httpproc = new ImmutableHttpProcessor(new HttpResponseInterceptor[] {
                new ResponseDate(),
                new ResponseServer(),
                new ResponseContent(),
                new ResponseConnControl()
        });

        AsyncNHttpServiceHandler handler = new AsyncNHttpServiceHandler(
                httpproc,
                new DefaultHttpResponseFactory(),
                new DefaultConnectionReuseStrategy(),
                params);

        NHttpRequestHandlerRegistry reqistry = new NHttpRequestHandlerRegistry();
        reqistry.register("/rnd", new NRandomDataHandler());
        handler.setHandlerResolver(reqistry);

        ListeningIOReactor ioreactor = new DefaultListeningIOReactor(2, params);
        IOEventDispatch ioEventDispatch = new DefaultServerIOEventDispatch(handler, params);
        this.listener = new NHttpListener(ioreactor, ioEventDispatch);
    }

    public String getName() {
        return "HttpCore (NIO)";
    }

    public String getVersion() {
        VersionInfo vinfo = VersionInfo.loadVersionInfo("org.apache.http",
                Thread.currentThread().getContextClassLoader());
        return vinfo.getRelease();
    }

    public void start() throws Exception {
        this.listener.start();
        this.listener.listen(new InetSocketAddress(this.port));
    }

    public void shutdown() {
        this.listener.terminate();
        try {
            this.listener.awaitTermination(1000);
        } catch (InterruptedException ex) {
        }
        Exception ex = this.listener.getException();
        if (ex != null) {
            System.out.println("Error: " + ex.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: <port>");
            System.exit(1);
        }
        int port = Integer.parseInt(args[0]);
        final HttpCoreNIOServer server = new HttpCoreNIOServer(port);
        System.out.println("Listening on port: " + port);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                server.shutdown();
            }

        });
    }

}

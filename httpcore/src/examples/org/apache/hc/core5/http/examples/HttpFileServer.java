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

package org.apache.hc.core5.http.examples;

import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ExceptionLogger;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.MethodNotSupportedException;
import org.apache.hc.core5.http.bootstrap.io.HttpServer;
import org.apache.hc.core5.http.bootstrap.io.ServerBootstrap;
import org.apache.hc.core5.http.config.SocketConfig;
import org.apache.hc.core5.http.entity.ContentType;
import org.apache.hc.core5.http.entity.EntityUtils;
import org.apache.hc.core5.http.entity.FileEntity;
import org.apache.hc.core5.http.entity.StringEntity;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.ssl.SSLContexts;

/**
 * Embedded HTTP/1.1 file server based on a classic (blocking) I/O model.
 */
public class HttpFileServer {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Please specify document root directory");
            System.exit(1);
        }
        // Document root directory
        String docRoot = args[0];
        int port = 8080;
        if (args.length >= 2) {
            port = Integer.parseInt(args[1]);
        }

        SSLContext sslcontext = null;
        if (port == 8443) {
            // Initialize SSL context
            URL url = HttpFileServer.class.getResource("/my.keystore");
            if (url == null) {
                System.out.println("Keystore not found");
                System.exit(1);
            }
            sslcontext = SSLContexts.custom()
                    .loadKeyMaterial(url, "secret".toCharArray(), "secret".toCharArray())
                    .build();
        }

        SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(15000)
                .setTcpNoDelay(true)
                .build();

        final HttpServer server = ServerBootstrap.bootstrap()
                .setListenerPort(port)
                .setServerInfo("Test/1.1")
                .setSocketConfig(socketConfig)
                .setSslContext(sslcontext)
                .setExceptionLogger(new StdErrorExceptionLogger())
                .registerHandler("*", new HttpFileHandler(docRoot))
                .create();

        server.start();
        server.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                server.shutdown(5, TimeUnit.SECONDS);
            }
        });
    }

    static class StdErrorExceptionLogger implements ExceptionLogger {

        @Override
        public void log(final Exception ex) {
            if (ex instanceof SocketTimeoutException) {
                System.err.println("Connection timed out");
            } else if (ex instanceof ConnectionClosedException) {
                System.err.println(ex.getMessage());
            } else {
                ex.printStackTrace();
            }
        }

    }

    static class HttpFileHandler implements HttpRequestHandler  {

        private final String docRoot;

        public HttpFileHandler(final String docRoot) {
            super();
            this.docRoot = docRoot;
        }

        @Override
        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {

            String method = request.getRequestLine().getMethod().toUpperCase(Locale.ROOT);
            if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("POST")) {
                throw new MethodNotSupportedException(method + " method not supported");
            }
            String target = request.getRequestLine().getUri();

            HttpEntity incomingEntity = request.getEntity();
            if (incomingEntity != null) {
                byte[] entityContent = EntityUtils.toByteArray(incomingEntity);
                System.out.println("Incoming incomingEntity content (bytes): " + entityContent.length);
            }

            final File file = new File(this.docRoot, URLDecoder.decode(target, "UTF-8"));
            if (!file.exists()) {

                response.setStatusCode(HttpStatus.SC_NOT_FOUND);
                StringEntity outgoingEntity = new StringEntity(
                        "<html><body><h1>File" + file.getPath() +
                        " not found</h1></body></html>",
                        ContentType.create("text/html", "UTF-8"));
                response.setEntity(outgoingEntity);
                System.out.println("File " + file.getPath() + " not found");

            } else if (!file.canRead() || file.isDirectory()) {

                response.setStatusCode(HttpStatus.SC_FORBIDDEN);
                StringEntity outgoingEntity = new StringEntity(
                        "<html><body><h1>Access denied</h1></body></html>",
                        ContentType.create("text/html", "UTF-8"));
                response.setEntity(outgoingEntity);
                System.out.println("Cannot read file " + file.getPath());

            } else {
                HttpCoreContext coreContext = HttpCoreContext.adapt(context);
                HttpConnection conn = coreContext.getConnection(HttpConnection.class);
                response.setStatusCode(HttpStatus.SC_OK);
                FileEntity body = new FileEntity(file, ContentType.create("text/html", (Charset) null));
                response.setEntity(body);
                System.out.println(conn + ": serving file " + file.getPath());
            }
        }

    }

}

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
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.ExceptionListener;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.MethodNotSupportedException;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.TimeValue;

/**
 * Example of embedded HTTP/1.1 file server using classic I/O.
 */
public class ClassicFileServerExample {

    public static void main(final String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Please specify document root directory");
            System.exit(1);
        }
        // Document root directory
        final String docRoot = args[0];
        int port = 8080;
        if (args.length >= 2) {
            port = Integer.parseInt(args[1]);
        }

        SSLContext sslContext = null;
        if (port == 8443) {
            // Initialize SSL context
            final URL url = ClassicFileServerExample.class.getResource("/my.keystore");
            if (url == null) {
                System.out.println("Keystore not found");
                System.exit(1);
            }
            sslContext = SSLContexts.custom()
                    .loadKeyMaterial(url, "secret".toCharArray(), "secret".toCharArray())
                    .build();
        }

        final SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(15, TimeUnit.SECONDS)
                .setTcpNoDelay(true)
                .build();

        final HttpServer server = ServerBootstrap.bootstrap()
                .setListenerPort(port)
                .setSocketConfig(socketConfig)
                .setSslContext(sslContext)
                .setExceptionListener(new ExceptionListener() {

                    @Override
                    public void onError(final Exception ex) {
                        ex.printStackTrace();
                    }

                    @Override
                    public void onError(final HttpConnection conn, final Exception ex) {
                        if (ex instanceof SocketTimeoutException) {
                            System.err.println("Connection timed out");
                        } else if (ex instanceof ConnectionClosedException) {
                            System.err.println(ex.getMessage());
                        } else {
                            ex.printStackTrace();
                        }
                    }

                })
                .register("*", new HttpFileHandler(docRoot))
                .create();

        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                server.close(CloseMode.GRACEFUL);
            }
        });
        System.out.println("Listening on port " + port);

        server.awaitTermination(TimeValue.MAX_VALUE);

    }

    static class HttpFileHandler implements HttpRequestHandler  {

        private final String docRoot;

        public HttpFileHandler(final String docRoot) {
            super();
            this.docRoot = docRoot;
        }

        @Override
        public void handle(
                final ClassicHttpRequest request,
                final ClassicHttpResponse response,
                final HttpContext context) throws HttpException, IOException {

            final String method = request.getMethod();
            if (!Method.GET.isSame(method) && !Method.HEAD.isSame(method) && !Method.POST.isSame(method)) {
                throw new MethodNotSupportedException(method + " method not supported");
            }
            final String path = request.getPath();

            final HttpEntity incomingEntity = request.getEntity();
            if (incomingEntity != null) {
                final byte[] entityContent = EntityUtils.toByteArray(incomingEntity);
                System.out.println("Incoming incomingEntity content (bytes): " + entityContent.length);
            }

            final File file = new File(this.docRoot, URLDecoder.decode(path, "UTF-8"));
            if (!file.exists()) {

                response.setCode(HttpStatus.SC_NOT_FOUND);
                final String msg = "File " + file.getPath() + " not found";
                final StringEntity outgoingEntity = new StringEntity(
                        "<html><body><h1>" + msg + "</h1></body></html>",
                        ContentType.create("text/html", "UTF-8"));
                response.setEntity(outgoingEntity);
                System.out.println(msg);

            } else if (!file.canRead() || file.isDirectory()) {

                response.setCode(HttpStatus.SC_FORBIDDEN);
                final String msg = "Cannot read file " + file.getPath();
                final StringEntity outgoingEntity = new StringEntity(
                        "<html><body><h1>" + msg + "</h1></body></html>",
                        ContentType.create("text/html", "UTF-8"));
                response.setEntity(outgoingEntity);
                System.out.println(msg);

            } else {
                final HttpCoreContext coreContext = HttpCoreContext.adapt(context);
                final EndpointDetails endpoint = coreContext.getEndpointDetails();
                response.setCode(HttpStatus.SC_OK);
                final FileEntity body = new FileEntity(file, ContentType.create("text/html", (Charset) null));
                response.setEntity(body);
                System.out.println(endpoint + ": serving file " + file.getPath());
            }
        }

    }

}

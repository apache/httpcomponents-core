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
package org.apache.hc.core5.http2.examples;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EndpointDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.impl.routing.RequestRouter;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.entity.FileEntityProducer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.AsyncServerPipeline;
import org.apache.hc.core5.http.nio.support.BasicResponseProducer;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpDateGenerator;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.frame.RawFrame;
import org.apache.hc.core5.http2.impl.nio.H2StreamListener;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.util.TextUtils;
import org.apache.hc.core5.util.TimeValue;

/**
 * Example of asynchronous embedded HTTP/2 file server.
 */
public class H2FileServerExample {

    public static void main(final String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Please specify document root directory");
            System.exit(1);
        }
        // Document root directory
        final File docRoot = new File(args[0]);
        int port = 8080;
        if (args.length >= 2) {
            port = Integer.parseInt(args[1]);
        }

        final IOReactorConfig config = IOReactorConfig.custom()
                .setSoTimeout(15, TimeUnit.SECONDS)
                .setTcpNoDelay(true)
                .build();

        final Supplier<AsyncServerExchangeHandler> exchangeHandlerSupplier = AsyncServerPipeline.assemble()
                // Read GET / HEAD requests ignoring their content body
                .request(Method.GET, Method.HEAD)
                .ignoreContent()
                // Write out responses by streaming out content of a file
                .response()
                .<Message<HttpResponse, File>>produce(m -> {
                    if (m.error() == null) {
                        final File file = m.getBody();
                        final ContentType contentType;
                        final String filename = TextUtils.toLowerCase(file.getName());
                        if (filename.endsWith(".txt")) {
                            contentType = ContentType.TEXT_PLAIN;
                        } else if (filename.endsWith(".html") || filename.endsWith(".htm")) {
                            contentType = ContentType.TEXT_HTML;
                        } else if (filename.endsWith(".xml")) {
                            contentType = ContentType.TEXT_XML;
                        } else {
                            contentType = ContentType.DEFAULT_BINARY;
                        }
                        return new BasicResponseProducer(new FileEntityProducer(file, contentType));
                    } else {
                        return new BasicResponseProducer(new StringAsyncEntityProducer(Objects.toString(m.error()), ContentType.TEXT_PLAIN));
                    }
                })
                // Map exceptions to a response message
                .errorMessage(Throwable::getMessage)
                // Generate a response to the request
                .handle((m, context) -> {
                    final HttpRequest request = m.head();
                    final URI requestUri;
                    try {
                        requestUri = request.getUri();
                    } catch (final URISyntaxException ex) {
                        throw new ProtocolException(ex.getMessage(), ex);
                    }
                    final String path = requestUri.getPath();
                    final File file = new File(docRoot, path);
                    if (!file.exists()) {
                        println("File " + file.getPath() + " not found");
                        return Message.error(new BasicHttpResponse(HttpStatus.SC_NOT_FOUND), "File not found");
                    } else if (!file.canRead() || file.isDirectory()) {
                        println("Cannot read file " + file.getPath());
                        return Message.error(new BasicHttpResponse(HttpStatus.SC_FORBIDDEN), "File cannot be accessed");
                    } else {
                        final HttpCoreContext coreContext = HttpCoreContext.cast(context);
                        final EndpointDetails endpoint = coreContext.getEndpointDetails();
                        println(endpoint + " | serving file " + file.getPath());
                        return Message.of(new BasicHttpResponse(HttpStatus.SC_OK), file);
                    }
                })
                .supplier();

        final HttpAsyncServer server = H2ServerBootstrap.bootstrap()
                .setIOReactorConfig(config)
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                .setStreamListener(new H2StreamListener() {

                    @Override
                    public void onHeaderInput(final HttpConnection connection, final int streamId, final List<? extends Header> headers) {
                        for (int i = 0; i < headers.size(); i++) {
                            System.out.println(connection.getRemoteAddress() + " (" + streamId + ") << " + headers.get(i));
                        }
                    }

                    @Override
                    public void onHeaderOutput(final HttpConnection connection, final int streamId, final List<? extends Header> headers) {
                        for (int i = 0; i < headers.size(); i++) {
                            System.out.println(connection.getRemoteAddress() + " (" + streamId + ") >> " + headers.get(i));
                        }
                    }

                    @Override
                    public void onFrameInput(final HttpConnection connection, final int streamId, final RawFrame frame) {
                    }

                    @Override
                    public void onFrameOutput(final HttpConnection connection, final int streamId, final RawFrame frame) {
                    }

                    @Override
                    public void onInputFlowControl(final HttpConnection connection, final int streamId, final int delta, final int actualSize) {
                    }

                    @Override
                    public void onOutputFlowControl(final HttpConnection connection, final int streamId, final int delta, final int actualSize) {
                    }

                })
                .setRequestRouter(RequestRouter.<Supplier<AsyncServerExchangeHandler>>builder()
                        .addRoute(RequestRouter.LOCAL_AUTHORITY, "*", exchangeHandlerSupplier)
                        .resolveAuthority(RequestRouter.LOCAL_AUTHORITY_RESOLVER)
                        .build())
                .create();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("HTTP server shutting down");
            server.close(CloseMode.GRACEFUL);
        }));

        server.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(port), URIScheme.HTTP);
        final ListenerEndpoint listenerEndpoint = future.get();
        System.out.print("Listening on " + listenerEndpoint.getAddress());
        server.awaitShutdown(TimeValue.ofDays(Long.MAX_VALUE));
    }

    static void println(final String msg) {
        System.out.println(HttpDateGenerator.INSTANCE.getCurrentDate() + " | " + msg);
    }

}

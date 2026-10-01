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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import javax.net.ssl.SSLContext;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.impl.bootstrap.HttpAsyncServer;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.support.AsyncServerPipeline;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.impl.nio.bootstrap.H2ServerBootstrap;
import org.apache.hc.core5.http2.ssl.H2ServerTlsStrategy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.TimeValue;

/**
 * TLS HTTP/2 server that advertises alternative origins with the ORIGIN frame.
 * The certificate in the supplied PKCS#12 file must cover every advertised
 * host before a client can safely coalesce requests onto this connection.
 *
 * <pre>{@code
 * H2OriginFrameServerExample server.p12 changeit 8443 \
 *     https://assets.example.test https://api.example.test:8443
 * }</pre>
 */
public class H2OriginFrameServerExample {

    public static void main(final String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: H2OriginFrameServerExample <server.p12> <password> "
                    + "[port] [origin ...]");
            System.exit(1);
        }

        final File keyStore = new File(args[0]);
        final char[] password = args[1].toCharArray();
        final int port = args.length > 2 ? Integer.parseInt(args[2]) : 8443;
        final List<HttpHost> originSet = new ArrayList<>();
        for (int i = 3; i < args.length; i++) {
            originSet.add(HttpHost.create(args[i]));
        }

        final SSLContext sslContext = SSLContexts.custom()
                .setKeyStoreType("pkcs12")
                .loadKeyMaterial(keyStore.toURI().toURL(), password, password)
                .build();

        final Supplier<AsyncServerExchangeHandler> handlerSupplier = AsyncServerPipeline.assemble()
                .request(Method.GET)
                .ignoreContent()
                .response()
                .asString(ContentType.TEXT_PLAIN)
                .handle((request, context) -> Message.of(
                        new BasicHttpResponse(HttpStatus.SC_OK),
                        "Served over the ORIGIN-advertised connection for " + request.head().getAuthority() + "\n"))
                .supplier();

        final H2ServerBootstrap bootstrap = H2ServerBootstrap.bootstrap()
                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                .setTlsStrategy(new H2ServerTlsStrategy(sslContext))
                .setOriginSet(originSet)
                .register("*", handlerSupplier);
        // Serve every advertised origin authoritatively, so the connection can
        // honour the origins it announces in the ORIGIN frame.
        for (final HttpHost origin : originSet) {
            bootstrap.register(origin.getHostName(), "*", handlerSupplier);
        }
        final HttpAsyncServer server = bootstrap.create();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.close(CloseMode.GRACEFUL)));
        server.start();
        final Future<ListenerEndpoint> future = server.listen(new InetSocketAddress(port), URIScheme.HTTPS);
        System.out.println("Listening on " + future.get().getAddress());
        System.out.println("Advertised Origin Set: " + originSet);
        server.awaitShutdown(TimeValue.ofDays(Long.MAX_VALUE));
    }
}

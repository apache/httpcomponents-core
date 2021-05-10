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

package org.apache.hc.core5.http.impl.io;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequestMapper;
import org.apache.hc.core5.http.HttpResponseFactory;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.UnsupportedHttpVersionException;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.ServerSupport;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.HttpServerConnection;
import org.apache.hc.core5.http.io.HttpServerRequestHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.support.BasicHttpServerExpectationDecorator;
import org.apache.hc.core5.http.io.support.BasicHttpServerRequestHandler;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.util.Args;

/**
 * {@code HttpService} is a server side HTTP protocol handler based on
 * the classic (blocking) I/O model.
 * <p>
 * {@code HttpService} relies on {@link HttpProcessor} to generate mandatory
 * protocol headers for all outgoing messages and apply common, cross-cutting
 * message transformations to all incoming and outgoing messages, whereas
 * individual {@link HttpRequestHandler}s are expected to implement
 * application specific content generation and processing.
 * <p>
 * {@code HttpService} uses {@link HttpRequestMapper} to map
 * matching request handler for a particular request URI of an incoming HTTP
 * request.
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class HttpService {

    private final HttpProcessor processor;
    private final HttpServerRequestHandler requestHandler;
    private final ConnectionReuseStrategy connReuseStrategy;
    private final Http1StreamListener streamListener;

    /**
     * Create a new HTTP service.
     *
     * @param processor the processor to use on requests and responses
     * @param handlerMapper  the handler mapper
     * @param responseFactory  the response factory. If {@code null}
     *   {@link DefaultClassicHttpResponseFactory#INSTANCE} will be used.
     * @param connReuseStrategy the connection reuse strategy. If {@code null}
     *   {@link DefaultConnectionReuseStrategy#INSTANCE} will be used.
     * @param streamListener message stream listener.
     */
    public HttpService(
            final HttpProcessor processor,
            final HttpRequestMapper<HttpRequestHandler> handlerMapper,
            final ConnectionReuseStrategy connReuseStrategy,
            final HttpResponseFactory<ClassicHttpResponse> responseFactory,
            final Http1StreamListener streamListener) {
        this(processor,
                new BasicHttpServerExpectationDecorator(new BasicHttpServerRequestHandler(handlerMapper, responseFactory)),
                connReuseStrategy,
                streamListener);
    }

    /**
     * Create a new HTTP service.
     *
     * @param processor the processor to use on requests and responses
     * @param handlerMapper  the handler mapper
     * @param connReuseStrategy the connection reuse strategy. If {@code null}
     *   {@link DefaultConnectionReuseStrategy#INSTANCE} will be used.
     * @param responseFactory  the response factory. If {@code null}
     *   {@link DefaultClassicHttpResponseFactory#INSTANCE} will be used.
     */
    public HttpService(
            final HttpProcessor processor,
            final HttpRequestMapper<HttpRequestHandler> handlerMapper,
            final ConnectionReuseStrategy connReuseStrategy,
            final HttpResponseFactory<ClassicHttpResponse> responseFactory) {
        this(processor, handlerMapper, connReuseStrategy, responseFactory, null);
    }

    /**
     * Create a new HTTP service.
     *
     * @param processor the processor to use on requests and responses
     * @param requestHandler  the request handler.
     * @param connReuseStrategy the connection reuse strategy. If {@code null}
     *   {@link DefaultConnectionReuseStrategy#INSTANCE} will be used.
     * @param streamListener message stream listener.
     */
    public HttpService(
            final HttpProcessor processor,
            final HttpServerRequestHandler requestHandler,
            final ConnectionReuseStrategy connReuseStrategy,
            final Http1StreamListener streamListener) {
        super();
        this.processor =  Args.notNull(processor, "HTTP processor");
        this.requestHandler =  Args.notNull(requestHandler, "Request handler");
        this.connReuseStrategy = connReuseStrategy != null ? connReuseStrategy : DefaultConnectionReuseStrategy.INSTANCE;
        this.streamListener = streamListener;
    }

    /**
     * Create a new HTTP service.
     *
     * @param processor the processor to use on requests and responses
     * @param requestHandler  the request handler.
     */
    public HttpService(
            final HttpProcessor processor, final HttpServerRequestHandler requestHandler) {
        this(processor, requestHandler, null, null);
    }

    /**
     * Handles receives one HTTP request over the given connection within the
     * given execution context and sends a response back to the client.
     *
     * @param conn the active connection to the client
     * @param context the actual execution context.
     * @throws IOException in case of an I/O error.
     * @throws HttpException in case of HTTP protocol violation or a processing
     *   problem.
     */
    public void handleRequest(
            final HttpServerConnection conn,
            final HttpContext context) throws IOException, HttpException {

        final AtomicBoolean responseSubmitted = new AtomicBoolean(false);
        try {
            final ClassicHttpRequest request = conn.receiveRequestHeader();
            if (request == null) {
                conn.close();
                return;
            }
            if (streamListener != null) {
                streamListener.onRequestHead(conn, request);
            }
            conn.receiveRequestEntity(request);
            final ProtocolVersion transportVersion = request.getVersion();
            context.setProtocolVersion(transportVersion != null ? transportVersion : HttpVersion.HTTP_1_1);
            context.setAttribute(HttpCoreContext.SSL_SESSION, conn.getSSLSession());
            context.setAttribute(HttpCoreContext.CONNECTION_ENDPOINT, conn.getEndpointDetails());
            context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
            this.processor.process(request, request.getEntity(), context);

            this.requestHandler.handle(request, new HttpServerRequestHandler.ResponseTrigger() {

                @Override
                public void sendInformation(final ClassicHttpResponse response) throws HttpException, IOException {
                    if (responseSubmitted.get()) {
                        throw new HttpException("Response already submitted");
                    }
                    if (response.getCode() >= HttpStatus.SC_SUCCESS) {
                        throw new HttpException("Invalid intermediate response");
                    }
                    if (streamListener != null) {
                        streamListener.onResponseHead(conn, response);
                    }
                    conn.sendResponseHeader(response);
                    conn.flush();
                }

                @Override
                public void submitResponse(final ClassicHttpResponse response) throws HttpException, IOException {
                    try {
                        final ProtocolVersion transportVersion = response.getVersion();
                        if (transportVersion != null && transportVersion.greaterEquals(HttpVersion.HTTP_2)) {
                            throw new UnsupportedHttpVersionException(transportVersion);
                        }
                        ServerSupport.validateResponse(response, response.getEntity());
                        context.setProtocolVersion(transportVersion != null ? transportVersion : HttpVersion.HTTP_1_1);
                        context.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
                        processor.process(response, response.getEntity(), context);

                        responseSubmitted.set(true);
                        conn.sendResponseHeader(response);
                        if (streamListener != null) {
                            streamListener.onResponseHead(conn, response);
                        }
                        if (MessageSupport.canResponseHaveBody(request.getMethod(), response)) {
                            conn.sendResponseEntity(response);
                        }
                        // Make sure the request content is fully consumed
                        EntityUtils.consume(request.getEntity());
                        final boolean keepAlive = connReuseStrategy.keepAlive(request, response, context);
                        if (streamListener != null) {
                            streamListener.onExchangeComplete(conn, keepAlive);
                        }
                        if (!keepAlive) {
                            conn.close();
                        }
                        conn.flush();
                    } finally {
                        response.close();
                    }
                }

            }, context);

        } catch (final HttpException ex) {
            if (responseSubmitted.get()) {
                throw ex;
            }
            try (final ClassicHttpResponse errorResponse = new BasicClassicHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR)) {
                handleException(ex, errorResponse);
                errorResponse.setHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
                context.setAttribute(HttpCoreContext.HTTP_RESPONSE, errorResponse);
                this.processor.process(errorResponse, errorResponse.getEntity(), context);

                conn.sendResponseHeader(errorResponse);
                if (streamListener != null) {
                    streamListener.onResponseHead(conn, errorResponse);
                }
                conn.sendResponseEntity(errorResponse);
                conn.close();
            }
        }
    }

    /**
     * Handles the given exception and generates an HTTP response to be sent
     * back to the client to inform about the exceptional condition encountered
     * in the course of the request processing.
     *
     * @param ex the exception.
     * @param response the HTTP response.
     */
    protected void handleException(final HttpException ex, final ClassicHttpResponse response) {
        response.setCode(toStatusCode(ex));
        response.setEntity(new StringEntity(ServerSupport.toErrorMessage(ex), ContentType.TEXT_PLAIN));
    }

    protected int toStatusCode(final Exception ex) {
        return ServerSupport.toStatusCode(ex);
    }

}

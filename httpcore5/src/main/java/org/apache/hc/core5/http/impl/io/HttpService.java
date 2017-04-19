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
import java.io.InputStream;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponseFactory;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.MethodNotSupportedException;
import org.apache.hc.core5.http.NotImplementedException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.UnsupportedHttpVersionException;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.io.HttpExpectationVerifier;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.HttpRequestHandlerMapper;
import org.apache.hc.core5.http.io.HttpServerConnection;
import org.apache.hc.core5.http.io.entity.StringEntity;
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
 * {@code HttpService} uses {@link HttpRequestHandlerMapper} to map
 * matching request handler for a particular request URI of an incoming HTTP
 * request.
 * <p>
 * {@code HttpService} can use optional {@link HttpExpectationVerifier}
 * to ensure that incoming requests meet server's expectations.
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class HttpService {

    private final HttpProcessor processor;
    private final HttpRequestHandlerMapper handlerMapper;
    private final ConnectionReuseStrategy connReuseStrategy;
    private final HttpResponseFactory<ClassicHttpResponse> responseFactory;
    private final HttpExpectationVerifier expectationVerifier;
    private final Http1StreamListener streamListener;

    /**
     * Create a new HTTP service.
     *
     * @param processor the processor to use on requests and responses
     * @param connReuseStrategy the connection reuse strategy. If {@code null}
     *   {@link DefaultConnectionReuseStrategy#INSTANCE} will be used.
     * @param responseFactory  the response factory. If {@code null}
     *   {@link DefaultClassicHttpResponseFactory#INSTANCE} will be used.
     * @param handlerMapper  the handler mapper. May be null.
     * @param expectationVerifier the expectation verifier. May be null.
     *
     * @since 4.3
     */
    public HttpService(
            final HttpProcessor processor,
            final ConnectionReuseStrategy connReuseStrategy,
            final HttpResponseFactory<ClassicHttpResponse> responseFactory,
            final HttpRequestHandlerMapper handlerMapper,
            final HttpExpectationVerifier expectationVerifier,
            final Http1StreamListener streamListener) {
        super();
        this.processor =  Args.notNull(processor, "HTTP processor");
        this.connReuseStrategy = connReuseStrategy != null ? connReuseStrategy :
            DefaultConnectionReuseStrategy.INSTANCE;
        this.responseFactory = responseFactory != null ? responseFactory :
            DefaultClassicHttpResponseFactory.INSTANCE;
        this.handlerMapper = handlerMapper;
        this.expectationVerifier = expectationVerifier;
        this.streamListener = streamListener;
    }

    /**
     * Create a new HTTP service.
     *
     * @param processor the processor to use on requests and responses
     * @param connReuseStrategy the connection reuse strategy. If {@code null}
     *   {@link DefaultConnectionReuseStrategy#INSTANCE} will be used.
     * @param responseFactory  the response factory. If {@code null}
     *   {@link DefaultClassicHttpResponseFactory#INSTANCE} will be used.
     * @param handlerMapper  the handler mapper. May be null.
     *
     * @since 4.3
     */
    public HttpService(
            final HttpProcessor processor,
            final ConnectionReuseStrategy connReuseStrategy,
            final HttpResponseFactory<ClassicHttpResponse> responseFactory,
            final HttpRequestHandlerMapper handlerMapper) {
        this(processor, connReuseStrategy, responseFactory, handlerMapper, null, null);
    }

    /**
     * Create a new HTTP service.
     *
     * @param processor the processor to use on requests and responses
     * @param handlerMapper  the handler mapper. May be null.
     *
     * @since 4.3
     */
    public HttpService(
            final HttpProcessor processor, final HttpRequestHandlerMapper handlerMapper) {
        this(processor, null, null, handlerMapper);
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

        final ClassicHttpRequest request = conn.receiveRequestHeader();
        if (streamListener != null) {
            streamListener.onRequestHead(conn, request);
        }
        ClassicHttpResponse response = null;
        try {
            try {
                conn.receiveRequestEntity(request);
                final ProtocolVersion transportVersion = request.getVersion();
                if (transportVersion != null) {
                    context.setProtocolVersion(transportVersion);
                }
                context.setAttribute(HttpCoreContext.SSL_SESSION, conn.getSSLSession());
                context.setAttribute(HttpCoreContext.CONNECTION_ENDPOINT, conn.getEndpointDetails());
                context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
                this.processor.process(request, request.getEntity(), context);

                final Header expect = request.getFirstHeader(HttpHeaders.EXPECT);
                final boolean expectContinue = expect != null && "100-continue".equalsIgnoreCase(expect.getValue());

                if (expectContinue) {
                    final ClassicHttpResponse ack = this.responseFactory.newHttpResponse(HttpStatus.SC_CONTINUE);
                    if (this.expectationVerifier != null) {
                        this.expectationVerifier.verify(request, ack, context);
                    }
                    if (ack.getCode() < HttpStatus.SC_SUCCESS) {
                        // Send 1xx response indicating the server expectations
                        // have been met
                        conn.sendResponseHeader(ack);
                        if (streamListener != null) {
                            streamListener.onResponseHead(conn, ack);
                        }
                        conn.flush();
                    } else {
                        response = ack;
                    }
                }
                if (response == null) {
                    response = this.responseFactory.newHttpResponse(HttpStatus.SC_OK);
                    doService(request, response, context);
                }
            } catch (final HttpException ex) {
                if (response != null) {
                    response.close();
                }
                response = this.responseFactory.newHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                handleException(ex, response);
            }
            context.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
            this.processor.process(response, response.getEntity(), context);

            conn.sendResponseHeader(response);
            if (streamListener != null) {
                streamListener.onResponseHead(conn, response);
            }
            if (canResponseHaveBody(request, response)) {
                conn.sendResponseEntity(response);
            }
            conn.flush();

            // Make sure the request content is fully consumed
            final HttpEntity entity = request.getEntity();
            if (entity != null && entity.isStreaming()) {
                final InputStream instream = entity.getContent();
                if (instream != null) {
                    instream.close();
                }
            }
            final boolean keepAlive = this.connReuseStrategy.keepAlive(request, response, context);
            if (streamListener != null) {
                streamListener.onExchangeComplete(conn, keepAlive);
            }
            if (!keepAlive) {
                conn.close();
            }
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    private boolean canResponseHaveBody(final ClassicHttpRequest request, final ClassicHttpResponse response) {
        if (request != null && "HEAD".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        final int status = response.getCode();
        return status >= HttpStatus.SC_SUCCESS
                && status != HttpStatus.SC_NO_CONTENT
                && status != HttpStatus.SC_NOT_MODIFIED;
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
        if (ex instanceof MethodNotSupportedException) {
            response.setCode(HttpStatus.SC_NOT_IMPLEMENTED);
        } else if (ex instanceof UnsupportedHttpVersionException) {
            response.setCode(HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED);
        } else if (ex instanceof NotImplementedException) {
            response.setCode(HttpStatus.SC_NOT_IMPLEMENTED);
        } else if (ex instanceof ProtocolException) {
            response.setCode(HttpStatus.SC_BAD_REQUEST);
        } else {
            response.setCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        String message = ex.getMessage();
        if (message == null) {
            message = ex.toString();
        }
        final StringEntity entity = new StringEntity(message, ContentType.TEXT_PLAIN);
        response.setEntity(entity);
    }

    /**
     * The default implementation of this method attempts to resolve an
     * {@link HttpRequestHandler} for the request URI of the given request
     * and, if found, executes its
     * {@link HttpRequestHandler#handle(ClassicHttpRequest, ClassicHttpResponse, HttpContext)}
     * method.
     * <p>
     * Super-classes can override this method in order to provide a custom
     * implementation of the request processing logic.
     *
     * @param request the HTTP request.
     * @param response the HTTP response.
     * @param context the execution context.
     * @throws IOException in case of an I/O error.
     * @throws HttpException in case of HTTP protocol violation or a processing
     *   problem.
     */
    protected void doService(
            final ClassicHttpRequest request,
            final ClassicHttpResponse response,
            final HttpContext context) throws HttpException, IOException {
        HttpRequestHandler handler = null;
        if (this.handlerMapper != null) {
            handler = this.handlerMapper.lookup(request, context);
        }
        if (handler != null) {
            handler.handle(request, response, context);
        } else {
            response.setCode(HttpStatus.SC_NOT_IMPLEMENTED);
        }
    }

}

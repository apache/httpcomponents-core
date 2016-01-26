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

import org.apache.hc.core5.annotation.Immutable;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseFactory;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.MethodNotSupportedException;
import org.apache.hc.core5.http.NotImplementedException;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.UnsupportedHttpVersionException;
import org.apache.hc.core5.http.entity.ContentType;
import org.apache.hc.core5.http.entity.EntityUtils;
import org.apache.hc.core5.http.entity.StringEntity;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.DefaultHttpResponseFactory;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.io.HttpRequestHandlerMapper;
import org.apache.hc.core5.http.io.HttpServerConnection;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpExpectationVerifier;
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
@Immutable
public class HttpService {

    private final HttpProcessor processor;
    private final HttpRequestHandlerMapper handlerMapper;
    private final ConnectionReuseStrategy connStrategy;
    private final HttpResponseFactory responseFactory;
    private final HttpExpectationVerifier expectationVerifier;

    /**
     * Create a new HTTP service.
     *
     * @param processor the processor to use on requests and responses
     * @param connStrategy the connection reuse strategy. If {@code null}
     *   {@link DefaultConnectionReuseStrategy#INSTANCE} will be used.
     * @param responseFactory  the response factory. If {@code null}
     *   {@link DefaultHttpResponseFactory#INSTANCE} will be used.
     * @param handlerMapper  the handler mapper. May be null.
     * @param expectationVerifier the expectation verifier. May be null.
     *
     * @since 4.3
     */
    public HttpService(
            final HttpProcessor processor,
            final ConnectionReuseStrategy connStrategy,
            final HttpResponseFactory responseFactory,
            final HttpRequestHandlerMapper handlerMapper,
            final HttpExpectationVerifier expectationVerifier) {
        super();
        this.processor =  Args.notNull(processor, "HTTP processor");
        this.connStrategy = connStrategy != null ? connStrategy :
            DefaultConnectionReuseStrategy.INSTANCE;
        this.responseFactory = responseFactory != null ? responseFactory :
            DefaultHttpResponseFactory.INSTANCE;
        this.handlerMapper = handlerMapper;
        this.expectationVerifier = expectationVerifier;
    }

    /**
     * Create a new HTTP service.
     *
     * @param processor the processor to use on requests and responses
     * @param connStrategy the connection reuse strategy. If {@code null}
     *   {@link DefaultConnectionReuseStrategy#INSTANCE} will be used.
     * @param responseFactory  the response factory. If {@code null}
     *   {@link DefaultHttpResponseFactory#INSTANCE} will be used.
     * @param handlerMapper  the handler mapper. May be null.
     *
     * @since 4.3
     */
    public HttpService(
            final HttpProcessor processor,
            final ConnectionReuseStrategy connStrategy,
            final HttpResponseFactory responseFactory,
            final HttpRequestHandlerMapper handlerMapper) {
        this(processor, connStrategy, responseFactory, handlerMapper, null);
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
        this(processor, null, null, handlerMapper, null);
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

        context.setAttribute(HttpCoreContext.HTTP_CONNECTION, conn);

        final HttpRequest request = conn.receiveRequestHeader();

        final Header expect = request.getFirstHeader(HttpHeaders.EXPECT);
        final boolean expectContinue = expect != null && "100-continue".equalsIgnoreCase(expect.getValue());

        HttpResponse response = null;

        if (expectContinue) {
            response = this.responseFactory.newHttpResponse(HttpStatus.SC_CONTINUE, context);
            if (this.expectationVerifier != null) {
                try {
                    this.expectationVerifier.verify(request, response, context);
                } catch (final HttpException ex) {
                    response = this.responseFactory.newHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, context);
                    handleException(ex, response);
                }
            }
            if (response.getCode() < HttpStatus.SC_SUCCESS) {
                // Send 1xx response indicating the server expectations
                // have been met
                conn.sendResponseHeader(response);
                conn.flush();
                response = null;
            } else {
                context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
                context.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
                this.processor.process(response, context);
                conn.sendResponseHeader(response);
                if (canResponseHaveBody(request, response)) {
                    conn.sendResponseEntity(response);
                }
                conn.flush();
                conn.receiveRequestEntity(request);
                return;
            }
        }

        try {
            conn.receiveRequestEntity(request);

            context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
            this.processor.process(request, context);

            response = this.responseFactory.newHttpResponse(HttpStatus.SC_OK, context);
            doService(request, response, context);

            context.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
            this.processor.process(response, context);

        } catch (final HttpException ex) {
            response = this.responseFactory.newHttpResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR, context);
            handleException(ex, response);
            context.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
            this.processor.process(response, context);
        }

        conn.sendResponseHeader(response);
        if (canResponseHaveBody(request, response)) {
            conn.sendResponseEntity(response);
        }
        conn.flush();

        // Make sure the request content is fully consumed
        final HttpEntity entity = request.getEntity();
        EntityUtils.consume(entity);

        if (!this.connStrategy.keepAlive(request, response, context)) {
            conn.close();
        }
    }

    private boolean canResponseHaveBody(final HttpRequest request, final HttpResponse response) {
        if (request != null && "HEAD".equalsIgnoreCase(request.getRequestLine().getMethod())) {
            return false;
        }
        final int status = response.getCode();
        return status >= HttpStatus.SC_SUCCESS
                && status != HttpStatus.SC_NO_CONTENT
                && status != HttpStatus.SC_NOT_MODIFIED
                && status != HttpStatus.SC_RESET_CONTENT;
    }

    /**
     * Handles the given exception and generates an HTTP response to be sent
     * back to the client to inform about the exceptional condition encountered
     * in the course of the request processing.
     *
     * @param ex the exception.
     * @param response the HTTP response.
     */
    protected void handleException(final HttpException ex, final HttpResponse response) {
        if (ex instanceof MethodNotSupportedException) {
            response.setStatusCode(HttpStatus.SC_NOT_IMPLEMENTED);
        } else if (ex instanceof UnsupportedHttpVersionException) {
            response.setStatusCode(HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED);
        } else if (ex instanceof NotImplementedException) {
            response.setStatusCode(HttpStatus.SC_NOT_IMPLEMENTED);
        } else if (ex instanceof ProtocolException) {
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
        } else {
            response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
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
     * {@link HttpRequestHandler#handle(HttpRequest, HttpResponse, HttpContext)}
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
            final HttpRequest request,
            final HttpResponse response,
            final HttpContext context) throws HttpException, IOException {
        HttpRequestHandler handler = null;
        if (this.handlerMapper != null) {
            handler = this.handlerMapper.lookup(request, context);
        }
        if (handler != null) {
            handler.handle(request, response, context);
        } else {
            response.setStatusCode(HttpStatus.SC_NOT_IMPLEMENTED);
        }
    }

}

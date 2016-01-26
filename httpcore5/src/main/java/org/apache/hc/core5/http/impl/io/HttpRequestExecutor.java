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
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.util.Args;

/**
 * {@code HttpRequestExecutor} is a client side HTTP protocol handler based
 * on the blocking (classic) I/O model.
 * <p>
 * {@code HttpRequestExecutor} relies on {@link HttpProcessor} to generate
 * mandatory protocol headers for all outgoing messages and apply common,
 * cross-cutting message transformations to all incoming and outgoing messages.
 * Application specific processing can be implemented outside
 * {@code HttpRequestExecutor} once the request has been executed and
 * a response has been received.
 *
 * @since 4.0
 */
@Immutable
public class HttpRequestExecutor {

    public static final int DEFAULT_WAIT_FOR_CONTINUE = 3000;

    private final int waitForContinue;

    /**
     * Creates new instance of HttpRequestExecutor.
     *
     * @since 4.3
     */
    public HttpRequestExecutor(final int waitForContinue) {
        super();
        this.waitForContinue = Args.positive(waitForContinue, "Wait for continue time");
    }

    public HttpRequestExecutor() {
        this(DEFAULT_WAIT_FOR_CONTINUE);
    }

    /**
     * Decide whether a response comes with an entity.
     * The implementation in this class is based on RFC 2616.
     * <p>
     * Derived executors can override this method to handle
     * methods and response codes not specified in RFC 2616.
     * </p>
     *
     * @param request   the request, to obtain the executed method
     * @param response  the response, to obtain the status code
     */
    protected boolean canResponseHaveBody(final HttpRequest request,
                                          final HttpResponse response) {

        if ("HEAD".equalsIgnoreCase(request.getRequestLine().getMethod())) {
            return false;
        }
        final int status = response.getCode();
        return status >= HttpStatus.SC_SUCCESS
            && status != HttpStatus.SC_NO_CONTENT
            && status != HttpStatus.SC_NOT_MODIFIED
            && status != HttpStatus.SC_RESET_CONTENT;
    }

    /**
     * Sends the request and obtain a response.
     *
     * @param request   the request to execute.
     * @param conn      the connection over which to execute the request.
     *
     * @return  the response to the request.
     *
     * @throws IOException in case of an I/O error.
     * @throws HttpException in case of HTTP protocol violation or a processing
     *   problem.
     */
    public HttpResponse execute(
            final HttpRequest request,
            final HttpClientConnection conn,
            final HttpContext context) throws IOException, HttpException {
        Args.notNull(request, "HTTP request");
        Args.notNull(conn, "Client connection");
        Args.notNull(context, "HTTP context");
        try {

            Args.notNull(request, "HTTP request");
            Args.notNull(conn, "Client connection");
            Args.notNull(context, "HTTP context");

            HttpResponse response = null;

            context.setAttribute(HttpCoreContext.HTTP_CONNECTION, conn);

            conn.sendRequestHeader(request);
            final HttpEntity entity = request.getEntity();
            if (entity != null) {
                final Header expect = request.getFirstHeader(HttpHeaders.EXPECT);
                final boolean expectContinue = expect != null && "100-continue".equalsIgnoreCase(expect.getValue());
                if (expectContinue) {

                    conn.flush();
                    // Don't wait for a 100-continue response forever. On timeout, send the entity.
                    if (conn.isDataAvailable(this.waitForContinue)) {
                        response = conn.receiveResponseHeader();
                        final int status = response.getCode();
                        if (status < HttpStatus.SC_SUCCESS) {
                            if (status != HttpStatus.SC_CONTINUE) {
                                throw new ProtocolException("Unexpected response: " + response.getStatusLine());
                            }
                            // discard 100-continue
                            response = null;
                            conn.sendRequestEntity(request);
                        } else {
                            if (canResponseHaveBody(request, response)) {
                                conn.receiveResponseEntity(response);
                            }
                            conn.terminateRequest(request);
                        }
                    } else {
                        conn.sendRequestEntity(request);
                    }
                } else {
                    conn.sendRequestEntity(request);
                }
            }
            conn.flush();

            while (response == null || response.getCode() < HttpStatus.SC_OK) {
                response = conn.receiveResponseHeader();
                if (canResponseHaveBody(request, response)) {
                    conn.receiveResponseEntity(response);
                }
            }
            return response;

        } catch (final HttpException | IOException | RuntimeException ex) {
            closeConnection(conn);
            throw ex;
        }
    }

    private static void closeConnection(final HttpClientConnection conn) {
        try {
            conn.close();
        } catch (final IOException ignore) {
        }
    }

    /**
     * Pre-process the given request using the given protocol processor and
     * initiates the process of request execution.
     *
     * @param request   the request to prepare
     * @param processor the processor to use
     * @param context   the context for sending the request
     *
     * @throws IOException in case of an I/O error.
     * @throws HttpException in case of HTTP protocol violation or a processing
     *   problem.
     */
    public void preProcess(
            final HttpRequest request,
            final HttpProcessor processor,
            final HttpContext context) throws HttpException, IOException {
        Args.notNull(request, "HTTP request");
        Args.notNull(processor, "HTTP processor");
        Args.notNull(context, "HTTP context");
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
        processor.process(request, context);
    }

    /**
     * Post-processes the given response using the given protocol processor and
     * completes the process of request execution.
     * <p>
     * This method does <i>not</i> read the response entity, if any.
     * The connection over which content of the response entity is being
     * streamed from cannot be reused until the response entity has been
     * fully consumed.
     *
     * @param response  the response object to post-process
     * @param processor the processor to use
     * @param context   the context for post-processing the response
     *
     * @throws IOException in case of an I/O error.
     * @throws HttpException in case of HTTP protocol violation or a processing
     *   problem.
     */
    public void postProcess(
            final HttpResponse response,
            final HttpProcessor processor,
            final HttpContext context) throws HttpException, IOException {
        Args.notNull(response, "HTTP response");
        Args.notNull(processor, "HTTP processor");
        Args.notNull(context, "HTTP context");
        context.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
        processor.process(response, context);
    }

} // class HttpRequestExecutor

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

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.UnsupportedHttpVersionException;
import org.apache.hc.core5.http.impl.DefaultConnectionReuseStrategy;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.io.HttpClientConnection;
import org.apache.hc.core5.http.io.HttpResponseInformationCallback;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;

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
@Contract(threading = ThreadingBehavior.IMMUTABLE)
public class HttpRequestExecutor {

    public static final Timeout DEFAULT_WAIT_FOR_CONTINUE = Timeout.ofSeconds(3);

    private final Timeout waitForContinue;
    private final ConnectionReuseStrategy connReuseStrategy;
    private final Http1StreamListener streamListener;

    /**
     * Creates new instance of HttpRequestExecutor.
     *
     * @since 4.3
     */
    public HttpRequestExecutor(
            final Timeout waitForContinue,
            final ConnectionReuseStrategy connReuseStrategy,
            final Http1StreamListener streamListener) {
        super();
        this.waitForContinue = Args.positive(waitForContinue, "Wait for continue time");
        this.connReuseStrategy = connReuseStrategy != null ? connReuseStrategy : DefaultConnectionReuseStrategy.INSTANCE;
        this.streamListener = streamListener;
    }

    public HttpRequestExecutor(final ConnectionReuseStrategy connReuseStrategy) {
        this(DEFAULT_WAIT_FOR_CONTINUE, connReuseStrategy, null);
    }

    public HttpRequestExecutor() {
        this(DEFAULT_WAIT_FOR_CONTINUE, null, null);
    }

    /**
     * Sends the request and obtain a response.
     *
     * @param request   the request to execute.
     * @param conn      the connection over which to execute the request.
     * @param informationCallback   callback to execute upon receipt of information status (1xx).
     *                              May be null.
     * @param context the context
     * @return  the response to the request.
     *
     * @throws IOException in case of an I/O error.
     * @throws HttpException in case of HTTP protocol violation or a processing
     *   problem.
     */
    public ClassicHttpResponse execute(
            final ClassicHttpRequest request,
            final HttpClientConnection conn,
            final HttpResponseInformationCallback informationCallback,
            final HttpContext context) throws IOException, HttpException {
        Args.notNull(request, "HTTP request");
        Args.notNull(conn, "Client connection");
        Args.notNull(context, "HTTP context");
        try {
            context.setAttribute(HttpCoreContext.SSL_SESSION, conn.getSSLSession());
            context.setAttribute(HttpCoreContext.CONNECTION_ENDPOINT, conn.getEndpointDetails());

            conn.sendRequestHeader(request);
            if (streamListener != null) {
                streamListener.onRequestHead(conn, request);
            }
            boolean expectContinue = false;
            final HttpEntity entity = request.getEntity();
            if (entity != null) {
                final Header expect = request.getFirstHeader(HttpHeaders.EXPECT);
                expectContinue = expect != null && HeaderElements.CONTINUE.equalsIgnoreCase(expect.getValue());
                if (!expectContinue) {
                    conn.sendRequestEntity(request);
                }
            }
            conn.flush();
            ClassicHttpResponse response = null;
            while (response == null) {
                if (expectContinue) {
                    if (conn.isDataAvailable(this.waitForContinue)) {
                        response = conn.receiveResponseHeader();
                        if (streamListener != null) {
                            streamListener.onResponseHead(conn, response);
                        }
                        final int status = response.getCode();
                        if (status == HttpStatus.SC_CONTINUE) {
                            // discard 100-continue
                            response = null;
                            conn.sendRequestEntity(request);
                        } else if (status < HttpStatus.SC_SUCCESS) {
                            if (informationCallback != null) {
                                informationCallback.execute(response, conn, context);
                            }
                            response = null;
                            continue;
                        } else if (status >= HttpStatus.SC_CLIENT_ERROR){
                            conn.terminateRequest(request);
                        } else {
                            conn.sendRequestEntity(request);
                        }
                    } else {
                        conn.sendRequestEntity(request);
                    }
                    conn.flush();
                    expectContinue = false;
                } else {
                    response = conn.receiveResponseHeader();
                    if (streamListener != null) {
                        streamListener.onResponseHead(conn, response);
                    }
                    final int status = response.getCode();
                    if (status < HttpStatus.SC_INFORMATIONAL) {
                        throw new ProtocolException("Invalid response: " + new StatusLine(response));
                    }
                    if (status < HttpStatus.SC_SUCCESS) {
                        if (informationCallback != null && status != HttpStatus.SC_CONTINUE) {
                            informationCallback.execute(response, conn, context);
                        }
                        response = null;
                    }
                }
            }
            if (MessageSupport.canResponseHaveBody(request.getMethod(), response)) {
                conn.receiveResponseEntity(response);
            }
            return response;

        } catch (final HttpException | IOException | RuntimeException ex) {
            Closer.closeQuietly(conn);
            throw ex;
        }
    }

    /**
     * Sends the request and obtain a response.
     *
     * @param request   the request to execute.
     * @param conn      the connection over which to execute the request.
     * @param context the context
     * @return  the response to the request.
     *
     * @throws IOException in case of an I/O error.
     * @throws HttpException in case of HTTP protocol violation or a processing
     *   problem.
     */
    public ClassicHttpResponse execute(
            final ClassicHttpRequest request,
            final HttpClientConnection conn,
            final HttpContext context) throws IOException, HttpException {
        return execute(request, conn, null, context);
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
            final ClassicHttpRequest request,
            final HttpProcessor processor,
            final HttpContext context) throws HttpException, IOException {
        Args.notNull(request, "HTTP request");
        Args.notNull(processor, "HTTP processor");
        Args.notNull(context, "HTTP context");
        final ProtocolVersion transportVersion = request.getVersion();
        if (transportVersion != null && transportVersion.greaterEquals(HttpVersion.HTTP_2)) {
            throw new UnsupportedHttpVersionException(transportVersion);
        }
        context.setProtocolVersion(transportVersion != null ? transportVersion : HttpVersion.HTTP_1_1);
        context.setAttribute(HttpCoreContext.HTTP_REQUEST, request);
        processor.process(request, request.getEntity(), context);
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
            final ClassicHttpResponse response,
            final HttpProcessor processor,
            final HttpContext context) throws HttpException, IOException {
        Args.notNull(response, "HTTP response");
        Args.notNull(processor, "HTTP processor");
        Args.notNull(context, "HTTP context");
        final ProtocolVersion transportVersion = response.getVersion();
        context.setProtocolVersion(transportVersion != null ? transportVersion : HttpVersion.HTTP_1_1);
        context.setAttribute(HttpCoreContext.HTTP_RESPONSE, response);
        processor.process(response, response.getEntity(), context);
    }

    /**
     * Determines whether the connection can be kept alive and is safe to be re-used for subsequent message exchanges.
     *
     * @param request current request object.
     * @param response  current response object.
     * @param connection actual connection.
     * @param context current context.
     * @return {@code true} is the connection can be kept-alive and re-used.
     * @throws IOException in case of an I/O error.
     */
    public boolean keepAlive(
            final ClassicHttpRequest request,
            final ClassicHttpResponse response,
            final HttpClientConnection connection,
            final HttpContext context) throws IOException {
        Args.notNull(connection, "HTTP connection");
        Args.notNull(request, "HTTP request");
        Args.notNull(response, "HTTP response");
        Args.notNull(context, "HTTP context");
        final boolean keepAlive = connection.isConsistent() && connReuseStrategy.keepAlive(request, response, context);
        if (streamListener != null) {
            streamListener.onExchangeComplete(connection, keepAlive);
        }
        return keepAlive;
    }

}

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
package org.apache.hc.core5.http.io.support;

import java.io.IOException;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpFilterChain;
import org.apache.hc.core5.http.io.HttpFilterHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIAuthority;

/**
 * Abstract HTTP request filter that implements standard HTTP authentication handshake.
 *
 * @param <T> authorization token representation.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public abstract class AbstractHttpServerAuthFilter<T> implements HttpFilterHandler {

    private final boolean respondImmediately;

    protected AbstractHttpServerAuthFilter(final boolean respondImmediately) {
        this.respondImmediately = respondImmediately;
    }

    /**
     * Parses authorization header value into an authentication token sent by the client
     * as a response to an authentication challenge.
     *
     * @param authorizationValue the authorization header value.
     * @param context the actual execution context.
     * @return authorization token
     */
    protected abstract T parseChallengeResponse(String authorizationValue, HttpContext context) throws HttpException;

    /**
     * Authenticates the client using the authentication token sent by the client
     * as a response to an authentication challenge.
     *
     * @param challengeResponse the authentication token sent by the client
     *                          as a response to an authentication challenge.
     * @param authority the URI authority.
     * @param requestUri the request URI.
     * @param context the actual execution context.
     * @return {@code true} if the client could be successfully authenticated {@code false} otherwise.
     */
    protected abstract boolean authenticate(T challengeResponse, URIAuthority authority, String requestUri, HttpContext context);

    /**
     * Generates an authentication challenge in case of unsuccessful authentication.
     *
     * @param challengeResponse the authentication token sent by the client
     *                          as a response to an authentication challenge
     *                          or {@code null} if the client has not sent any.
     * @param authority the URI authority.
     * @param requestUri the request URI.
     * @param context the actual execution context.
     * @return an authorization challenge value.
     */
    protected abstract String generateChallenge(T challengeResponse, URIAuthority authority, String requestUri, HttpContext context);

    /**
     * Generates response body for UNAUTHORIZED response.
     *
     * @param unauthorized the response to return as a result of authentication failure.
     * @return the response content entity.
     */
    protected HttpEntity generateResponseContent(final HttpResponse unauthorized) {
        return new StringEntity("Unauthorized");
    }

    @Override
    public final void handle(
            final ClassicHttpRequest request,
            final HttpFilterChain.ResponseTrigger responseTrigger,
            final HttpContext context,
            final HttpFilterChain chain) throws HttpException, IOException {
        final Header h = request.getFirstHeader(HttpHeaders.AUTHORIZATION);
        final T challengeResponse = h != null ? parseChallengeResponse(h.getValue(), context) : null;

        final URIAuthority authority = request.getAuthority();
        final String requestUri = request.getRequestUri();

        final boolean authenticated = authenticate(challengeResponse, authority, requestUri, context);
        final Header expect = request.getFirstHeader(HttpHeaders.EXPECT);
        final boolean expectContinue = expect != null && HeaderElements.CONTINUE.equalsIgnoreCase(expect.getValue());

        if (authenticated) {
            if (expectContinue) {
                responseTrigger.sendInformation(new BasicClassicHttpResponse(HttpStatus.SC_CONTINUE));
            }
            chain.proceed(request, responseTrigger, context);
        } else {
            final ClassicHttpResponse unauthorized = new BasicClassicHttpResponse(HttpStatus.SC_UNAUTHORIZED);
            unauthorized.addHeader(HttpHeaders.WWW_AUTHENTICATE, generateChallenge(challengeResponse, authority, requestUri, context));
            final HttpEntity responseContent = generateResponseContent(unauthorized);
            unauthorized.setEntity(responseContent);
            if (respondImmediately || expectContinue || request.getEntity() == null) {
                // Respond immediately
                responseTrigger.submitResponse(unauthorized);
                // Consume request body later
                EntityUtils.consume(request.getEntity());
            } else {
                // Consume request body first
                EntityUtils.consume(request.getEntity());
                // Respond later
                responseTrigger.submitResponse(unauthorized);
            }
        }
    }
}

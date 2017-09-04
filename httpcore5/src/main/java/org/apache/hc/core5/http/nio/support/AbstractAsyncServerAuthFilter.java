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
package org.apache.hc.core5.http.nio.support;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncFilterChain;
import org.apache.hc.core5.http.nio.AsyncFilterHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.net.URIAuthority;

/**
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public abstract class AbstractAsyncServerAuthFilter<T> implements AsyncFilterHandler {

    private final boolean respondImmediately;

    protected AbstractAsyncServerAuthFilter(final boolean respondImmediately) {
        this.respondImmediately = respondImmediately;
    }

    protected abstract T parseChallengeResponse(String authorizationValue, HttpContext context) throws HttpException;

    protected abstract boolean authenticate(T challengeResponse, URIAuthority authority, String requestUri, HttpContext context);

    protected abstract String generateChallenge(T challengeResponse, URIAuthority authority, String requestUri, HttpContext context);

    protected AsyncEntityProducer generateResponseContent(final HttpResponse unauthorized) {
        return new BasicAsyncEntityProducer("Unauthorized");
    }

    @Override
    public AsyncDataConsumer handle(
            final HttpRequest request,
            final EntityDetails entityDetails,
            final HttpContext context,
            final AsyncFilterChain.ResponseTrigger responseTrigger,
            final AsyncFilterChain chain) throws HttpException, IOException {
        final Header h = request.getFirstHeader(HttpHeaders.AUTHORIZATION);
        final T challengeResponse = h != null ? parseChallengeResponse(h.getValue(), context) : null;

        final URIAuthority authority = request.getAuthority();
        final String requestUri = request.getRequestUri();

        final boolean authenticated = authenticate(challengeResponse, authority, requestUri, context);
        final Header expect = request.getFirstHeader(HttpHeaders.EXPECT);
        final boolean expectContinue = expect != null && "100-continue".equalsIgnoreCase(expect.getValue());

        if (authenticated) {
            if (expectContinue) {
                responseTrigger.sendInformation(new BasicClassicHttpResponse(HttpStatus.SC_CONTINUE));
            }
            return chain.proceed(request, entityDetails, context, responseTrigger);
        } else {
            final HttpResponse unauthorized = new BasicHttpResponse(HttpStatus.SC_UNAUTHORIZED);
            unauthorized.addHeader(HttpHeaders.WWW_AUTHENTICATE, generateChallenge(challengeResponse, authority, requestUri, context));
            final AsyncEntityProducer responseContentProducer = generateResponseContent(unauthorized);
            if (respondImmediately || expectContinue || entityDetails == null) {
                responseTrigger.submitResponse(unauthorized, responseContentProducer);
                return null;
            } else {
                return new AsyncDataConsumer() {

                    @Override
                    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
                        capacityChannel.update(Integer.MAX_VALUE);
                    }

                    @Override
                    public int consume(final ByteBuffer src) throws IOException {
                        return Integer.MAX_VALUE;
                    }

                    @Override
                    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
                        responseTrigger.submitResponse(unauthorized, responseContentProducer);
                    }

                    @Override
                    public void releaseResources() {
                        if (responseContentProducer != null) {
                            responseContentProducer.releaseResources();
                        }
                    }

                };
            }
        }
    }

}

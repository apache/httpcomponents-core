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

package org.apache.http.nio.protocol;

import org.apache.http.HttpRequest;
import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.annotation.Contract;
import org.apache.http.protocol.UriPatternMatcher;
import org.apache.http.util.Args;

/**
 * Maintains a map of HTTP request handlers keyed by a request URI pattern.
 * <br>
 * Patterns may have three formats:
 * <ul>
 *   <li>{@code *}</li>
 *   <li>{@code *<uri>}</li>
 *   <li>{@code <uri>*}</li>
 * </ul>
 * <br>
 * This class can be used to map an instance of {@link HttpAsyncRequestHandler}
 * matching a particular request URI. Usually the mapped request handler
 * will be used to process the request with the specified request URI.
 *
 * @since 4.3
 */
@Contract(threading = ThreadingBehavior.SAFE)
public class UriHttpAsyncRequestHandlerMapper implements HttpAsyncRequestHandlerMapper {

    private final UriPatternMatcher<HttpAsyncRequestHandler<?>> matcher;

    protected UriHttpAsyncRequestHandlerMapper(final UriPatternMatcher<HttpAsyncRequestHandler<?>> matcher) {
        super();
        this.matcher = Args.notNull(matcher, "Pattern matcher");
    }

    /**
     * Gets the matcher.
     *
     * @return the matcher
     * @since 4.4.9
     */
    public UriPatternMatcher<HttpAsyncRequestHandler<?>> getUriPatternMatcher() {
        return matcher;
    }

    public UriHttpAsyncRequestHandlerMapper() {
        this(new UriPatternMatcher<HttpAsyncRequestHandler<?>>());
    }

    /**
     * Registers the given {@link HttpAsyncRequestHandler} as a handler for URIs
     * matching the given pattern.
     *
     * @param pattern the pattern to register the handler for.
     * @param handler the handler.
     */
    public void register(final String pattern, final HttpAsyncRequestHandler<?> handler) {
        matcher.register(pattern, handler);
    }

    /**
     * Removes registered handler, if exists, for the given pattern.
     *
     * @param pattern the pattern to unregister the handler for.
     */
    public void unregister(final String pattern) {
        matcher.unregister(pattern);
    }

    /**
     * Extracts request path from the given {@link HttpRequest}
     */
    protected String getRequestPath(final HttpRequest request) {
        String uriPath = request.getRequestLine().getUri();
        int index = uriPath.indexOf('?');
        if (index != -1) {
            uriPath = uriPath.substring(0, index);
        } else {
            index = uriPath.indexOf('#');
            if (index != -1) {
                uriPath = uriPath.substring(0, index);
            }
        }
        return uriPath;
    }

    @Override
    public String toString() {
        return getClass().getName() + " [matcher=" + matcher + "]";
    }

    /**
     * Looks up a handler matching the given request URI.
     *
     * @param request the request
     * @return handler or {@code null} if no match is found.
     */
    @Override
    public HttpAsyncRequestHandler<?> lookup(final HttpRequest request) {
        return matcher.lookup(getRequestPath(request));
    }

}

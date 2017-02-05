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
package org.apache.hc.core5.http.route;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.PostRouteInterceptor;
import org.apache.hc.core5.http.PreRouteInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;

import java.util.List;

/**
 * Default immutable implementation of {@link RouteProcessor}.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public final class DefaultRouteProcessor implements RouteProcessor {

    private final PreRouteInterceptor[] preRouteInterceptors;
    private final PostRouteInterceptor[] postRouteInterceptors;

    public DefaultRouteProcessor(
            final PreRouteInterceptor[] preRouteInterceptors,
            final PostRouteInterceptor[] postRouteInterceptors) {
        super();
        if (preRouteInterceptors != null) {
            final int l = preRouteInterceptors.length;
            this.preRouteInterceptors = new PreRouteInterceptor[l];
            System.arraycopy(preRouteInterceptors, 0, this.preRouteInterceptors, 0, l);
        }
        else {
            this.preRouteInterceptors = new PreRouteInterceptor[0];
        }
        if (postRouteInterceptors != null) {
            final int l = postRouteInterceptors.length;
            this.postRouteInterceptors = new PostRouteInterceptor[l];
            System.arraycopy(postRouteInterceptors, 0, this.postRouteInterceptors, 0, l);
        }
        else {
            this.postRouteInterceptors = new PostRouteInterceptor[0];
        }
    }

    /**
     * @since 4.3
     */
    public DefaultRouteProcessor(
            final List<PreRouteInterceptor> preRouteInterceptors,
            final List<PostRouteInterceptor> postRouteInterceptors) {
        super();
        if (preRouteInterceptors != null) {
            final int l = preRouteInterceptors.size();
            this.preRouteInterceptors = preRouteInterceptors.toArray(new PreRouteInterceptor[l]);
        }
        else {
            this.preRouteInterceptors = new PreRouteInterceptor[0];
        }
        if (postRouteInterceptors != null) {
            final int l = postRouteInterceptors.size();
            this.postRouteInterceptors = postRouteInterceptors.toArray(new PostRouteInterceptor[l]);
        }
        else {
            this.postRouteInterceptors = new PostRouteInterceptor[0];
        }
    }

    public DefaultRouteProcessor(final PreRouteInterceptor... preRouteInterceptors) {
        this(preRouteInterceptors, null);
    }

    public DefaultRouteProcessor(final PostRouteInterceptor... postRouteInterceptors) {
        this(null, postRouteInterceptors);
    }

    @Override
    public void preProcess(final HttpRequest request, final HttpContext context) {
        for (final PreRouteInterceptor preRouteInterceptor : this.preRouteInterceptors) {
            preRouteInterceptor.preProcess(request, context);
        }
    }

    @Override
    public void postProcess(final HttpRequest request, final HttpContext context) {
        for (final PostRouteInterceptor postRouteInterceptor : this.postRouteInterceptors) {
            postRouteInterceptor.postProcess(request, context);
        }
    }
}

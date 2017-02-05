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


import org.apache.hc.core5.http.PostRouteInterceptor;
import org.apache.hc.core5.http.PreRouteInterceptor;

/**
 * Builder for {@link RouteProcessor} instances.
 *
 * @since 5.0
 */
public class RouteProcessorBuilder {
    private ChainBuilder<PreRouteInterceptor> preRouteChainBuilder;
    private ChainBuilder<PostRouteInterceptor> postRouteChainBuilder;

    public static RouteProcessorBuilder create() {
        return new RouteProcessorBuilder();
    }

    RouteProcessorBuilder() {
        super();
    }

    private ChainBuilder<PreRouteInterceptor> getPreRouteChainBuilder() {
        if (preRouteChainBuilder == null) {
            preRouteChainBuilder = new ChainBuilder<PreRouteInterceptor>();
        }
        return preRouteChainBuilder;
    }

    private ChainBuilder<PostRouteInterceptor> getPostRouteChainBuilder() {
        if (postRouteChainBuilder == null) {
            postRouteChainBuilder = new ChainBuilder<PostRouteInterceptor>();
        }
        return postRouteChainBuilder;
    }

    public RouteProcessorBuilder addFirst(final PreRouteInterceptor e) {
        if (e == null) {
            return this;
        }
        getPreRouteChainBuilder().addFirst(e);
        return this;
    }

    public RouteProcessorBuilder addLast(final PreRouteInterceptor e) {
        if (e == null) {
            return this;
        }
        getPreRouteChainBuilder().addLast(e);
        return this;
    }

    public RouteProcessorBuilder add(final PreRouteInterceptor e) {
        return addLast(e);
    }

    public RouteProcessorBuilder addAllFirst(final PreRouteInterceptor... e) {
        if (e == null) {
            return this;
        }
        getPreRouteChainBuilder().addAllFirst(e);
        return this;
    }

    public RouteProcessorBuilder addAllLast(final PreRouteInterceptor... e) {
        if (e == null) {
            return this;
        }
        getPreRouteChainBuilder().addAllLast(e);
        return this;
    }

    public RouteProcessorBuilder addAll(final PreRouteInterceptor... e) {
        return addAllLast(e);
    }

    public RouteProcessorBuilder addFirst(final PostRouteInterceptor e) {
        if (e == null) {
            return this;
        }
        getPostRouteChainBuilder().addFirst(e);
        return this;
    }

    public RouteProcessorBuilder addLast(final PostRouteInterceptor e) {
        if (e == null) {
            return this;
        }
        getPostRouteChainBuilder().addLast(e);
        return this;
    }

    public RouteProcessorBuilder add(final PostRouteInterceptor e) {
        return addLast(e);
    }

    public RouteProcessorBuilder addAllFirst(final PostRouteInterceptor... e) {
        if (e == null) {
            return this;
        }
        getPostRouteChainBuilder().addAllFirst(e);
        return this;
    }

    public RouteProcessorBuilder addAllLast(final PostRouteInterceptor... e) {
        if (e == null) {
            return this;
        }
        getPostRouteChainBuilder().addAllLast(e);
        return this;
    }

    public RouteProcessorBuilder addAll(final PostRouteInterceptor... e) {
        return addAllLast(e);
    }

    public RouteProcessor build() {
        return new DefaultRouteProcessor(
                preRouteChainBuilder != null ? preRouteChainBuilder.build() : null,
                postRouteChainBuilder != null ? postRouteChainBuilder.build() : null);
    }
}

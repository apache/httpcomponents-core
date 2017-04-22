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
import org.apache.hc.core5.http.PostRouteInterceptor;
import org.apache.hc.core5.http.PreRouteInterceptor;

/**
 * HTTP route processor is a collection of route interceptors that
 * implements the 'Chain of Responsibility' pattern, where each individual
 * route interceptor is expected to work on a particular aspect of the HTTP
 * route the interceptor is responsible for.
 * <p>
 * Usually the order in which interceptors are executed should not matter as
 * long as they do not depend on a particular state of the execution context.
 * If route interceptors have interdependencies and therefore must be
 * executed in a particular order, they should be added to the route
 * processor in the same sequence as their expected execution order.
 * <p>
 * Route interceptors must be implemented as thread-safe. Similarly to
 * servlets, route interceptors should not use instance variables unless
 * access to those variables is synchronized.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public interface RouteProcessor extends PreRouteInterceptor, PostRouteInterceptor {
}

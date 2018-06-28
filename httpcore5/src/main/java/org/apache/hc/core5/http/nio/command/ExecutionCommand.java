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

package org.apache.hc.core5.http.nio.command;

import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.util.Args;

/**
 * Request execution command.
 *
 * @since 5.0
 */
public final class ExecutionCommand implements Command {

    private final AsyncClientExchangeHandler exchangeHandler;
    private final CancellableDependency cancellableDependency;
    private final HttpContext context;

    public ExecutionCommand(
            final AsyncClientExchangeHandler exchangeHandler,
            final CancellableDependency cancellableDependency,
            final HttpContext context) {
        this.exchangeHandler = Args.notNull(exchangeHandler, "Handler");
        this.cancellableDependency = cancellableDependency;
        this.context = context;
    }

    public ExecutionCommand(
            final AsyncClientExchangeHandler exchangeHandler,
            final HttpContext context) {
        this(exchangeHandler, null, context);
    }

    public AsyncClientExchangeHandler getExchangeHandler() {
        return exchangeHandler;
    }

    public CancellableDependency getCancellableDependency() {
        return cancellableDependency;
    }

    public HttpContext getContext() {
        return context;
    }

    @Override
    public boolean cancel() {
        exchangeHandler.cancel();
        return true;
    }

}

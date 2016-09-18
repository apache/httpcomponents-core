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

package org.apache.hc.core5.http2.nio.command;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http2.nio.AsyncRequestProducer;
import org.apache.hc.core5.http2.nio.AsyncResponseConsumer;
import org.apache.hc.core5.reactor.Command;

/**
 * Request execution command.
 *
 * @param <T> message processing result type.
 *
 * @since 5.0
 */
public final class ExecutionCommand<T> implements Command {

    private final AsyncRequestProducer requestProducer;
    private final AsyncResponseConsumer<T> responseConsumer;
    private final FutureCallback<T> callback;

    public ExecutionCommand(
            final AsyncRequestProducer requestProducer,
            final AsyncResponseConsumer<T> responseConsumer,
            final FutureCallback<T> callback) {
        this.requestProducer = requestProducer;
        this.responseConsumer = responseConsumer;
        this.callback = callback;
    }

    public AsyncRequestProducer getRequestProducer() {
        return requestProducer;
    }

    public AsyncResponseConsumer<T> getResponseConsumer() {
        return responseConsumer;
    }

    public FutureCallback<T> getCallback() {
        return callback;
    }

    @Override
    public boolean cancel() {
        try {
            requestProducer.releaseResources();
            responseConsumer.releaseResources();
        } finally {
            if (callback != null) {
                callback.cancelled();
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "Request: " + requestProducer;
    }


}

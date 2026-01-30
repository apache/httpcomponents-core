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
package org.apache.hc.core5.jackson2.http;

import java.io.IOException;

import org.apache.hc.core5.concurrent.CallbackContribution;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.jackson2.JsonConsumer;
import org.apache.hc.core5.util.Args;

class JsonSequenceResponseConsumer<T, E> extends AbstractJsonMessageConsumer<HttpResponse, T>
        implements AsyncResponseConsumer<T> {

    private final Supplier<AsyncEntityConsumer<E>> errorConsumerSupplier;
    private final JsonConsumer<HttpResponse> responseValidator;
    private final Callback<E> errorCallback;

    public JsonSequenceResponseConsumer(
            final Supplier<AsyncEntityConsumer<T>> jsonConsumerSupplier,
            final Supplier<AsyncEntityConsumer<E>> errorConsumerSupplier,
            final JsonConsumer<HttpResponse> responseValidator,
            final Callback<E> errorCallback) {
        super(jsonConsumerSupplier);
        this.errorConsumerSupplier = Args.notNull(errorConsumerSupplier, "Error consumer supplier");
        this.responseValidator = responseValidator;
        this.errorCallback = errorCallback;
    }

    @Override
    public void informationResponse(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
    }

    @Override
    public void consumeResponse(final HttpResponse response,
                                final EntityDetails entityDetails,
                                final HttpContext context,
                                final FutureCallback<T> resultCallback) throws HttpException, IOException {
        if (responseValidator != null) {
            responseValidator.accept(response);
        }
        if (response.getCode() >= HttpStatus.SC_REDIRECTION) {
            final AsyncEntityConsumer<E> entityConsumer = errorConsumerSupplier.get();
            handleContent(entityConsumer, entityDetails, new CallbackContribution<E>(resultCallback) {

                @Override
                public void completed(final E result) {
                    if (errorCallback != null) {
                        errorCallback.execute(result);
                    }
                }

            });
        } else {
            consumeMessage(response, entityDetails, context, new CallbackContribution<T>(resultCallback) {

                @Override
                public void completed(final T result) {
                    if (resultCallback != null) {
                        resultCallback.completed(result);
                    }
                }

            });
        }
    }

    @Override
    public void failed(final Exception cause) {
        super.failed(cause);
    }

}

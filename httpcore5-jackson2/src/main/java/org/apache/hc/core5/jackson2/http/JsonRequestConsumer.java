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
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;

final class JsonRequestConsumer<T> extends AbstractJsonMessageConsumer<HttpRequest, T>
        implements AsyncRequestConsumer<Message<HttpRequest, T>> {

    public JsonRequestConsumer(final Supplier<AsyncEntityConsumer<T>> jsonConsumerSupplier) {
        super(jsonConsumerSupplier);
    }

    @Override
    public void consumeRequest(final HttpRequest request,
                               final EntityDetails entityDetails,
                               final HttpContext context,
                               final FutureCallback<Message<HttpRequest, T>> resultCallback) throws HttpException, IOException {
        consumeMessage(request, entityDetails, context, new CallbackContribution<T>(resultCallback) {

            @Override
            public void completed(final T result) {
                resultCallback.completed(new Message<>(request, result));
            }

        });
    }

    @Override
    public void failed(final Exception cause) {
        super.failed(cause);
    }

}

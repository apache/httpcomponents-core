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
package org.apache.hc.core5.testing.nio;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.support.BasicRequestConsumer;
import org.apache.hc.core5.http.nio.support.AbstractServerExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * @since 5.0
 */
public abstract class MessageExchangeHandler<T> extends AbstractServerExchangeHandler<Message<HttpRequest, T>> {

    private final AsyncRequestConsumer<Message<HttpRequest, T>> requestConsumer;

    public MessageExchangeHandler(final AsyncRequestConsumer<Message<HttpRequest, T>> requestConsumer) {
        super();
        this.requestConsumer = requestConsumer;
    }

    public MessageExchangeHandler(final Supplier<AsyncEntityConsumer<T>> dataConsumerSupplier) {
        this(new BasicRequestConsumer<>(dataConsumerSupplier));
    }

    public MessageExchangeHandler(final AsyncEntityConsumer<T> entityConsumer) {
        this(new BasicRequestConsumer<>(entityConsumer));
    }

    @Override
    protected AsyncRequestConsumer<Message<HttpRequest, T>> supplyConsumer(
            final HttpRequest request,
            final EntityDetails entityDetails,
            final HttpContext context) throws HttpException {
        return requestConsumer;
    }

}

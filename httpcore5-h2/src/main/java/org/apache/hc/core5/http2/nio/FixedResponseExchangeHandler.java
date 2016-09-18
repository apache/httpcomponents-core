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
package org.apache.hc.core5.http2.nio;

import java.io.IOException;

import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.entity.ContentType;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http2.nio.entity.NoopEntityConsumer;
import org.apache.hc.core5.http2.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.util.Args;

/**
 * @since 5.0
 */
public final class FixedResponseExchangeHandler extends AbstractAsyncExchangeHandler<Void>{

    private final AsyncResponseProducer responseProducer;

    public FixedResponseExchangeHandler(final AsyncResponseProducer responseProducer) {
        super(new NoopEntityConsumer());
        this.responseProducer = Args.notNull(responseProducer, "Response producer");
    }

    public FixedResponseExchangeHandler(final HttpResponse response, final String message) {
        this(new BasicResponseProducer(response, new StringAsyncEntityProducer(message, ContentType.TEXT_PLAIN)));
    }

    public FixedResponseExchangeHandler(final int status, final String message) {
        this(new BasicHttpResponse(status), message);
    }

    @Override
    protected void handle(
            final Message<HttpRequest, Void> request,
            final AsyncResponseTrigger responseTrigger) throws IOException, HttpException {
        responseTrigger.submitResponse(responseProducer);
    }

}

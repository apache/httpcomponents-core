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
package org.apache.hc.core5.http.nio.support;

import java.io.IOException;

import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncPushProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.Args;

/**
 * Basic implementation of {@link AsyncPushProducer} that produces one fixed response
 * and relies on a {@link AsyncEntityProducer} to generate response entity stream.
 *
 * @since 5.0
 */
public class BasicPushProducer implements AsyncPushProducer {

    private final HttpResponse response;
    private final AsyncEntityProducer dataProducer;

    public BasicPushProducer(final HttpResponse response, final AsyncEntityProducer dataProducer) {
        this.response = Args.notNull(response, "Response");
        this.dataProducer = Args.notNull(dataProducer, "Entity producer");
    }

    public BasicPushProducer(final int code, final AsyncEntityProducer dataProducer) {
        this(new BasicHttpResponse(code), dataProducer);
    }

    public BasicPushProducer(final AsyncEntityProducer dataProducer) {
        this(HttpStatus.SC_OK, dataProducer);
    }

    @Override
    public void produceResponse(final ResponseChannel channel, final HttpContext httpContext) throws HttpException, IOException {
        channel.sendResponse(response, dataProducer, httpContext);
    }

    @Override
    public int available() {
        return dataProducer != null ? dataProducer.available() : 0;
    }

    @Override
    public void produce(final DataStreamChannel channel) throws IOException {
        if (dataProducer != null) {
            dataProducer.produce(channel);
        }
    }

    @Override
    public void failed(final Exception cause) {
        releaseResources();
    }

    @Override
    public void releaseResources() {
        if (dataProducer != null) {
            dataProducer.releaseResources();
        }
    }

}

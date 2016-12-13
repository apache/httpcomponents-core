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
import java.nio.ByteBuffer;
import java.util.List;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncResponseProducer;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.BasicResponseProducer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.ResponseChannel;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.util.Args;

/**
 * @since 5.0
 */
public final class ImmediateResponseExchangeHandler implements AsyncServerExchangeHandler {

    private final AsyncResponseProducer responseProducer;

    public ImmediateResponseExchangeHandler(final AsyncResponseProducer responseProducer) {
        this.responseProducer = Args.notNull(responseProducer, "Response producer");
    }

    public ImmediateResponseExchangeHandler(final HttpResponse response, final String message) {
        this(new BasicResponseProducer(response, new BasicAsyncEntityProducer(message, ContentType.TEXT_PLAIN)));
    }

    public ImmediateResponseExchangeHandler(final int status, final String message) {
        this(new BasicHttpResponse(status), message);
    }

    @Override
    public void handleRequest(
            final HttpRequest request,
            final EntityDetails entityDetails,
            final ResponseChannel responseChannel) throws HttpException, IOException {
        responseChannel.sendResponse(responseProducer.produceResponse(), responseProducer.getEntityDetails());
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        capacityChannel.update(Integer.MAX_VALUE);
    }

    @Override
    public int consume(final ByteBuffer src) throws IOException {
        return Integer.MAX_VALUE;
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
    }

    @Override
    public final int available() {
        return responseProducer.available();
    }

    @Override
    public final void produce(final DataStreamChannel channel) throws IOException {
        responseProducer.produce(channel);
    }

    @Override
    public final void failed(final Exception cause) {
        responseProducer.failed(cause);
        releaseResources();
    }

    @Override
    public final void releaseResources() {
        responseProducer.releaseResources();
    }

}

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
package org.apache.hc.core5.http2.impl.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.protocol.HttpContext;

class NoopAsyncPushHandler implements AsyncPushConsumer {

    @Override
    public void consumePromise(final HttpRequest promise,
                               final HttpResponse response,
                               final EntityDetails entityDetails,
                               final HttpContext context) throws HttpException, IOException {
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        capacityChannel.update(Integer.MAX_VALUE);
    }

    @Override
    public void consume(final ByteBuffer src) throws IOException {
    }

    @Override
    public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
    }

    @Override
    public void failed(final Exception cause) {
    }

    @Override
    public void releaseResources() {
    }

}

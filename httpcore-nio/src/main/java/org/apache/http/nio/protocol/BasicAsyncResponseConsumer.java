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
package org.apache.http.nio.protocol;

import java.io.IOException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.entity.BufferingNHttpEntity;
import org.apache.http.nio.entity.ConsumingNHttpEntity;
import org.apache.http.nio.util.ByteBufferAllocator;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.protocol.HttpContext;

/**
 * @since 4.2
 */
@ThreadSafe
public class BasicAsyncResponseConsumer extends AbstractAsyncResponseConsumer<HttpResponse> {

    private final ByteBufferAllocator allocator;
    private volatile HttpResponse response;
    private volatile ConsumingNHttpEntity consumer;

    public BasicAsyncResponseConsumer(final ByteBufferAllocator allocator) {
        super();
        if (allocator == null) {
            throw new IllegalArgumentException("Byte buffer allocator is null");
        }
        this.allocator = allocator;
    }

    public BasicAsyncResponseConsumer() {
        this(new HeapByteBufferAllocator());
    }

    @Override
    protected void onResponseReceived(final HttpResponse response) {
        this.response = response;
        HttpEntity entity = this.response.getEntity();
        if (entity != null) {
            this.consumer = new BufferingNHttpEntity(entity, this.allocator);
            this.response.setEntity(this.consumer);
        }
    }

    @Override
    protected void onContentReceived(
            final ContentDecoder decoder, final IOControl ioctrl) throws IOException {
        if (this.consumer == null) {
            throw new IllegalArgumentException("Content consumer is null");
        }
        this.consumer.consumeContent(decoder, ioctrl);
    }

    @Override
    protected void releaseResources() {
        this.response = null;
        this.consumer = null;
    }

    @Override
    protected HttpResponse buildResult(final HttpContext context) {
        return this.response;
    }

}

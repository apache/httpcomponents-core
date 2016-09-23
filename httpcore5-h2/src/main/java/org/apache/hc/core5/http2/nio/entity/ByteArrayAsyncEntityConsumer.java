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
package org.apache.hc.core5.http2.nio.entity;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.entity.ContentType;
import org.apache.hc.core5.http2.nio.CapacityChannel;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;
import org.apache.hc.core5.util.ByteArrayBuffer;

/**
 * @since 5.0
 */
public class ByteArrayAsyncEntityConsumer extends AbstractBinAsyncEntityConsumer<byte[]> {

    private final ByteArrayBuffer content;
    private final int capacityIncrement;

    private volatile FutureCallback<byte[]> resultCallback;

    public ByteArrayAsyncEntityConsumer(final int capacityIncrement) {
        this.capacityIncrement = Args.positive(capacityIncrement, "Capacity increment");
        this.content = new ByteArrayBuffer(128);
    }

    public ByteArrayAsyncEntityConsumer() {
        this(Integer.MAX_VALUE);
    }

    @Override
    public int capacity() {
        return capacityIncrement;
    }

    @Override
    public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        capacityChannel.update(capacityIncrement);
    }

    @Override
    protected void dataStart(
            final ContentType contentType,
            final FutureCallback<byte[]> resultCallback) throws HttpException, IOException {
        this.resultCallback = resultCallback;
    }

    @Override
    public void consumeData(final ByteBuffer src) throws IOException {
        final int chunk = src.remaining();
        content.ensureCapacity(chunk);
        src.get(content.array(), content.length(), chunk);
        content.setLength(content.length() + chunk);
    }

    @Override
    protected void dataEnd() throws IOException {
        Asserts.notNull(resultCallback, "Result callback");
        resultCallback.completed(content.toByteArray());
    }

    @Override
    public void releaseResources() {
    }

}

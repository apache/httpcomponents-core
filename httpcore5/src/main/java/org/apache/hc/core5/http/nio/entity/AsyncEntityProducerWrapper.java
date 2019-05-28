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

package org.apache.hc.core5.http.nio.entity;

import java.io.IOException;
import java.util.Set;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.util.Args;

/**
 * Base class for wrapping entity producers that delegates all calls to the wrapped producer.
 * Implementations can derive from this class and override only those methods that
 * should not be delegated to the wrapped producer.
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class AsyncEntityProducerWrapper implements AsyncEntityProducer {

    private final AsyncEntityProducer wrappedEntityProducer;

    public AsyncEntityProducerWrapper(final AsyncEntityProducer wrappedEntityProducer) {
        super();
        this.wrappedEntityProducer = Args.notNull(wrappedEntityProducer, "Wrapped entity producer");
    }

    @Override
    public boolean isRepeatable() {
        return wrappedEntityProducer.isRepeatable();
    }

    @Override
    public boolean isChunked() {
        return wrappedEntityProducer.isChunked();
    }

    @Override
    public long getContentLength() {
        return wrappedEntityProducer.getContentLength();
    }

    @Override
    public String getContentType() {
        return wrappedEntityProducer.getContentType();
    }

    @Override
    public String getContentEncoding() {
        return wrappedEntityProducer.getContentEncoding();
    }

    @Override
    public Set<String> getTrailerNames() {
        return wrappedEntityProducer.getTrailerNames();
    }

    @Override
    public int available() {
        return wrappedEntityProducer.available();
    }

    @Override
    public void produce(final DataStreamChannel channel) throws IOException {
        wrappedEntityProducer.produce(channel);
    }

    @Override
    public void failed(final Exception cause) {
        wrappedEntityProducer.failed(cause);
    }

    @Override
    public void releaseResources() {
        wrappedEntityProducer.releaseResources();
    }

    @Override
    public String toString() {
        return "Wrapper [" + wrappedEntityProducer + "]";
    }

}

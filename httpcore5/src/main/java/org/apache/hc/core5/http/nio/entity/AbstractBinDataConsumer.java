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
import java.nio.ByteBuffer;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;

/**
 * Abstract binary data consumer.
 *
 * @since 5.0
 */
public abstract class AbstractBinDataConsumer implements AsyncDataConsumer {

    private static final ByteBuffer EMPTY = ByteBuffer.wrap(new byte[0]);

    /**
     * Triggered to obtain the capacity increment.
     *
     * @return the number of bytes this consumer is prepared to process.
     */
    protected abstract int capacityIncrement();

    /**
     * Triggered to pass incoming data packet to the data consumer.
     *
     * @param src the data packet.
     * @param endOfStream flag indicating whether this data packet is the last in the data stream.
     *
     */
    protected abstract void data(ByteBuffer src, boolean endOfStream) throws IOException;

    /**
     * Triggered to signal completion of data processing.
     */
    protected abstract void completed() throws IOException;

    @Override
    public final void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
        capacityChannel.update(capacityIncrement());
    }

    @Override
    public final void consume(final ByteBuffer src) throws IOException {
        data(src, false);
    }

    @Override
    public final void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
        data(EMPTY, true);
        completed();
    }

}
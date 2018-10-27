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
package org.apache.hc.core5.http.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;

/**
 * Abstract asynchronous data consumer.
 *
 * @since 5.0
 */
public interface AsyncDataConsumer extends ResourceHolder {

    /**
     * Triggered to signal ability of the underlying data stream to receive
     * data capacity update. The data consumer can choose to write data
     * immediately inside the call or asynchronously at some later point.
     *
     * @param capacityChannel the channel for capacity updates.
     */
    void updateCapacity(CapacityChannel capacityChannel) throws IOException;

    /**
     * Triggered to pass incoming data to the data consumer. The consumer must
     * consume the entire content of the data buffer. The consumer must stop
     * incrementing its capacity on the capacity channel if it is unable to
     * accept more data. Once the data consumer has handled accumulated data
     * or allocated more intermediate storage it can update its capacity
     * information on the capacity channel.
     *
     * @param src data source.
     */
    void consume(ByteBuffer src) throws IOException;

    /**
     * Triggered to signal termination of the data stream.
     *
     * @param trailers data stream trailers.
     */
    void streamEnd(List<? extends Header> trailers) throws HttpException, IOException;

}

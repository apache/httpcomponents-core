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

/**
 * Abstract asynchronous data producer.
 *
 * @since 5.0
 */
public interface AsyncDataProducer extends ResourceHolder {

    /**
     * Returns the number of bytes immediately available for output.
     * This method can be used as a hint to control output events
     * of the underlying I/O session.
     * <p>
     * Please note this method should return zero if the data producer
     * is unable to produce any more data, in which case
     * {@link #produce(DataStreamChannel)} method will not get triggered.
     * The producer can resume writing out data asynchronously
     * once more data becomes available or request output readiness events
     * with {@link DataStreamChannel#requestOutput()}.
     *
     * @return the number of bytes immediately available for output
     *
     * @see #produce(DataStreamChannel)
     * @see DataStreamChannel#requestOutput()
     */
    int available();

    /**
     * Triggered to signal the ability of the underlying data channel
     * to accept more data. The data producer can choose to write data
     * immediately inside the call or asynchronously at some later point.
     * <p>
     * Please note this method gets triggered only if {@link #available()}
     * returns a positive value.
     *
     * @param channel the data channel capable of accepting more data.
     * @throws IOException in case of an I/O error.
     * @see #available()
     */
    void produce(DataStreamChannel channel) throws IOException;

}

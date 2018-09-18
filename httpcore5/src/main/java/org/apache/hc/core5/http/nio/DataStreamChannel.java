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

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.Header;

/**
 * Abstract byte stream channel
 * <p>
 * Implementations are expected to be thread-safe.
 * </p>
 *
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.SAFE)
public interface DataStreamChannel extends StreamChannel<ByteBuffer> {

    /**
     * Signals intent by the data producer to produce more data.
     * Once the channel is able to accept data its handler is expected
     * to trigger an event to notify the data producer.
     */
    void requestOutput();

    /**
     * Writes data from the buffer through this channel into the underlying byte stream.
     * If the underlying byte stream is temporarily unable to accept more data
     * it can return zero to indicate that no data could be written to the data
     * stream. The data producer can choose to call {@link #requestOutput()}
     * to signal its intent to produce more data.
     *
     * @param src source of data
     *
     * @return The number of bytes written, possibly zero
     */
    @Override
    int write(ByteBuffer src) throws IOException;

    /**
     * Terminates the underlying data stream and optionally writes
     * a closing sequence with the given trailers.
     * <p>
     * Please note that some data streams may not support trailers
     * and may silently ignore the trailers parameter.
     * </p>
     */
    void endStream(List<? extends Header> trailers) throws IOException;

}

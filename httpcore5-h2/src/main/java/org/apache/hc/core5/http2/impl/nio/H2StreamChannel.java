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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.nio.AsyncPushProducer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http2.H2Error;

interface H2StreamChannel extends DataStreamChannel, CapacityChannel, Cancellable {

    int getId();

    AtomicInteger getOutputWindow();

    AtomicInteger getInputWindow();

    void submit(List<Header> headers, boolean endStream) throws HttpException, IOException;

    void push(List<Header> headers, AsyncPushProducer pushProducer) throws HttpException, IOException;

    boolean isLocalClosed();

    void markLocalClosed();

    boolean localReset(int errorCode) throws IOException;

    default boolean localReset(H2Error error) throws IOException {
        return localReset(error != null ? error.getCode() : H2Error.INTERNAL_ERROR.getCode());
    }

    default void terminate() {
        try {
            localReset(H2Error.INTERNAL_ERROR);
        } catch (final IOException ignore) {
        }
    }

    long getLocalResetTime();

    default boolean isLocalReset() {
        return getLocalResetTime() > 0;
    }

}

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

package org.apache.hc.core5.http.io;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.ClassicHttpRequest;

import java.io.IOException;
import java.io.InputStream;

/**
 * Represents a strategy to determine how frequently the client should check for an out of order response.
 * An out of order response is sent before the server has read the full request. If the client fails to
 * check for an early response then a {@link java.net.SocketException} or {@link java.net.SocketTimeoutException}
 * may be thrown while writing the request entity after a timeout is reached on either the client or server.
 *
 * @since 5.1
 */
@Internal
public interface ResponseOutOfOrderStrategy {

    /**
     * Called before each write to the to a socket {@link java.io.OutputStream} with the number of
     * bytes that have already been sent, and the size of the write that will occur if this check
     * does not encounter an out of order response.
     *
     * @param request The current request.
     * @param connection The connection used to send the current request.
     * @param inputStream The response stream, this may be used to check for an early response.
     * @param totalBytesSent Number of bytes that have already been sent.
     * @param nextWriteSize The size of a socket write operation that will follow this check.
     * @return True if an early response was detected, otherwise false.
     * @throws IOException in case of a network failure while checking for an early response.
     */
    boolean isEarlyResponseDetected(
            ClassicHttpRequest request,
            HttpClientConnection connection,
            InputStream inputStream,
            long totalBytesSent,
            long nextWriteSize) throws IOException;
}

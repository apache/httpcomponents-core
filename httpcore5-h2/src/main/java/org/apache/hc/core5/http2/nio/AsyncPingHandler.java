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
package org.apache.hc.core5.http2.nio;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.hc.core5.http.HttpException;

/**
 * Abstract asynchronous ping exchange handler.
 *
 * @since 5.0
 */
public interface AsyncPingHandler {

    /**
     * Returns content of ping message.
     *
     * @return the ping message content.
     */
    ByteBuffer getData();

    /**
     * Triggered to signal receipt of a ping response message.
     *
     * @param feedback the ping message feedback.
     */
    void consumeResponse(ByteBuffer feedback) throws HttpException, IOException;

    /**
     * Triggered to signal a failure in data processing.
     *
     * @param cause the cause of the failure.
     */
    void failed(Exception cause);

    /**
     * Triggered to cancel the message exchange.
     */
    void cancel();

}

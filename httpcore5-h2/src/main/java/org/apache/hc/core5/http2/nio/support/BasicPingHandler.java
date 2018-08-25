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
package org.apache.hc.core5.http2.nio.support;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http2.nio.AsyncPingHandler;
import org.apache.hc.core5.util.Args;

/**
 * Basic {@link AsyncPingHandler} implementation.
 *
 * @since 5.0
 */
public class BasicPingHandler implements AsyncPingHandler {

    private static final byte[] PING_MESSAGE = new byte[] {'*', '*', 'p', 'i', 'n', 'g', '*', '*'};

    private final Callback<Boolean> callback;

    public BasicPingHandler(final Callback<Boolean> callback) {
        this.callback = Args.notNull(callback, "Callback");
    }

    @Override
    public ByteBuffer getData() {
        return ByteBuffer.wrap(PING_MESSAGE);
    }

    @Override
    public void consumeResponse(final ByteBuffer feedback) throws HttpException, IOException {
        boolean result = true;
        for (int i = 0; i < PING_MESSAGE.length; i++) {
            if (!feedback.hasRemaining() || PING_MESSAGE[i] != feedback.get()) {
                result = false;
                break;
            }
        }
        callback.execute(result);
    }

    @Override
    public void failed(final Exception cause) {
        callback.execute(Boolean.FALSE);
    }

    @Override
    public void cancel() {
        callback.execute(Boolean.FALSE);
    }

}

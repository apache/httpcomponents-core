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

package org.apache.hc.core5.http2.frame;

import java.nio.ByteBuffer;

import org.apache.hc.core5.util.Args;

/**
 * Default {@link FrameFactory} implementation.
 *
 * @since 5.0
 */
public class DefaultFrameFactory extends FrameFactory {

    public static final FrameFactory INSTANCE = new DefaultFrameFactory();

    @Override
    public RawFrame createHeaders(final int streamId, final ByteBuffer payload, final boolean endHeaders, final boolean endStream) {
        Args.positive(streamId, "Stream id");
        final int flags = (endHeaders ? FrameFlag.END_HEADERS.value : 0) | (endStream ? FrameFlag.END_STREAM.value : 0);
        return new RawFrame(FrameType.HEADERS.getValue(), flags, streamId, payload);
    }

    @Override
    public RawFrame createContinuation(final int streamId, final ByteBuffer payload, final boolean endHeaders) {
        Args.positive(streamId, "Stream id");
        final int flags = (endHeaders ? FrameFlag.END_HEADERS.value : 0);
        return new RawFrame(FrameType.CONTINUATION.getValue(), flags, streamId, payload);
    }

    @Override
    public RawFrame createPushPromise(final int streamId, final ByteBuffer payload, final boolean endHeaders) {
        Args.positive(streamId, "Stream id");
        final int flags = (endHeaders ? FrameFlag.END_HEADERS.value : 0);
        return new RawFrame(FrameType.PUSH_PROMISE.getValue(), flags, streamId, payload);
    }

    @Override
    public RawFrame createData(final int streamId, final ByteBuffer payload, final boolean endStream) {
        Args.positive(streamId, "Stream id");
        final int flags = (endStream ? FrameFlag.END_STREAM.value : 0);
        return new RawFrame(FrameType.DATA.getValue(), flags, streamId, payload);
    }

}

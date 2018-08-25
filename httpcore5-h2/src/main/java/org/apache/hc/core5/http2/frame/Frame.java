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

/**
 * Abstract HTTP/2 data frame.
 *
 * @since 5.0
 *
 * @param <T> frame payload representation.
 */
public abstract class Frame<T> {

    private final int type;
    private final int flags;
    private final int streamId;

    public Frame(final int type, final int flags, final int streamId) {
        this.type = type;
        this.flags = flags;
        this.streamId = streamId;
    }

    public boolean isType(final FrameType type) {
        return getType() == type.value;
    }

    public boolean isFlagSet(final FrameFlag flag) {
        return (getFlags() & flag.value) != 0;
    }

    public int getType() {
        return type;
    }

    public int getFlags() {
        return flags;
    }

    public int getStreamId() {
        return streamId;
    }

    public abstract T getPayload();

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("[");
        sb.append("type=").append(type);
        sb.append(", flags=").append(flags);
        sb.append(", streamId=").append(streamId);
        sb.append(", payoad=").append(getPayload());
        sb.append(']');
        return sb.toString();
    }

}

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

package org.apache.hc.core5.http.impl.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import org.apache.hc.core5.util.Args;

/**
 * A buffer that expand its capacity on demand. Internally, this class is backed
 * by an instance of {@link ByteBuffer}.
 * <p>
 * This class is not thread safe.
 * </p>
 * @since 5.0
 */
public class BufferedData extends ExpandableBuffer {

    public static BufferedData allocate(final int bufferSize) {
        return new BufferedData(bufferSize);
    }

    protected BufferedData(final int bufferSize) {
        super(bufferSize);
    }

    @Override
    public final boolean hasData() {
        return super.hasData();
    }

    @Override
    public final int length() {
        return super.length();
    }

    @Override
    public final int capacity() {
        return super.capacity();
    }

    @Override
    public final void clear() {
        super.clear();
    }

    public final void put(final ByteBuffer src) {
        Args.notNull(src, "Data source");
        setInputMode();
        final int requiredCapacity = buffer().position() + src.remaining();
        ensureAdjustedCapacity(requiredCapacity);
        buffer().put(src);
    }

    public final int readFrom(final ReadableByteChannel channel) throws IOException {
        Args.notNull(channel, "Channel");
        setInputMode();
        if (!buffer().hasRemaining()) {
            expand();
        }
        return channel.read(buffer());
    }

    public final int writeTo(final WritableByteChannel dst) throws IOException {
        if (dst == null) {
            return 0;
        }
        setOutputMode();
        return dst.write(buffer());
    }

    public final ByteBuffer data() {
        setOutputMode();
        return buffer();
    }

}

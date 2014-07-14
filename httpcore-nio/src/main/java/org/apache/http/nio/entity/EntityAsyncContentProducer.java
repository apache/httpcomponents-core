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

package org.apache.http.nio.entity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import org.apache.http.HttpEntity;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.util.Args;

/**
 * Basic implementation of {@link HttpAsyncContentProducer} that relies on
 * inefficient and potentially blocking I/O operation redirection through
 * {@link Channels#newChannel(java.io.InputStream)}.
 *
 * @since 4.2
 */
@NotThreadSafe
public class EntityAsyncContentProducer implements HttpAsyncContentProducer {

    private final HttpEntity entity;
    private final ByteBuffer buffer;
    private ReadableByteChannel channel;

    public EntityAsyncContentProducer(final HttpEntity entity) {
        super();
        Args.notNull(entity, "HTTP entity");
        this.entity = entity;
        this.buffer = ByteBuffer.allocate(4096);
    }

    @Override
    public void produceContent(
            final ContentEncoder encoder, final IOControl ioctrl) throws IOException {
        if (this.channel == null) {
            this.channel = Channels.newChannel(this.entity.getContent());
        }
        final int i = this.channel.read(this.buffer);
        this.buffer.flip();
        encoder.write(this.buffer);
        final boolean buffering = this.buffer.hasRemaining();
        this.buffer.compact();
        if (i == -1 && !buffering) {
            encoder.complete();
            close();
        }
    }

    @Override
    public boolean isRepeatable() {
        return this.entity.isRepeatable();
    }

    @Override
    public void close() throws IOException {
        final ReadableByteChannel local = this.channel;
        this.channel = null;
        if (local != null) {
            local.close();
        }
        if (this.entity.isStreaming()) {
            final InputStream instream = this.entity.getContent();
            instream.close();
        }
    }

    @Override
    public String toString() {
        return this.entity.toString();
    }

}

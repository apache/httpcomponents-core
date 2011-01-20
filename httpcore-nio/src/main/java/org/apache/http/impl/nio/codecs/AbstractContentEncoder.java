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

package org.apache.http.impl.nio.codecs;

import java.io.IOException;
import java.nio.channels.WritableByteChannel;

import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.reactor.SessionOutputBuffer;

/**
 * Abstract {@link ContentEncoder} that serves as a base for all content
 * encoder implementations.
 *
 * @since 4.0
 */
public abstract class AbstractContentEncoder implements ContentEncoder {

    protected final WritableByteChannel channel;
    protected final SessionOutputBuffer buffer;
    protected final HttpTransportMetricsImpl metrics;

    protected boolean completed;

    /**
     * Creates an instance of this class.
     *
     * @param channel the destination channel.
     * @param buffer the session output buffer that can be used to store
     *    session data for intermediate processing.
     * @param metrics Transport metrics of the underlying HTTP transport.
     */
    public AbstractContentEncoder(
            final WritableByteChannel channel,
            final SessionOutputBuffer buffer,
            final HttpTransportMetricsImpl metrics) {
        super();
        if (channel == null) {
            throw new IllegalArgumentException("Channel may not be null");
        }
        if (buffer == null) {
            throw new IllegalArgumentException("Session input buffer may not be null");
        }
        if (metrics == null) {
            throw new IllegalArgumentException("Transport metrics may not be null");
        }
        this.buffer = buffer;
        this.channel = channel;
        this.metrics = metrics;
    }

    public boolean isCompleted() {
        return this.completed;
    }

    public void complete() throws IOException {
        this.completed = true;
    }

    protected void assertNotCompleted() {
        if (this.completed) {
            throw new IllegalStateException("Encoding process already completed");
        }
    }

}

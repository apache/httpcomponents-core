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

package org.apache.http.impl;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionOutputBufferImpl;

/**
 * {@link org.apache.http.io.SessionOutputBuffer} mockup implementation.
 *
 */
public class SessionOutputBufferMock extends SessionOutputBufferImpl {

    public static final int BUFFER_SIZE = 16;

    private final ByteArrayOutputStream buffer;

    public SessionOutputBufferMock(
            final ByteArrayOutputStream buffer,
            final int buffersize,
            final int fragementSizeHint,
            final CharsetEncoder encoder) {
        super(new HttpTransportMetricsImpl(), buffersize, fragementSizeHint, encoder);
        bind(buffer);
        this.buffer = buffer;
    }

    public SessionOutputBufferMock(
            final ByteArrayOutputStream buffer,
            final int buffersize) {
        this(buffer, buffersize, buffersize, null);
    }

    public SessionOutputBufferMock(
            final CharsetEncoder encoder) {
        this(new ByteArrayOutputStream(), BUFFER_SIZE, BUFFER_SIZE, encoder);
    }

    public SessionOutputBufferMock(
            final Charset charset) {
        this(new ByteArrayOutputStream(), BUFFER_SIZE, BUFFER_SIZE,
                charset != null ? charset.newEncoder() : null);
    }

    public SessionOutputBufferMock(final ByteArrayOutputStream buffer) {
        this(buffer, BUFFER_SIZE, BUFFER_SIZE, null);
    }

    public SessionOutputBufferMock() {
        this(new ByteArrayOutputStream());
    }

    public byte[] getData() {
        if (this.buffer != null) {
            return this.buffer.toByteArray();
        } else {
            return new byte[] {};
        }
    }

}

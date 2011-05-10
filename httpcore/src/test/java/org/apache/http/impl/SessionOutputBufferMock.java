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
import java.io.OutputStream;

import org.apache.http.impl.io.AbstractSessionOutputBuffer;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

/**
 * {@link org.apache.http.io.SessionOutputBuffer} mockup implementation.
 *
 */
public class SessionOutputBufferMock extends AbstractSessionOutputBuffer {

    private ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    public static final int BUFFER_SIZE = 16;

    public SessionOutputBufferMock(
            final OutputStream outstream,
            int buffersize,
            final HttpParams params) {
        super();
        init(outstream, buffersize, params);
    }

    public SessionOutputBufferMock(
            final OutputStream outstream,
            int buffersize) {
        this(outstream, buffersize, new BasicHttpParams());
    }

    public SessionOutputBufferMock(
            final ByteArrayOutputStream buffer,
            final HttpParams params) {
        this(buffer, BUFFER_SIZE, params);
        this.buffer = buffer;
    }

    public SessionOutputBufferMock(
            final ByteArrayOutputStream buffer) {
        this(buffer, BUFFER_SIZE, new BasicHttpParams());
        this.buffer = buffer;
    }

    public SessionOutputBufferMock(final HttpParams params) {
        this(new ByteArrayOutputStream(), params);
    }

    public SessionOutputBufferMock() {
        this(new ByteArrayOutputStream(), new BasicHttpParams());
    }

    public byte[] getData() {
        if (this.buffer != null) {
            return this.buffer.toByteArray();
        } else {
            return new byte[] {};
        }
    }

}

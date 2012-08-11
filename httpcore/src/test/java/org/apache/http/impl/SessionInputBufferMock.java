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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;

import org.apache.http.Consts;
import org.apache.http.impl.io.AbstractSessionInputBuffer;

/**
 * {@link org.apache.http.io.SessionInputBuffer} mockup implementation.
 */
public class SessionInputBufferMock extends AbstractSessionInputBuffer {

    public static final int BUFFER_SIZE = 16;

    public SessionInputBufferMock(
            final InputStream instream, 
            int buffersize, 
            final Charset charset,
            int maxLineLen,
            int minChunkLimit,
            final CodingErrorAction malformedInputAction,
            final CodingErrorAction unmappableInputAction) {
        super(instream, buffersize, charset, maxLineLen, minChunkLimit, 
                malformedInputAction, unmappableInputAction);
    }

    public SessionInputBufferMock(
            final InputStream instream,
            int buffersize) {
        this(instream, buffersize, null, -1, -1, null, null);
    }

    public SessionInputBufferMock(
            final byte[] bytes,
            int buffersize,
            final Charset charset,
            int maxLineLen,
            int minChunkLimit,
            final CodingErrorAction malformedInputAction,
            final CodingErrorAction unmappableInputAction) {
        this(new ByteArrayInputStream(bytes), buffersize, charset, maxLineLen, minChunkLimit, 
                malformedInputAction, unmappableInputAction);
    }

    public SessionInputBufferMock(
            final byte[] bytes,
            int buffersize,
            int maxLineLen) {
        this(new ByteArrayInputStream(bytes), buffersize, Consts.ASCII, maxLineLen, -1, null, null);
    }

    public SessionInputBufferMock(
            final byte[] bytes,
            int buffersize) {
        this(new ByteArrayInputStream(bytes), buffersize);
    }

    public SessionInputBufferMock(
            final byte[] bytes) {
        this(bytes, BUFFER_SIZE);
    }

    public SessionInputBufferMock(
            final byte[] bytes, final Charset charset) {
        this(bytes, BUFFER_SIZE, charset, -1, -1, null, null);
    }

    public SessionInputBufferMock(
            final byte[] bytes, 
            final Charset charset,
            final CodingErrorAction malformedInputAction,
            final CodingErrorAction unmappableInputAction) {
        this(bytes, BUFFER_SIZE, charset, -1, -1, malformedInputAction, unmappableInputAction);
    }

    public SessionInputBufferMock(
            final String s,
            final Charset charset)
        throws UnsupportedEncodingException {
        this(s.getBytes(charset.name()), BUFFER_SIZE, charset, -1, -1, null, null);

    }

    public boolean isDataAvailable(int timeout) throws IOException {
        return true;
    }

}

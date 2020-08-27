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

package org.apache.hc.core5.http.nio.entity;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.util.ByteArrayBuffer;

/**
 * Basic {@link org.apache.hc.core5.http.nio.AsyncEntityConsumer} implementation
 * that processes the data stream content into a byte array.
 *
 * @since 5.0
 */
public class BasicAsyncEntityConsumer extends AbstractBinAsyncEntityConsumer<byte[]> {

    private final ByteArrayBuffer buffer;

    public BasicAsyncEntityConsumer() {
        super();
        this.buffer = new ByteArrayBuffer(1024);
    }

    @Override
    protected void streamStart(final ContentType contentType) throws HttpException, IOException {
    }

    @Override
    protected int capacityIncrement() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected void data(final ByteBuffer src, final boolean endOfStream) throws IOException {
        buffer.append(src);
    }

    @Override
    protected byte[] generateContent() throws IOException {
        return buffer.toByteArray();
    }

    @Override
    public void releaseResources() {
        buffer.clear();
    }

}

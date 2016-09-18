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
package org.apache.hc.core5.http2.nio.entity;

import java.io.IOException;
import java.nio.CharBuffer;

import org.apache.hc.core5.http.entity.ContentType;
import org.apache.hc.core5.http2.nio.StreamChannel;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;

public class StringAsyncEntityProducer extends AbstractCharAsyncEntityProducer {

    private final CharBuffer content;

    public StringAsyncEntityProducer(final CharSequence content, final int bufferSize, final ContentType contentType) {
        super(bufferSize, contentType);
        Args.notNull(content, "Content");
        this.content = CharBuffer.wrap(content);
    }

    public StringAsyncEntityProducer(final CharSequence content, final ContentType contentType) {
        this(content, 4096, contentType);
    }

    @Override
    protected void dataStart(final StreamChannel<CharBuffer> channel) throws IOException {
    }

    @Override
    public int available() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected void produceData(final StreamChannel<CharBuffer> channel) throws IOException {
        Asserts.notNull(channel, "StreamChannel");
        channel.write(content);
        if (!content.hasRemaining()) {
            channel.endStream();
        }
    }

    @Override
    public void releaseResources() {
    }

}

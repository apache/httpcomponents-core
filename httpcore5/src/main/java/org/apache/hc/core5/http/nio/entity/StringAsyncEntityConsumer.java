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
import java.nio.CharBuffer;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Basic {@link org.apache.hc.core5.http.nio.AsyncEntityConsumer} implementation
 * that processes the data stream content into a string.
 *
 * @since 5.0
 */
public class StringAsyncEntityConsumer extends AbstractCharAsyncEntityConsumer<String> {

    private final int capacityIncrement;
    private final CharArrayBuffer content;

    public StringAsyncEntityConsumer(final int bufSize, final int capacityIncrement, final CharCodingConfig charCodingConfig) {
        super(bufSize, charCodingConfig);
        this.capacityIncrement = Args.positive(capacityIncrement, "Capacity increment");
        this.content = new CharArrayBuffer(1024);
    }

    public StringAsyncEntityConsumer(final int capacityIncrement) {
        this(DEF_BUF_SIZE, capacityIncrement, CharCodingConfig.DEFAULT);
    }

    public StringAsyncEntityConsumer(final CharCodingConfig charCodingConfig) {
        this(DEF_BUF_SIZE, Integer.MAX_VALUE, charCodingConfig);
    }

    public StringAsyncEntityConsumer() {
        this(Integer.MAX_VALUE);
    }

    @Override
    protected final void streamStart(final ContentType contentType) throws HttpException, IOException {
    }

    @Override
    protected int capacityIncrement() {
        return capacityIncrement;
    }

    @Override
    protected final void data(final CharBuffer src, final boolean endOfStream) {
        Args.notNull(src, "CharBuffer");
        final int chunk = src.remaining();
        content.ensureCapacity(chunk);
        src.get(content.array(), content.length(), chunk);
        content.setLength(content.length() + chunk);
    }

    @Override
    public String generateContent() {
        return content.toString();
    }

    @Override
    public void releaseResources() {
        content.clear();
    }

}

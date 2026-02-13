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
import java.util.function.Function;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * {@link org.apache.hc.core5.http.nio.AsyncEntityConsumer} implementation
 * that processes the data stream content into a {@link CharSequence}.
 *
 * @since 5.5
 */
public class CharSequenceAsyncEntityConsumer<T> extends AbstractCharAsyncEntityConsumer<T> {

    private final int capacityIncrement;
    private final CharArrayBuffer content;
    private final Function<CharSequence, T> transformation;

    public CharSequenceAsyncEntityConsumer(
            final int bufSize,
            final int capacityIncrement,
            final CharCodingConfig charCodingConfig,
            final Function<CharSequence, T> transformation) {
        super(bufSize, charCodingConfig);
        this.capacityIncrement = Args.positive(capacityIncrement, "Capacity increment");
        this.content = new CharArrayBuffer(1024);
        this.transformation = transformation;
    }

    public CharSequenceAsyncEntityConsumer(final int capacityIncrement, final Function<CharSequence, T> transformation) {
        this(DEF_BUF_SIZE, capacityIncrement, CharCodingConfig.DEFAULT, transformation);
    }

    public CharSequenceAsyncEntityConsumer(
            final CharCodingConfig charCodingConfig,
            final Function<CharSequence, T> transformation) {
        this(DEF_BUF_SIZE, Integer.MAX_VALUE, charCodingConfig, transformation);
    }

    public CharSequenceAsyncEntityConsumer(final Function<CharSequence, T> transformation) {
        this(Integer.MAX_VALUE, transformation);
    }

    @Override
    protected final void streamStart(final ContentType contentType) throws HttpException, IOException {
    }

    @Override
    protected int capacityIncrement() {
        final int available = content.capacity() - content.length();
        return Math.max(capacityIncrement, available);
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
    public T generateContent() {
        return transformation.apply(content);
    }

    @Override
    public void releaseResources() {
        content.clear();
    }

}

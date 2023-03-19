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
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;

/**
 * {@link AsyncEntityProducer} implementation that generates a data stream from the content at a {@link Path}.
 *
 * @since 5.2
 */
public final class PathEntityProducer implements AsyncEntityProducer {

    private static final int BUFFER_SIZE = 8192;
    private final Path file;
    private final OpenOption[] openOptions;
    private final ByteBuffer byteBuffer;
    private final long length;
    private final ContentType contentType;
    private final boolean chunked;
    private final AtomicReference<Exception> exception;
    private final AtomicReference<SeekableByteChannel> channelRef;
    private boolean eof;

    public PathEntityProducer(final Path file, final ContentType contentType, final boolean chunked,
            final OpenOption... openOptions) throws IOException {
        this(file, BUFFER_SIZE, contentType, chunked, openOptions);
    }

    public PathEntityProducer(final Path file, final ContentType contentType, final OpenOption... openOptions)
            throws IOException {
        this(file, contentType, false, openOptions);
    }

    public PathEntityProducer(final Path file, final int bufferSize, final ContentType contentType,
            final boolean chunked, final OpenOption... openOptions) throws IOException {
        this.file = Args.notNull(file, "file");
        this.openOptions = openOptions;
        this.length = Files.size(file);
        this.byteBuffer = ByteBuffer.allocate(bufferSize);
        this.contentType = contentType;
        this.chunked = chunked;
        this.channelRef = new AtomicReference<>();
        this.exception = new AtomicReference<>();
    }

    public PathEntityProducer(final Path file, final OpenOption... openOptions) throws IOException {
        this(file, ContentType.APPLICATION_OCTET_STREAM, openOptions);
    }

    @Override
    public int available() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void failed(final Exception cause) {
        if (exception.compareAndSet(null, cause)) {
            releaseResources();
        }
    }

    @Override
    public String getContentEncoding() {
        return null;
    }

    @Override
    public long getContentLength() {
        return length;
    }

    @Override
    public String getContentType() {
        return contentType != null ? contentType.toString() : null;
    }

    public Exception getException() {
        return exception.get();
    }

    @Override
    public Set<String> getTrailerNames() {
        return Collections.emptySet();
    }

    @Override
    public boolean isChunked() {
        return chunked;
    }

    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Override
    public void produce(final DataStreamChannel dataStreamChannel) throws IOException {
        SeekableByteChannel seekableByteChannel = channelRef.get();
        if (seekableByteChannel == null) {
            seekableByteChannel = Files.newByteChannel(file, openOptions);
            Asserts.check(channelRef.getAndSet(seekableByteChannel) == null, "Illegal producer state");
        }
        if (!eof) {
            final int bytesRead = seekableByteChannel.read(byteBuffer);
            if (bytesRead < 0) {
                eof = true;
            }
        }
        if (byteBuffer.position() > 0) {
            byteBuffer.flip();
            dataStreamChannel.write(byteBuffer);
            byteBuffer.compact();
        }
        if (eof && byteBuffer.position() == 0) {
            dataStreamChannel.endStream();
            releaseResources();
        }
    }

    @Override
    public void releaseResources() {
        eof = false;
        Closer.closeQuietly(channelRef.getAndSet(null));
    }

}

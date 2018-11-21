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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.io.Closer;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;

/**
 * {@link AsyncEntityProducer} implementation that generates data stream
 * from content of a {@link File}.
 *
 * @since 5.0
 */
public final class FileEntityProducer implements AsyncEntityProducer {

    private final File file;
    private final ByteBuffer byteBuffer;
    private final long length;
    private final ContentType contentType;
    private final boolean chunked;
    private final AtomicReference<Exception> exception;
    private final AtomicReference<RandomAccessFile> accessFileRef;
    private boolean eof;

    public FileEntityProducer(final File file, final int bufferSize, final ContentType contentType, final boolean chunked) {
        this.file = Args.notNull(file, "File");
        this.length = file.length();
        this.byteBuffer = ByteBuffer.allocate(bufferSize);
        this.contentType = contentType;
        this.chunked = chunked;
        this.accessFileRef = new AtomicReference<>(null);
        this.exception = new AtomicReference<>(null);
    }

    public FileEntityProducer(final File file, final ContentType contentType, final boolean chunked) {
        this(file, 8192, contentType, chunked);
    }

    public FileEntityProducer(final File file, final ContentType contentType) {
        this(file, contentType, false);
    }

    public FileEntityProducer(final File file) {
        this(file, ContentType.APPLICATION_OCTET_STREAM);
    }

    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Override
    public String getContentType() {
        return contentType != null ? contentType.toString() : null;
    }

    @Override
    public long getContentLength() {
        return length;
    }

    @Override
    public int available() {
        return Integer.MAX_VALUE;
    }

    @Override
    public String getContentEncoding() {
        return null;
    }

    @Override
    public boolean isChunked() {
        return chunked;
    }

    @Override
    public Set<String> getTrailerNames() {
        return null;
    }

    @Override
    public void produce(final DataStreamChannel channel) throws IOException {
        @SuppressWarnings("resource")
        RandomAccessFile accessFile = accessFileRef.get();
        if (accessFile == null) {
            accessFile = new RandomAccessFile(file, "r");
            Asserts.check(accessFileRef.getAndSet(accessFile) == null, "Illegal producer state");
        }
        if (!eof) {
            final int bytesRead = accessFile.getChannel().read(byteBuffer);
            if (bytesRead < 0) {
                eof = true;
            }
        }
        if (byteBuffer.position() > 0) {
            byteBuffer.flip();
            channel.write(byteBuffer);
            byteBuffer.compact();
        }
        if (eof && byteBuffer.position() == 0) {
            channel.endStream();
            releaseResources();
        }
    }

    @Override
    public void failed(final Exception cause) {
        if (exception.compareAndSet(null, cause)) {
            releaseResources();
        }
    }

    public Exception getException() {
        return exception.get();
    }

    @Override
    public void releaseResources() {
        eof = false;
        Closer.closeQuietly(accessFileRef.getAndSet(null));
    }

}

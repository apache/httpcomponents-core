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
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Asserts;

/**
 * @since 5.0
 */
public final class FileEntityProducer implements AsyncEntityProducer {

    private final File file;
    private final ByteBuffer bytebuf;
    private final long length;
    private final ContentType contentType;
    private final AtomicReference<Exception> exception;

    private AtomicReference<RandomAccessFile> accessFileRef;
    private boolean eof;

    public FileEntityProducer(final File file, final int bufferSize, final ContentType contentType) {
        this.file = Args.notNull(file, "File");
        this.length = file.length();
        this.bytebuf = ByteBuffer.allocate((int)(bufferSize > this.length ? bufferSize : this.length));
        this.contentType = contentType;
        this.accessFileRef = new AtomicReference<>(null);
        this.exception = new AtomicReference<>(null);
    }

    public FileEntityProducer(final File file, final ContentType contentType) {
        this(file, 8192, contentType);
    }

    public FileEntityProducer(final File content) {
        this(content, ContentType.APPLICATION_OCTET_STREAM);
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
        return false;
    }

    @Override
    public Set<String> getTrailerNames() {
        return null;
    }

    @Override
    public void produce(final DataStreamChannel channel) throws IOException {
        RandomAccessFile accessFile = accessFileRef.get();
        if (accessFile == null) {
            accessFile = new RandomAccessFile(file, "r");
            Asserts.check(accessFileRef.getAndSet(accessFile) == null, "Illegal producer state");
        }
        if (!eof) {
            final int bytesRead = accessFile.getChannel().read(bytebuf);
            if (bytesRead < 0) {
                eof = true;
            }
        }
        if (bytebuf.position() > 0) {
            bytebuf.flip();
            channel.write(bytebuf);
            bytebuf.compact();
        }
        if (eof && bytebuf.position() == 0) {
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
        final RandomAccessFile accessFile = accessFileRef.getAndSet(null);
        if (accessFile != null) {
            try {
                accessFile.close();
            } catch (final IOException ignore) {
            }
        }
    }

}

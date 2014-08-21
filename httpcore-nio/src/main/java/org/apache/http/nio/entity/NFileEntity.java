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

package org.apache.http.nio.entity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.ContentEncoderChannel;
import org.apache.http.nio.FileContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.util.Args;

/**
 * A self contained, repeatable non-blocking entity that retrieves its content
 * from a file. This class is mostly used to stream large files of different
 * types, so one needs to supply the content type of the file to make sure
 * the content can be correctly recognized and processed by the recipient.
 *
 * @since 4.0
 */
@SuppressWarnings("deprecation")
@NotThreadSafe
public class NFileEntity extends AbstractHttpEntity
                         implements HttpAsyncContentProducer, ProducingNHttpEntity {

    private final File file;
    private RandomAccessFile accessfile;
    private FileChannel fileChannel;
    private long idx = -1;
    private boolean useFileChannels;

    /**
     * Creates new instance of NFileEntity from the given source {@link File}
     * with the given content type. If {@code useFileChannels} is set to
     * {@code true}, the entity will try to use {@link FileContentEncoder}
     * interface to stream file content directly from the file channel.
     *
     * @param file the source file.
     * @param contentType the content type of the file.
     * @param useFileChannels flag whether the direct transfer from the file
     *   channel should be attempted.
     *
     * @since 4.2
     */
    public NFileEntity(final File file, final ContentType contentType, final boolean useFileChannels) {
        Args.notNull(file, "File");
        this.file = file;
        this.useFileChannels = useFileChannels;
        if (contentType != null) {
            setContentType(contentType.toString());
        }
    }

    /**
     * @since 4.2
     */
    public NFileEntity(final File file) {
        Args.notNull(file, "File");
        this.file = file;
    }
    /**
     * Creates new instance of NFileEntity from the given source {@link File}
     * with the given content type.
     *
     * @param file the source file.
     * @param contentType the content type of the file.
     *
     * @since 4.2
     */
    public NFileEntity(final File file, final ContentType contentType) {
        this(file, contentType, true);
    }

    /**
     * @deprecated (4.2) use {@link #NFileEntity(File, ContentType, boolean)}
     */
    @Deprecated
    public NFileEntity(final File file, final String contentType, final boolean useFileChannels) {
        Args.notNull(file, "File");
        this.file = file;
        this.useFileChannels = useFileChannels;
        setContentType(contentType);
    }

    /**
     * @deprecated (4.2) use {@link #NFileEntity(File, ContentType)}
     */
    @Deprecated
    public NFileEntity(final File file, final String contentType) {
        this(file, contentType, true);
    }

    /**
     * {@inheritDoc}
     *
     * @since 4.2
     */
    @Override
    public void close() throws IOException {
        if (accessfile != null) {
            accessfile.close();
        }
        accessfile = null;
        fileChannel = null;
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated (4.2) use {@link #close()}
     */
    @Deprecated
    public void finish() throws IOException {
        close();
    }

    @Override
    public long getContentLength() {
        return file.length();
    }

    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Override
    public void produceContent(final ContentEncoder encoder, final IOControl ioctrl)
            throws IOException {
        if (accessfile == null) {
            accessfile = new RandomAccessFile(this.file, "r");
        }
        if (fileChannel == null) {
            fileChannel = accessfile.getChannel();
            idx = 0;
        }

        final long transferred;
        if (useFileChannels && encoder instanceof FileContentEncoder) {
            transferred = ((FileContentEncoder)encoder)
                .transfer(fileChannel, idx, Long.MAX_VALUE);
        } else {
            transferred = fileChannel.
                transferTo(idx, Long.MAX_VALUE, new ContentEncoderChannel(encoder));
        }
        if (transferred > 0) {
            idx += transferred;
        }
        if (idx >= fileChannel.size()) {
            encoder.complete();
            close();
        }
    }

    @Override
    public boolean isStreaming() {
        return false;
    }

    @Override
    public InputStream getContent() throws IOException {
        return new FileInputStream(this.file);
    }

    @Override
    public void writeTo(final OutputStream outstream) throws IOException {
        Args.notNull(outstream, "Output stream");
        final InputStream instream = new FileInputStream(this.file);
        try {
            final byte[] tmp = new byte[4096];
            int l;
            while ((l = instream.read(tmp)) != -1) {
                outstream.write(tmp, 0, l);
            }
            outstream.flush();
        } finally {
            instream.close();
        }
    }

}

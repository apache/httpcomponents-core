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
import java.nio.channels.FileChannel;

import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.ContentEncoderChannel;
import org.apache.http.nio.FileContentEncoder;
import org.apache.http.nio.IOControl;

/**
 * A self contained, repeatable non-blocking entity that retrieves its content
 * from a file. This class is mostly used to stream large files of different
 * types, so one needs to supply the content type of the file to make sure
 * the content can be correctly recognized and processed by the recipient.
 *
 * @since 4.0
 */
public class NFileEntity extends AbstractHttpEntity implements ProducingNHttpEntity {

    private final File file;
    private FileChannel fileChannel;
    private long idx = -1;
    private boolean useFileChannels;

    /**
     * Creates new instance of NFileEntity from the given source {@link File}
     * with the given content type. If <code>useFileChannels</code> is set to
     * <code>true</code>, the entity will try to use {@link FileContentEncoder}
     * interface to stream file content directly from the file channel.
     *
     * @param file the source file.
     * @param contentType the content type of the file.
     * @param useFileChannels flag whether the direct transfer from the file
     *   channel should be attempted.
     */
    public NFileEntity(final File file, final String contentType, boolean useFileChannels) {
        if (file == null) {
            throw new IllegalArgumentException("File may not be null");
        }
        this.file = file;
        this.useFileChannels = useFileChannels;
        setContentType(contentType);
    }

    public NFileEntity(final File file, final String contentType) {
        this(file, contentType, true);
    }

    public void finish() {
        try {
            if(fileChannel != null)
                fileChannel.close();
        } catch(IOException ignored) {}
        fileChannel = null;
    }

    public long getContentLength() {
        return file.length();
    }

    public boolean isRepeatable() {
        return true;
    }

    public void produceContent(ContentEncoder encoder, IOControl ioctrl)
            throws IOException {
        if(fileChannel == null) {
            FileInputStream in = new FileInputStream(file);
            fileChannel = in.getChannel();
            idx = 0;
        }

        long transferred;
        if(useFileChannels && encoder instanceof FileContentEncoder) {
            transferred = ((FileContentEncoder)encoder)
                .transfer(fileChannel, idx, Long.MAX_VALUE);
        } else {
            transferred = fileChannel.
                transferTo(idx, Long.MAX_VALUE, new ContentEncoderChannel(encoder));
        }

        if(transferred > 0)
            idx += transferred;

        if(idx >= fileChannel.size())
            encoder.complete();
    }

    public boolean isStreaming() {
        return false;
    }

    public InputStream getContent() throws IOException {
        return new FileInputStream(this.file);
    }

    public void writeTo(final OutputStream outstream) throws IOException {
        if (outstream == null) {
            throw new IllegalArgumentException("Output stream may not be null");
        }
        InputStream instream = new FileInputStream(this.file);
        try {
            byte[] tmp = new byte[4096];
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

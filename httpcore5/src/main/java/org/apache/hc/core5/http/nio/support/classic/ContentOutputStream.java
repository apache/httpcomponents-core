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

package org.apache.hc.core5.http.nio.support.classic;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * {@link OutputStream} adaptor for {@link ContentOutputBuffer}.
 *
 * @since 4.0
 */
public class ContentOutputStream extends OutputStream {

    private final ContentOutputBuffer buffer;

    public ContentOutputStream(final ContentOutputBuffer buffer) {
        super();
        Objects.requireNonNull(buffer, "Output buffer");
        this.buffer = buffer;
    }

    @Override
    public void close() throws IOException {
        this.buffer.writeCompleted();
    }

    @Override
    public void flush() throws IOException {
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        this.buffer.write(b, off, len);
    }

    @Override
    public void write(final byte[] b) throws IOException {
        if (b == null) {
            return;
        }
        this.buffer.write(b, 0, b.length);
    }

    @Override
    public void write(final int b) throws IOException {
        this.buffer.write(b);
    }

}

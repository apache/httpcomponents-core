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

package org.apache.hc.core5.http.io.entity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.util.Args;

/**
 * A streamed entity that obtains its content from a {@link Serializable}.
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class SerializableEntity extends AbstractHttpEntity {

    private final Serializable serializable;

    /**
     * Creates new instance of this class.
     *
     * @param serializable the serializable object.
     * @param contentType the content type.
     * @param contentEncoding the content encoding.
     */
    public SerializableEntity(
            final Serializable serializable, final ContentType contentType, final String contentEncoding) {
        super(contentType, contentEncoding);
        this.serializable = Args.notNull(serializable, "Source object");
    }

    /**
     * Creates new instance of this class.
     *
     * @param serializable the serializable object.
     * @param contentType the content type.
     */
    public SerializableEntity(final Serializable serializable, final ContentType contentType) {
        this(serializable, contentType, null);
    }

    @Override
    public final InputStream getContent() throws IOException, IllegalStateException {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        writeTo(buf);
        return new ByteArrayInputStream(buf.toByteArray());
    }

    @Override
    public final long getContentLength() {
        return -1;
    }

    @Override
    public final boolean isRepeatable() {
        return true;
    }

    @Override
    public final boolean isStreaming() {
        return false;
    }

    @Override
    public final void writeTo(final OutputStream outStream) throws IOException {
        Args.notNull(outStream, "Output stream");
        final ObjectOutputStream out = new ObjectOutputStream(outStream);
        out.writeObject(this.serializable);
        out.flush();
    }

    @Override
    public final void close() throws IOException {
    }

}

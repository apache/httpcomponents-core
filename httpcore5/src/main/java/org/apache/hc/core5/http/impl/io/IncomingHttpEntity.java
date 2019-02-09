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

package org.apache.hc.core5.http.impl.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.AbstractHttpEntity;
import org.apache.hc.core5.io.Closer;

class IncomingHttpEntity implements HttpEntity {

    private final InputStream content;
    private final long len;
    private final boolean chunked;
    private final Header contentType;
    private final Header contentEncoding;

    IncomingHttpEntity(final InputStream content, final long len, final boolean chunked, final Header contentType, final Header contentEncoding) {
        this.content = content;
        this.len = len;
        this.chunked = chunked;
        this.contentType = contentType;
        this.contentEncoding = contentEncoding;
    }

    @Override
    public boolean isRepeatable() {
        return false;
    }

    @Override
    public boolean isChunked() {
        return chunked;
    }

    @Override
    public long getContentLength() {
        return len;
    }

    @Override
    public String getContentType() {
        return contentType != null ? contentType.getValue() : null;
    }

    @Override
    public String getContentEncoding() {
        return contentEncoding != null ? contentEncoding.getValue() : null;
    }

    @Override
    public InputStream getContent() throws IOException, IllegalStateException {
        return content;
    }

    @Override
    public boolean isStreaming() {
        return content != null && content != EmptyInputStream.INSTANCE;
    }

    @Override
    public void writeTo(final OutputStream outStream) throws IOException {
        AbstractHttpEntity.writeTo(this, outStream);
    }

    @Override
    public Supplier<List<? extends Header>> getTrailers() {
        return null;
    }

    @Override
    public Set<String> getTrailerNames() {
        return Collections.emptySet();
    }

    @Override
    public void close() throws IOException {
        Closer.close(content);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append('[');
        sb.append("Content-Type: ");
        sb.append(getContentType());
        sb.append(',');
        sb.append("Content-Encoding: ");
        sb.append(getContentEncoding());
        sb.append(',');
        final long len = getContentLength();
        if (len >= 0) {
            sb.append("Content-Length: ");
            sb.append(len);
            sb.append(',');
        }
        sb.append("Chunked: ");
        sb.append(isChunked());
        sb.append(']');
        return sb.toString();
    }

}

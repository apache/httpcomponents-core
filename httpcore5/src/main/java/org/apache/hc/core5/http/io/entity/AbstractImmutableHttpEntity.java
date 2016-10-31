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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Args;

/**
 * Abstract base class for immutable entities.
 *
 * @since 5.0
 */
public abstract class AbstractImmutableHttpEntity implements HttpEntity, HttpContentProducer {

    static final int OUTPUT_BUFFER_SIZE = 4096;

    @Override
    public void writeTo(final OutputStream outstream) throws IOException {
        Args.notNull(outstream, "Output stream");
        final InputStream instream = getContent();
        if (instream != null) {
            try {
                int l;
                final byte[] tmp = new byte[OUTPUT_BUFFER_SIZE];
                while ((l = instream.read(tmp)) != -1) {
                    outstream.write(tmp, 0, l);
                }
            } finally {
                instream.close();
            }
        }
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

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

package org.apache.hc.core5.http2.impl.io;

import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Locale;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http2.hpack.HPackEncoder;
import org.apache.hc.core5.http2.io.Http2MessageWriter;
import org.apache.hc.core5.util.ByteArrayBuffer;

/**
 * Abstract base class for HTTP message writers that serialize output in HPACK format to a output buffer.
 *
 * @since 5.0
 */
public abstract class AbstractHttp2MessageWriter<T extends HttpMessage> implements Http2MessageWriter<T> {

    private final HPackEncoder encoder;

    public AbstractHttp2MessageWriter(final Charset charset) {
        this.encoder = new HPackEncoder(charset);
    }

    public int getMaxTableSize() {
        return this.encoder.getMaxTableSize();
    }

    public void setMaxTableSize(final int maxTableSize) {
        this.encoder.setMaxTableSize(maxTableSize);
    }

    protected HPackEncoder getEncoder() {
        return encoder;
    }

    @Override
    public final void write(final T message, final ByteArrayBuffer dst) throws HttpException {

        writePseudoHeaders(message, dst);
        for (final Iterator<Header> it = message.headerIterator(); it.hasNext(); ) {
            final Header header = it.next();
            final String name = header.getName();
            if (name.startsWith(":")) {
                throw new ProtocolException("Header name '" + name + "' is invalid");
            }
            if (name.equalsIgnoreCase(HttpHeaders.CONNECTION)) {
                throw new ProtocolException("Header '" + header.getName() + ": " + header.getValue() + "' is illegal for HTTP/2 messages");
            }
            this.encoder.encodeHeader(dst, header.getName().toLowerCase(Locale.ROOT), header.getValue(), header.isSensitive());
        }
    }

    protected abstract void writePseudoHeaders(T message, ByteArrayBuffer buffer) throws HttpException;

}

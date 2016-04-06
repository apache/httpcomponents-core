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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.core5.annotation.NotThreadSafe;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http2.hpack.HPackDecoder;
import org.apache.hc.core5.http2.io.Http2MessageParser;

/**
 * Abstract base class for HTTP message parsers that read input in HPACk format from an input buffer.
 *
 * @since 5.0
 */
@NotThreadSafe
public abstract class AbstractHttp2MessageParser<T extends HttpMessage> implements Http2MessageParser<T> {

    private final HPackDecoder decoder;

    public AbstractHttp2MessageParser(final Charset charset) {
        this.decoder = new HPackDecoder(charset);
    }

    public int getMaxTableSize() {
        return this.decoder.getMaxTableSize();
    }

    public void setMaxTableSize(final int maxTableSize) {
        this.decoder.setMaxTableSize(maxTableSize);
    }

    @Override
    public final T parse(final ByteBuffer src) throws HttpException {
        final List<Header> pseudoHeaders = new ArrayList<>();
        final List<Header> headers = new ArrayList<>();
        boolean pseudoHeaderCompleted = false;
        while (src.hasRemaining()) {
            final Header header = this.decoder.decodeHeader(src);
            if (header == null) {
                break;
            } else {
                final String name = header.getName();
                for (int i = 0; i < name.length(); i++) {
                    final char ch = name.charAt(i);
                    if (Character.isAlphabetic(ch) && !Character.isLowerCase(ch)) {
                        throw new ProtocolException("Header name '" + name + "' is invalid (header name contains uppercase characters)");
                    }
                }
                if (name.startsWith(":")) {
                    if (pseudoHeaderCompleted) {
                        throw new ProtocolException("Invalid sequence of headers (pseudo-headers must precede message headers)");
                    }
                    pseudoHeaders.add(header);
                } else {
                    pseudoHeaderCompleted = true;
                    if (name.equalsIgnoreCase(HttpHeaders.CONNECTION)) {
                        throw new ProtocolException("Header '" + header.getName() + ": " + header.getValue() + "' is illegal for HTTP/2 messages");
                    }
                    headers.add(header);
                }
            }
        }
        final T message = createMessage(pseudoHeaders);
        for (int i = 0; i < headers.size(); i++) {
            message.addHeader(headers.get(i));
        }
        return message;
    }

    protected abstract T createMessage(List<Header> pseudoHeaders) throws HttpException;

}

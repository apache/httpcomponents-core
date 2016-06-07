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

import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http2.H2PseudoRequestHeaders;
import org.apache.hc.core5.http2.hpack.HPackEncoder;
import org.apache.hc.core5.util.ByteArrayBuffer;
import org.apache.hc.core5.util.TextUtils;

/**
 * HTTP/2 request writer.
 *
 * @since 5.0
 */
public class Http2RequestWriter extends AbstractHttp2MessageWriter<HttpRequest> {

    public Http2RequestWriter(final Charset charset) {
        super(charset);
    }

    @Override
    protected void writePseudoHeaders(final HttpRequest message, final ByteArrayBuffer buffer) throws HttpException {
        if (TextUtils.isBlank(message.getMethod())) {
            throw new ProtocolException("Request method is empty");
        }
        final boolean optionMethod = "CONNECT".equalsIgnoreCase(message.getMethod());
        if (optionMethod) {
            if (TextUtils.isBlank(message.getAuthority())) {
                throw new ProtocolException("CONNECT request authority is not set");
            }
            if (message.getPath() != null) {
                throw new ProtocolException("CONNECT request path must be null");
            }
        } else {
            if (TextUtils.isBlank(message.getScheme())) {
                throw new ProtocolException("Request scheme is not set");
            }
            if (TextUtils.isBlank(message.getPath())) {
                throw new ProtocolException("Request path is not set");
            }
        }
        final HPackEncoder encoder = getEncoder();
        encoder.encodeHeader(buffer, H2PseudoRequestHeaders.METHOD, message.getMethod(), false);
        if (optionMethod) {
            encoder.encodeHeader(buffer, H2PseudoRequestHeaders.AUTHORITY, message.getAuthority(), false);
        }  else {
            encoder.encodeHeader(buffer, H2PseudoRequestHeaders.SCHEME, message.getScheme(), false);
            if (message.getAuthority() != null) {
                encoder.encodeHeader(buffer, H2PseudoRequestHeaders.AUTHORITY, message.getAuthority(), false);
            }
            encoder.encodeHeader(buffer, H2PseudoRequestHeaders.PATH, message.getPath(), false);
        }
    }

}

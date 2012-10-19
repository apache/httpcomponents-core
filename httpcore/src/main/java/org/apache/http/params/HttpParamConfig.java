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

package org.apache.http.params;

import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;

import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.MessageConstraints;
import org.apache.http.config.SocketConfig;

/**
 * @deprecated (4.3) provided for compatibility with {@link HttpParams}. Do not use.
 *
 * @since 4.3
 */
@Deprecated
public final class HttpParamConfig {

    private HttpParamConfig() {
    }

    public static SocketConfig getSocketConfig(final HttpParams params) {
        return SocketConfig.custom()
                .setSoTimeout(HttpConnectionParams.getSoTimeout(params))
                .setSoReuseAddress(HttpConnectionParams.getSoReuseaddr(params))
                .setSoKeepAlive(HttpConnectionParams.getSoKeepalive(params))
                .setSoLinger(HttpConnectionParams.getLinger(params))
                .setTcpNoDelay(HttpConnectionParams.getTcpNoDelay(params))
                .build();
    }

    public static MessageConstraints getMessageConstraints(final HttpParams params) {
        return MessageConstraints.custom()
                .setMaxHeaderCount(params.getIntParameter(CoreConnectionPNames.MAX_HEADER_COUNT, -1))
                .setMaxLineLength(params.getIntParameter(CoreConnectionPNames.MAX_LINE_LENGTH, -1))
                .build();
    }

    public static ConnectionConfig getConnectionConfig(final HttpParams params) {
        MessageConstraints messageConstraints = getMessageConstraints(params);
        String csname = HttpProtocolParams.getHttpElementCharset(params);
        return ConnectionConfig.custom()
                .setConnectTimeout(HttpConnectionParams.getConnectionTimeout(params))
                .setCharset(csname != null ? Charset.forName(csname) : null)
                .setMalformedInputAction((CodingErrorAction)
                        params.getParameter(CoreProtocolPNames.HTTP_MALFORMED_INPUT_ACTION))
                .setMalformedInputAction((CodingErrorAction)
                        params.getParameter(CoreProtocolPNames.HTTP_UNMAPPABLE_INPUT_ACTION))
                .setMessageConstraints(messageConstraints)
                .build();
    }

}

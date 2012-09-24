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

import java.nio.charset.CodingErrorAction;

import org.apache.http.HttpVersion;
import org.apache.http.util.Args;

/**
 * HTTP core configuration builder.
 * 
 * @since 4.3
 * 
 * @see CoreConnectionPNames
 * @see CoreProtocolPNames
 */
public class HttpCoreConfigBuilder {

    private Integer maxHeaderCount;
    private Integer maxLineLength;
    private Integer minChunkLimit;
    private Boolean socketKeepAlive;
    private Integer socketLinger;
    private Integer socketReuseAddress;
    private Integer socketTimeout;
    private Integer socketBufferSize;
    private Boolean tcpNoDelay;
    private Integer connectTimeout;
    private String contentCharset;
    private String httpElementCharset;
    private CodingErrorAction malformedInputAction;
    private CodingErrorAction unmappableInputAction;
    private String originServer;
    private HttpVersion protocolVersion;
    private Boolean useExpectContinue;
    private String userAgent;
    private Integer waitForContinue;

    public HttpCoreConfigBuilder() {
        super();
    }

    public Integer getConnectTimeout() {
        return connectTimeout;
    }

    public HttpCoreConfigBuilder setConnectTimeout(final Integer connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    public Integer getMaxHeaderCount() {
        return maxHeaderCount;
    }

    public HttpCoreConfigBuilder setMaxHeaderCount(final Integer maxHeaderCount) {
        this.maxHeaderCount = maxHeaderCount;
        return this;
    }

    public Integer getMaxLineLength() {
        return maxLineLength;
    }

    public HttpCoreConfigBuilder setMaxLineLength(final Integer maxLineLength) {
        this.maxLineLength = maxLineLength;
        return this;
    }

    public Integer getMinChunkLimit() {
        return minChunkLimit;
    }

    public HttpCoreConfigBuilder setMinChunkLimit(final Integer minChunkLimit) {
        this.minChunkLimit = minChunkLimit;
        return this;
    }

    public Boolean getSocketKeepAlive() {
        return socketKeepAlive;
    }

    public HttpCoreConfigBuilder setSocketKeepAlive(final Boolean socketKeepAlive) {
        this.socketKeepAlive = socketKeepAlive;
        return this;
    }

    public Integer getSocketReuseAddress() {
        return socketReuseAddress;
    }

    public HttpCoreConfigBuilder setSocketReuseAddress(final Integer socketReuseAddress) {
        this.socketReuseAddress = socketReuseAddress;
        return this;
    }

    public Integer getSocketLinger() {
        return socketLinger;
    }

    public HttpCoreConfigBuilder setSocketLinger(final Integer socketLinger) {
        this.socketLinger = socketLinger;
        return this;
    }

    public Integer getSocketTimeout() {
        return socketTimeout;
    }

    public HttpCoreConfigBuilder setSocketTimeout(final Integer socketTimeout) {
        this.socketTimeout = socketTimeout;
        return this;
    }

    public Integer getSocketBufferSize() {
        return socketBufferSize;
    }

    public HttpCoreConfigBuilder setSocketBufferSize(final Integer socketBufferSize) {
        this.socketBufferSize = socketBufferSize;
        return this;
    }

    public Boolean getTcpNoDelay() {
        return tcpNoDelay;
    }

    public HttpCoreConfigBuilder setTcpNoDelay(final Boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
        return this;
    }

    public String getContentCharset() {
        return contentCharset;
    }

    public HttpCoreConfigBuilder setContentCharset(final String contentCharset) {
        this.contentCharset = contentCharset;
        return this;
    }

    public String getHttpElementCharset() {
        return httpElementCharset;
    }

    public HttpCoreConfigBuilder setHttpElementCharset(final String httpElementCharset) {
        this.httpElementCharset = httpElementCharset;
        return this;
    }

    public CodingErrorAction getMalformedInputAction() {
        return malformedInputAction;
    }

    public HttpCoreConfigBuilder setMalformedInputAction(final CodingErrorAction malformedInputAction) {
        this.malformedInputAction = malformedInputAction;
        return this;
    }

    public CodingErrorAction getUnmappableInputAction() {
        return unmappableInputAction;
    }

    public HttpCoreConfigBuilder setUnmappableInputAction(final CodingErrorAction unmappableInputAction) {
        this.unmappableInputAction = unmappableInputAction;
        return this;
    }

    public String getOriginServer() {
        return originServer;
    }

    public HttpCoreConfigBuilder setOriginServer(final String originServer) {
        this.originServer = originServer;
        return this;
    }

    public HttpVersion getProtocolVersion() {
        return protocolVersion;
    }

    public HttpCoreConfigBuilder setProtocolVersion(final HttpVersion protocolVersion) {
        this.protocolVersion = protocolVersion;
        return this;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public HttpCoreConfigBuilder setUserAgent(final String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    public Boolean getUseExpectContinue() {
        return useExpectContinue;
    }

    public HttpCoreConfigBuilder setUseExpectContinue(final Boolean useExpectContinue) {
        this.useExpectContinue = useExpectContinue;
        return this;
    }
    
    public Integer getWaitForContinue() {
        return waitForContinue;
    }

    public HttpCoreConfigBuilder setWaitForContinue(final Integer waitForContinue) {
        this.waitForContinue = waitForContinue;
        return this;
    }

    public void populate(final HttpParams params) {
        Args.notNull(params, "HTTP parameters");
        if (connectTimeout != null) {
            params.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, connectTimeout);
        }
        if (maxHeaderCount != null) {
            params.setParameter(CoreConnectionPNames.MAX_HEADER_COUNT, maxHeaderCount);
        }
        if (maxLineLength != null) {
            params.setParameter(CoreConnectionPNames.MAX_LINE_LENGTH, maxLineLength);
        }
        if (minChunkLimit != null) {
            params.setParameter(CoreConnectionPNames.MIN_CHUNK_LIMIT, minChunkLimit);
        }
        if (socketKeepAlive != null) {
            params.setParameter(CoreConnectionPNames.SO_KEEPALIVE, socketKeepAlive);
        }
        if (socketLinger != null) {
            params.setParameter(CoreConnectionPNames.SO_LINGER, socketLinger);
        }
        if (socketReuseAddress != null) {
            params.setParameter(CoreConnectionPNames.SO_REUSEADDR, socketReuseAddress);
        }
        if (socketTimeout != null) {
            params.setParameter(CoreConnectionPNames.SO_TIMEOUT, socketTimeout);
        }
        if (socketBufferSize != null) {
            params.setParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, socketBufferSize);
        }
        if (tcpNoDelay != null) {
            params.setParameter(CoreConnectionPNames.TCP_NODELAY, tcpNoDelay);
        }
        if (contentCharset != null) {
            params.setParameter(CoreProtocolPNames.HTTP_CONTENT_CHARSET, contentCharset);
        }
        if (httpElementCharset != null) {
            params.setParameter(CoreProtocolPNames.HTTP_ELEMENT_CHARSET, httpElementCharset);
        }
        if (malformedInputAction != null) {
            params.setParameter(CoreProtocolPNames.HTTP_MALFORMED_INPUT_ACTION, malformedInputAction);
        }
        if (unmappableInputAction != null) {
            params.setParameter(CoreProtocolPNames.HTTP_UNMAPPABLE_INPUT_ACTION, unmappableInputAction);
        }
        if (originServer != null) {
            params.setParameter(CoreProtocolPNames.ORIGIN_SERVER, originServer);
        }
        if (protocolVersion != null) {
            params.setParameter(CoreProtocolPNames.PROTOCOL_VERSION, protocolVersion);
        }
        if (useExpectContinue != null) {
            params.setParameter(CoreProtocolPNames.USE_EXPECT_CONTINUE, useExpectContinue);
        }
        if (userAgent != null) {
            params.setParameter(CoreProtocolPNames.USER_AGENT, userAgent);
        }
        if (waitForContinue != null) {
            params.setParameter(CoreProtocolPNames.WAIT_FOR_CONTINUE, waitForContinue);
        }
    }

    public HttpParams build() {
        HttpParams params = new BasicHttpParams();
        populate(params);
        return params;
    }
    
}

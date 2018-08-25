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
package org.apache.hc.core5.http2;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Errors codes defined by HTTP/2 specification.
 *
 * @since 5.0
 */
public enum H2Error {

    /**
     * Graceful shutdown
     * <p>
     * The associated condition is not as a result of an error.
     */
    NO_ERROR (0x00),

    /**
     * Protocol error detected
     * <p>
     * The endpoint detected an unspecific protocol error. This error is for use when a more
     * specific error code is not available
     */
    PROTOCOL_ERROR (0x01),

    /**
     * Implementation fault
     * <p>
     * The endpoint encountered an unexpected internal error.
     */
    INTERNAL_ERROR (0x02),

    /**
     * Flow control limits exceeded.
     * <p>
     * The endpoint detected that its peer violated the flow control protocol.
     */
    FLOW_CONTROL_ERROR (0x03),

    /**
     * Settings not acknowledged.
     * <p>
     * The endpoint sent a SETTINGS frame, but did not receive a response in a timely manner.
     */
    SETTINGS_TIMEOUT (0x04),

    /**
     * Frame received for closed stream.
     * <p>
     * The endpoint received a frame after a stream was half closed.
     */
    STREAM_CLOSED (0x05),

    /**
     * Frame size incorrect.
     * <p>
     * The endpoint received a frame with an invalid size.
     */
    FRAME_SIZE_ERROR (0x06),

    /**
     * Stream not processed
     * <p>
     * The endpoint refuses the stream prior to performing any application processing.
     */
    REFUSED_STREAM (0x07),

    /**
     * Stream canceled.
     * <p>
     * Used by the endpoint to indicate that the stream is no longer needed
     */
    CANCEL (0x08),

    /**
     * Compression state not updated.
     * <p>
     * The endpoint is unable to maintain the header compression context for the connection.
     */
    COMPRESSION_ERROR (0x09),

    /**
     * TCP connection error.
     * <p>
     * The connection established in response to a CONNECT request was reset or abnormally closed.
     */
    CONNECT_ERROR (0x0a),

    /**
     * Processing capacity exceeded.
     * <p>
     * The endpoint detected that its peer is exhibiting a behavior that might be generating
     * excessive load.
     */
    ENHANCE_YOUR_CALM (0x0b),

    /**
     * Negotiated TLS parameters not acceptable.
     * <p>
     * The underlying transport has properties that do not meet minimum security requirements.
     */
    INADEQUATE_SECURITY (0x0c),

    /**
     * Use HTTP/1.1 for the request.
     * <p>
     * The endpoint requires that HTTP/1.1 be used instead of HTTP/2.
     */
    HTTP_1_1_REQUIRED (0x0d);

    int code;

    H2Error(final int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    private static final ConcurrentMap<Integer, H2Error> MAP_BY_CODE;
    static {
        MAP_BY_CODE = new ConcurrentHashMap<>();
        for (final H2Error error: values()) {
            MAP_BY_CODE.putIfAbsent(error.code, error);
        }
    }

    public static H2Error getByCode(final int code) {
        return MAP_BY_CODE.get(code);
    }

}

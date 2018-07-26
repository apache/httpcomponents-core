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

package org.apache.hc.core5.http;

/**
 * HTTP messages consist of requests from client to server and responses
 * from server to client.
 *
 * @since 4.0
 */
public interface HttpMessage extends MessageHeaders {

    /**
     * Sets protocol version.
     * <p>
     * For incoming messages it represents protocol version this message was transmitted with.
     * For outgoing messages it represents a hint what protocol version should be used to transmit
     * the message.
     *
     * @since 5.0
     */
    void setVersion(ProtocolVersion version);

    /**
     * Returns protocol version or {@code null} when not available.
     * <p>
     * For incoming messages it represents protocol version this message was transmitted with.
     * For outgoing messages it represents a hint what protocol version should be used to transmit
     * the message.
     *
     * @since 5.0
     */
    ProtocolVersion getVersion();

    /**
     * Adds a header to this message. The header will be appended to the end of
     * the list.
     *
     * @param header the header to append.
     */
    void addHeader(Header header);

    /**
     * Adds a header to this message. The header will be appended to the end of
     * the list.
     *
     * @param name the name of the header.
     * @param value the value of the header, taken as the value's {@link Object#toString()}.
     */
    void addHeader(String name, Object value);

    /**
     * Overwrites the first header with the same name. The new header will be appended to
     * the end of the list, if no header with the given name can be found.
     *
     * @param header the header to set.
     */
    void setHeader(Header header);

    /**
     * Overwrites the first header with the same name. The new header will be appended to
     * the end of the list, if no header with the given name can be found.
     *
     * @param name the name of the header.
     * @param value the value of the header, taken as the value's {@link Object#toString()}.
     */
    void setHeader(String name, Object value);

    /**
     * Overwrites all the headers in the message.
     *
     * @param headers the array of headers to set.
     */
    void setHeaders(Header... headers);

    /**
     * Removes a header from this message.
     *
     * @param header the header to remove.
     * @return <code>true</code> if a header was removed as a result of this call.
     */
    boolean removeHeader(Header header);

    /**
     * Removes all headers with a certain name from this message.
     *
     * @param name The name of the headers to remove.
     * @return <code>true</code> if any header was removed as a result of this call.
     */
    boolean removeHeaders(String name);

}

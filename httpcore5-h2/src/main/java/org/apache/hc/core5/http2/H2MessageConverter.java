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

import java.util.List;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpMessage;

/**
 * Abstract message converter intended to convert from a list of HTTP/2 headers to object
 * representing an HTTP message and from an object representing an HTTP message to a list
 * of HTTP/2 headers.
 *
 * @param <T> represents {@link HttpMessage}
 *
 * @since 5.0
 */
public interface H2MessageConverter<T extends HttpMessage> {

    /**
     * Converts given list of HTTP/2 headers to a {@link HttpMessage}.
     *
     * @param headers list of HTTP/2 headers
     * @return HTTP message
     * @throws HttpException in case of HTTP protocol violation
     */
    T convert(List<Header> headers) throws HttpException;

    /**
     * Converts given {@link HttpMessage} to a list of HTTP/2 headers.
     *
     * @param message HTTP message
     * @return list of HTTP/2 headers
     * @throws HttpException in case of HTTP protocol violation
     */
    List<Header> convert(T message) throws HttpException;

}

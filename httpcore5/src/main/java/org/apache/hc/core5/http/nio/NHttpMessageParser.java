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

package org.apache.hc.core5.http.nio;

import java.io.IOException;

import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.MessageHeaders;

/**
 * Message parser intended to build HTTP message head from a session input buffer..
 *
 * @since 4.0
 */
public interface NHttpMessageParser<T extends MessageHeaders> {

    /**
     * Resets the parser. The parser will be ready to start parsing another
     * HTTP message.
     */
    void reset();

    /**
     * Attempts to parse a complete message head from the content of the
     * internal buffer. If the message in the input buffer is incomplete
     * this method will return {@code null}.
     *
     * @param buffer session input buffer.
     * @param endOfStream end of stream flag
     * @return HTTP message head, if available, {@code null} otherwise.
     * @throws IOException in case of an I/O error.
     * @throws HttpException in case the HTTP message is malformed or
     *  violates the HTTP protocol.
     */
    T parse(SessionInputBuffer buffer, boolean endOfStream) throws IOException, HttpException;

}

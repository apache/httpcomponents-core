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

package org.apache.hc.core5.http.message;

import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Interface for parsing lines in the HEAD section of an HTTP message.
 * There are individual methods for parsing a request line, a
 * status line, or a header line.
 * Instances of this interface are expected to be stateless and thread-safe.
 *
 * @since 4.0
 */
public interface LineParser {

    /**
     * Parses a request line from the given buffer containing one line of text.
     *
     * @param buffer    a buffer holding a line to parse
     *
     * @return  the parsed request line
     *
     * @throws ParseException        in case of a parse error
     */
    RequestLine parseRequestLine(CharArrayBuffer buffer) throws ParseException;

    /**
     * Parses a status line from the given buffer containing one line of text.
     *
     * @param buffer    a buffer holding a line to parse
     *
     * @return  the parsed status line
     *
     * @throws ParseException        in case of a parse error
     */
    StatusLine parseStatusLine(CharArrayBuffer buffer) throws ParseException;

    /**
     * Parses a header from the given buffer containing one line of text.
     * The full header line is expected here. Header continuation
     * lines must be joined by the caller before invoking this method.
     *
     * @param buffer    a buffer holding the full header line.
     *
     * @return  the header in the argument buffer.
     *
     * @throws ParseException        in case of a parse error
     */
    Header parseHeader(CharArrayBuffer buffer) throws ParseException;

}

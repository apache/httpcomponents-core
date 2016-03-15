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
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Interface for formatting elements of the HEAD section of an HTTP message.
 * There are individual methods for formatting a request line, a
 * status line, or a header line. The formatting methods are expected to produce
 * one line of formatted content that does <i>not</i> include a line delimiter
 * (such as CR-LF). Instances of this interface are expected to be stateless and
 * thread-safe.
 *
 * @since 4.0
 */
public interface LineFormatter {

    /**
     * Formats a request line.
     *
     * @param buffer    buffer to write formatted content to.
     * @param reqline   the request line to format
     */
    void formatRequestLine(CharArrayBuffer buffer, RequestLine reqline);

    /**
     * Formats a status line.
     *
     * @param buffer    buffer to write formatted content to.
     * @param statline  the status line to format
     */
    void formatStatusLine(CharArrayBuffer buffer, StatusLine statline);

    /**
     * Formats a header.
     *
     * @param buffer    buffer to write formatted content to.
     * @param header    the header to format
     */
    void formatHeader(CharArrayBuffer buffer, Header header);

}

/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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

package org.apache.http.message;


import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.Header;
import org.apache.http.util.CharArrayBuffer;


/**
 * Interface for parsing lines in the HEAD section of an HTTP message.
 * There are individual methods for parsing a request line, a
 * status line, or a header line.
 * The lines to parse are passed in memory, the parser does not depend
 * on any specific IO mechanism.
 * Instances of this interface are expected to be stateless and thread-safe.
 *
 *
 * <!-- empty lines above to avoid 'svn diff' context problems -->
 * @version $Revision$ $Date$
 *
 * @since 4.0
 */
public interface LineParser {


    /**
     * Parses the textual representation of a protocol version.
     * This is needed for parsing request lines (last element)
     * as well as status lines (first element).
     *
     * @param buffer    a buffer holding the protocol version to parse
     * 
     * @return  the parsed protocol version
     *
     * @throws ProtocolException        in case of a parse error
     */
    public HttpVersion parseProtocolVersion(final CharArrayBuffer buffer,
                                            final int indexFrom,
                                            final int indexTo) 
        throws ProtocolException
        ;


    /**
     * Parses a request line.
     *
     * @param buffer    a buffer holding the line to parse
     *
     * @return  the parsed request line
     *
     * @throws ProtocolException        in case of a parse error
     */
    public RequestLine parseRequestLine(final CharArrayBuffer buffer,
                                        final int indexFrom,
                                        final int indexTo)
        throws ProtocolException
        ;


    /**
     * Parses a status line.
     *
     * @param buffer    a buffer holding the line to parse
     *
     * @return  the parsed status line
     *
     * @throws ProtocolException        in case of a parse error
     */
    public StatusLine parseStatusLine(final CharArrayBuffer buffer,
                                      final int indexFrom,
                                      final int indexTo) 
        throws ProtocolException
        ;


    /**
     * Creates a header from a line.
     * The full header line is expected here. Header continuation lines
     * must be joined by the caller before invoking this method.
     *
     * @param buffer    a buffer holding the full header line.
     *                  This buffer MUST NOT be re-used afterwards, since
     *                  the returned object may reference the contents later.
     *
     * @return  the header in the argument buffer.
     *          The returned object MAY be a wrapper for the argument buffer.
     *          The argument buffer MUST NOT be re-used or changed afterwards.
     *
     * @throws ProtocolException        in case of a parse error
     */
    public Header parseHeader(CharArrayBuffer buffer)
        throws ProtocolException
        ;


}

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

import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.NameValuePair;

/**
 * Interface for parsing header values into elements.
 * Instances of this interface are expected to be stateless and thread-safe.
 *
 * @since 4.0
 */
public interface HeaderValueParser {

    /**
     * Parses a header value into elements.
     * Parse errors are indicated as {@code RuntimeException}.
     *
     * @param buffer    buffer holding the header value to parse
     * @param cursor    the parser cursor containing the current position and
     *                  the bounds within the buffer for the parsing operation
     *
     * @return  an array holding all elements of the header value
     */
    HeaderElement[] parseElements(CharSequence buffer, ParserCursor cursor);

    /**
     * Parses a single header element.
     * A header element consist of a semicolon-separate list
     * of name=value definitions.
     *
     * @param buffer    buffer holding the element to parse
     * @param cursor    the parser cursor containing the current position and
     *                  the bounds within the buffer for the parsing operation
     *
     * @return  the parsed element
     */
    HeaderElement parseHeaderElement(CharSequence buffer, ParserCursor cursor);

    /**
     * Parses a list of name-value pairs.
     * These lists are used to specify parameters to a header element.
     * Parse errors are indicated as {@code ParseException}.
     *
     * @param buffer    buffer holding the name-value list to parse
     * @param cursor    the parser cursor containing the current position and
     *                  the bounds within the buffer for the parsing operation
     *
     * @return  an array holding all items of the name-value list
     */
    NameValuePair[] parseParameters(CharSequence buffer, ParserCursor cursor);


    /**
     * Parses a name=value specification, where the = and value are optional.
     *
     * @param buffer    the buffer holding the name-value pair to parse
     * @param cursor    the parser cursor containing the current position and
     *                  the bounds within the buffer for the parsing operation
     *
     * @return  the name-value pair, where the value is {@code null}
     *          if no value is specified
     */
    NameValuePair parseNameValuePair(CharSequence buffer, ParserCursor cursor);

}


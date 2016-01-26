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
import org.apache.hc.core5.util.CharArrayBuffer;

/**
 * Interface for formatting elements of a header value.
 * Instances of this interface are expected to be stateless and thread-safe.
 *
 * @since 4.0
 */
public interface HeaderValueFormatter {

    /**
     * Formats an array of header elements.
     *
     * @param buffer    buffer to write formatted content to.
     * @param elems     the header elements to format
     * @param quote     {@code true} to always format with quoted values,
     *                  {@code false} to use quotes only when necessary
     */
    void formatElements(CharArrayBuffer buffer, HeaderElement[] elems, boolean quote);

    /**
     * Formats one header element.
     *
     * @param buffer    buffer to write formatted content to.
     * @param elem      the header element to format
     * @param quote     {@code true} to always format with quoted values,
     *                  {@code false} to use quotes only when necessary
     */
    void formatHeaderElement(CharArrayBuffer buffer, HeaderElement elem, boolean quote);

    /**
     * Formats the parameters of a header element.
     * That's a list of name-value pairs, to be separated by semicolons.
     * This method will <i>not</i> generate a leading semicolon.
     *
     * @param buffer    buffer to write formatted content to.
     * @param nvps      the parameters (name-value pairs) to format
     * @param quote     {@code true} to always format with quoted values,
     *                  {@code false} to use quotes only when necessary
     */
    void formatParameters(CharArrayBuffer buffer, NameValuePair[] nvps, boolean quote);

    /**
     * Formats one name-value pair, where the value is optional.
     *
     * @param buffer    buffer to write formatted content to.
     * @param nvp       the name-value pair to format
     * @param quote     {@code true} to always format with a quoted value,
     *                  {@code false} to use quotes only when necessary
     */
    void formatNameValuePair(CharArrayBuffer buffer, NameValuePair nvp, boolean quote);

}


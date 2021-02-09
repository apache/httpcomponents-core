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

import java.util.Iterator;

/**
 * Messages head consisting of multiple message headers.
 *
 * @since 5.0
 */
public interface MessageHeaders {

    /**
     * Checks if a certain header is present in this message. Header values are
     * ignored.
     *
     * @param name the header name to check for.
     * @return true if at least one header with this name is present.
     */
    boolean containsHeader(String name);

    /**
     * Checks if a certain header is present in this message and how many times.
     *
     * @param name the header name to check for.
     * @return number of occurrences of the header in the message.
     */
    int countHeaders(String name);

    /**
     * Returns the first header with a specified name of this message. Header
     * values are ignored. If there is more than one matching header in the
     * message the first element of {@link #getHeaders(String)} is returned.
     * If there is no matching header in the message {@code null} is
     * returned.
     *
     * @param name the name of the header to return.
     * @return the first header whose name property equals {@code name}
     *   or {@code null} if no such header could be found.
     */
    Header getFirstHeader(String name);

    /**
     * Gets single first header with the given name.
     *
     * <p>Header name comparison is case insensitive.
     *
     * @param name the name of the header to get
     * @return the first header or {@code null}
     * @throws ProtocolException in case multiple headers with the given name are found.
     */
    Header getHeader(String name) throws ProtocolException;

    /**
     * Returns all the headers of this message. Headers are ordered in the sequence
     * they will be sent over a connection.
     *
     * @return all the headers of this message
     */
    Header[] getHeaders();

    /**
     * Returns all the headers with a specified name of this message. Header values
     * are ignored. Headers are ordered in the sequence they will be sent over a
     * connection.
     *
     * @param name the name of the headers to return.
     * @return the headers whose name property equals {@code name}.
     */
    Header[] getHeaders(String name);

    /**
     * Returns the last header with a specified name of this message. Header values
     * are ignored. If there is more than one matching header in the message the
     * last element of {@link #getHeaders(String)} is returned. If there is no
     * matching header in the message {@code null} is returned.
     *
     * @param name the name of the header to return.
     * @return the last header whose name property equals {@code name}.
     *   or {@code null} if no such header could be found.
     */
    Header getLastHeader(String name);

    /**
     * Returns an iterator of all the headers.
     *
     * @return Iterator that returns Header objects in the sequence they are
     *         sent over a connection.
     */
    Iterator<Header> headerIterator();

    /**
     * Returns an iterator of the headers with a given name.
     *
     * @param name      the name of the headers over which to iterate, or
     *                  {@code null} for all headers
     *
     * @return Iterator that returns Header objects with the argument name
     *         in the sequence they are sent over a connection.
     */
    Iterator<Header> headerIterator(String name);

}

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

package org.apache.hc.core5.http.io;

import java.io.IOException;
import java.io.InputStream;

import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.MessageHeaders;

/**
 * Message parser intended to build HTTP message head from an input stream.
 *
 * @param <T>
 *            {@link MessageHeaders} or a subclass
 *
 * @since 4.0
 */
public interface HttpMessageParser<T extends MessageHeaders> {

    /**
     * Generates an instance of {@link MessageHeaders} from the given input stream..
     *
     * @param buffer Session input buffer
     * @param inputStream Input stream
     * @return HTTP message head or {@code null} if the input stream has been
     * closed by the opposite endpoint.
     * @throws IOException in case of an I/O error
     * @throws HttpException in case of HTTP protocol violation
     */
    T parse(SessionInputBuffer buffer, InputStream inputStream) throws IOException, HttpException;

}

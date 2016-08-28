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

import java.util.Locale;

/**
 * After receiving and interpreting a request message, a server responds
 * with an HTTP response message.
 *
 * @since 4.0
 */
public interface HttpResponse extends HttpMessage {

    /**
     * Obtains the code of this response message.
     *
     * @return  the status code.
     */
    int getCode();

    /**
     * Updates status code of this response message.
     *
     * @param code the HTTP status code.
     *
     * @see HttpStatus
     */
    void setCode(int code);

    /**
     * Obtains the reason phrase of this response if available.
     *
     * @return  the reason phrase.
     */
    String getReasonPhrase();

    /**
     * Updates the status line of this response with a new reason phrase.
     *
     * @param reason    the new reason phrase as a single-line string, or
     *                  {@code null} to unset the reason phrase
     */
    void setReasonPhrase(String reason);

    /**
     * Obtains the locale of this response.
     * The locale is used to determine the reason phrase
     * for the {@link #setCode status code}.
     * It can be changed using {@link #setLocale setLocale}.
     *
     * @return  the locale of this response, never {@code null}
     */
    Locale getLocale();

    /**
     * Changes the locale of this response.
     *
     * @param loc       the new locale
     */
    void setLocale(Locale loc);

}

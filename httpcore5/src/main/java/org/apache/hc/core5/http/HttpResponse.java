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
public interface HttpResponse extends HttpMessage<HttpEntity> {

    /**
     * Obtains the code in the status line of this response.
     * The status line can be set using one of the
     * {@link #setStatusLine setStatusLine} methods,
     * or it can be initialized in a constructor.
     *
     * @return  the status code, or {@code 0} if not yet set
     */
    int getCode();

    /**
     * Obtains the status line of this response.
     * The status line can be set using one of the
     * {@link #setStatusLine setStatusLine} methods,
     * or it can be initialized in a constructor.
     *
     * @return  the status line, or {@code null} if not yet set
     */
    StatusLine getStatusLine();

    /**
     * Sets the status line of this response.
     *
     * @param statusline the status line of this response
     */
    void setStatusLine(StatusLine statusline);

    /**
     * Sets the status line of this response.
     * The reason phrase will be determined based on the current
     * {@link #getLocale locale}.
     *
     * @param ver       the HTTP version
     * @param code      the status code
     */
    void setStatusLine(ProtocolVersion ver, int code);

    /**
     * Sets the status line of this response with a reason phrase.
     *
     * @param ver       the HTTP version
     * @param code      the status code
     * @param reason    the reason phrase, or {@code null} to omit
     */
    void setStatusLine(ProtocolVersion ver, int code, String reason);

    /**
     * Updates the status line of this response with a new status code.
     *
     * @param code the HTTP status code.
     *
     * @see HttpStatus
     * @see #setStatusLine(StatusLine)
     * @see #setStatusLine(ProtocolVersion,int)
     */
    void setStatusCode(int code);

    /**
     * Updates the status line of this response with a new reason phrase.
     *
     * @param reason    the new reason phrase as a single-line string, or
     *                  {@code null} to unset the reason phrase
     * @see #setStatusLine(StatusLine)
     * @see #setStatusLine(ProtocolVersion,int)
     */
    void setReasonPhrase(String reason);

    /**
     * Obtains the locale of this response.
     * The locale is used to determine the reason phrase
     * for the {@link #setStatusCode status code}.
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

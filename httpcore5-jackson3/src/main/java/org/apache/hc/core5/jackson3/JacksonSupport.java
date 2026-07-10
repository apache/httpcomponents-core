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
package org.apache.hc.core5.jackson3;

import java.io.IOException;

import org.apache.hc.core5.annotation.Internal;

import tools.jackson.core.JacksonException;

/**
 * Translates Jackson exceptions into {@link IOException} at the boundary between Jackson
 * and the HttpCore I/O contracts. Jackson 3 reports stream and databind failures as
 * unchecked {@link JacksonException}, whereas HttpCore producers and consumers declare
 * {@link IOException}. Every Jackson call made behind such a contract must be converted
 * here rather than allowed to escape as a runtime exception.
 *
 * @since 5.5
 */
@Internal
public final class JacksonSupport {

    private JacksonSupport() {
    }

    /**
     * Wraps a Jackson exception as an {@link IOException}, preserving it as the cause.
     *
     * @param ex the Jackson exception to translate
     * @return an {@link IOException} carrying the same message and the given cause
     * @since 5.5
     */
    public static IOException asIOException(final JacksonException ex) {
        return new IOException(ex.getMessage(), ex);
    }

}

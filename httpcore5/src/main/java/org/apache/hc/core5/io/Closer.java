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

package org.apache.hc.core5.io;

import java.io.Closeable;
import java.io.IOException;

/**
 * Closes resources.
 *
 * @since 5.0
 */
public final class Closer {

    /**
     * Closes the given Closeable in a null-safe manner.
     *
     * @param closeable what to close.
     * @throws IOException
     */
    public static void close(final Closeable closeable) throws IOException {
        if (closeable != null) {
            closeable.close();
        }
    }

    /**
     * Closes the given Closeable in a null-safe manner.
     *
     * @param closeable what to close.
     * @param closeMode How to close the given resource.
     */
    public static void close(final ModalCloseable closeable, final CloseMode closeMode) {
        if (closeable != null) {
            closeable.close(closeMode);
        }
    }

    /**
     * Closes the given Closeable quietly in a null-safe manner even in the event of an exception.
     *
     * @param closeable what to close.
     */
    public static void closeQuietly(final Closeable closeable) {
        try {
            close(closeable);
        } catch (final IOException e) {
            // Quietly ignore
        }
    }
}

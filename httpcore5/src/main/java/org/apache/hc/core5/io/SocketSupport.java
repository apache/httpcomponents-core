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

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.SocketOption;

import org.apache.hc.core5.annotation.Internal;

/**
 * @since 5.3
 */
@Internal
public class SocketSupport {

    public static final String TCP_KEEPIDLE = "TCP_KEEPIDLE";
    public static final String TCP_KEEPINTERVAL = "TCP_KEEPINTERVAL";
    public static final String TCP_KEEPCOUNT = "TCP_KEEPCOUNT";

    @SuppressWarnings("unchecked")
    public static <T> SocketOption<T> getExtendedSocketOptionOrNull(final String fieldName) {
        try {
            final Class<?> extendedSocketOptionsClass = Class.forName("jdk.net.ExtendedSocketOptions");
            final Field field = extendedSocketOptionsClass.getField(fieldName);
            return (SocketOption<T>) field.get(null);
        } catch (final Exception ignore) {
            return null;
        }
    }

    /**
     * Object can be ServerSocket or Socket.
     *
     * @param <T> ServerSocket or Socket.
     * @throws IOException in case of an I/O error.
     */
    public static <T> void setOption(final T object, final String fieldName, final T value) throws IOException {
        try {
            final Class<?> serverSocketClass = object.getClass();
            final Method setOptionMethod = serverSocketClass.getMethod("setOption", SocketOption.class, Object.class);
            final SocketOption<Integer> socketOption = getExtendedSocketOptionOrNull(fieldName);
            if (socketOption == null) {
                throw new UnsupportedOperationException("Extended socket option not supported: " + fieldName);
            }
            setOptionMethod.invoke(object, socketOption, value);
        } catch (final UnsupportedOperationException e) {
            throw e;
        } catch (final Exception ex) {
            throw new IOException("Failure setting extended socket option", ex);
        }
    }

}

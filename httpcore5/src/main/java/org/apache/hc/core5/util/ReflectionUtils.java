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

package org.apache.hc.core5.util;

import jdk.net.ExtendedSocketOptions;
import jdk.net.Sockets;
import org.apache.hc.core5.annotation.Internal;

import java.lang.reflect.Method;
import java.net.Socket;
import java.util.Arrays;

@Internal
@SuppressWarnings("Since15")
public final class ReflectionUtils {
    private static final boolean SUPPORTS_KEEPALIVE_OPTIONS = Sockets.supportedOptions(Socket.class)
            .containsAll(Arrays.asList(ExtendedSocketOptions.TCP_KEEPIDLE, ExtendedSocketOptions.TCP_KEEPINTERVAL,
                    ExtendedSocketOptions.TCP_KEEPCOUNT));

    public static void callSetter(final Object object, final String setterName, final Class<?> type, final Object value) {
        try {
            final Class<?> clazz = object.getClass();
            final Method method = clazz.getMethod("set" + setterName, type);
            method.setAccessible(true);
            method.invoke(object, value);
        } catch (final Exception ignore) {
        }
    }

    public static <T> T callGetter(final Object object, final String getterName, final Class<T> resultType) {
        return callGetter(object, getterName, null, null, resultType);
    }

    /**
     * @param <T> The return type.
     * @since 5.3
     */
    public static <T> T callGetter(final Object object, final String getterName, final Object arg, final Class<?> argType, final Class<T> resultType) {
        try {
            final Class<?> clazz = object.getClass();
            final Method method;
            if (arg != null) {
                assert argType != null;
                method = clazz.getMethod("get" + getterName, argType);
                method.setAccessible(true);
                return resultType.cast(method.invoke(object, arg));
            } else {
                assert argType == null;
                method = clazz.getMethod("get" + getterName);
                method.setAccessible(true);
                return resultType.cast(method.invoke(object));
            }
        } catch (final Exception ignore) {
            return null;
        }
    }

    public static int determineJRELevel() {
        final String s = System.getProperty("java.version");
        final String[] parts = s.split("\\.");
        if (parts.length > 0) {
            try {
                final int majorVersion = Integer.parseInt(parts[0]);
                if (majorVersion > 1) {
                    return majorVersion;
                } else if (majorVersion == 1 && parts.length > 1) {
                    return Integer.parseInt(parts[1]);
                }
            } catch (final NumberFormatException ignore) {
            }
        }
        return 7;
    }

    public static boolean supportsKeepAliveOptions() {
        return SUPPORTS_KEEPALIVE_OPTIONS;
    }
}

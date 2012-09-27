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

package org.apache.http.params;

/**
 * @since 4.3
 */
public final class Config {

    private Config() {
    }

    public static Object getValue(final HttpParams params, final String name) {
        if (params == null) {
            return null;
        }
        return params.getParameter(name);
    }

    public static <T> T getValue(final HttpParams params, final String name, final Class<T> clazz) {
        Object param = getValue(params, name);
        if (param == null) {
            return null;
        }
        return clazz.cast(param);
    }

    public static String getString(final HttpParams params, final String name) {
        return getValue(params, name, String.class);
    }

    public static long getLong(final HttpParams params, final String name, long def) {
        Long param = getValue(params, name, Long.class);
        if (param == null) {
            return def;
        }
        return param.longValue();
    }

    public static int getInt(final HttpParams params, final String name, int def) {
        Integer param = getValue(params, name, Integer.class);
        if (param == null) {
            return def;
        }
        return param.intValue();
    }

    public static double getDouble(final HttpParams params, final String name, double def) {
        Double param = getValue(params, name, Double.class);
        if (param == null) {
            return def;
        }
        return param.doubleValue();
    }

    public static boolean getBool(final HttpParams params, final String name, boolean def) {
        Boolean param = getValue(params, name, Boolean.class);
        if (param == null) {
            return def;
        }
        return param.booleanValue();
    }

    public static boolean isTrue(final HttpParams params, final String name) {
        return getBool(params, name, false);
    }

    public static boolean isFalse(final HttpParams params, final String name) {
        return !getBool(params, name, false);
    }

}

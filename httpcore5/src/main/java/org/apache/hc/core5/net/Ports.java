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
package org.apache.hc.core5.net;

import org.apache.hc.core5.util.Args;

/**
 * Port helper methods.
 *
 * @since 5.0
 */
public class Ports {

    /**
     * The scheme default port.
     */
    public final static int SCHEME_DEFAULT = -1;

    /**
     * The minimum port value per https://tools.ietf.org/html/rfc6335.
     */
    public final static int MIN_VALUE = 0;

    /**
     * The maximum port value per https://tools.ietf.org/html/rfc6335.
     */
    public final static int MAX_VALUE = 65535;

    /**
     * Checks a port number where {@code -1} indicates the scheme default port.
     *
     * @param port
     *            The port to check where {@code -1} indicates the scheme default port.
     * @return the port
     *
     * @throws IllegalArgumentException
     *             If the port parameter is outside the specified range of valid port values, which is between 0 and
     *             65535, inclusive. {@code -1} indicates the scheme default port.
     */
    public static int checkWithDefault(final int port) {
        return Args.checkRange(port, SCHEME_DEFAULT, MAX_VALUE,
                "Port number(Use -1 to specify the scheme default port)");
    }

    /**
     * Checks a port number.
     *
     * @param port
     *            The port to check.
     * @return the port
     *
     * @throws IllegalArgumentException
     *             If the port parameter is outside the specified range of valid port values, which is between 0 and
     *             65535, inclusive.
     */
    public static int check(final int port) {
        return Args.checkRange(port, MIN_VALUE, MAX_VALUE, "Port number");
    }

}

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

package org.apache.http.nio.protocol;

import java.io.IOException;

import org.apache.http.HttpException;
import org.apache.http.nio.NHttpConnection;

/**
 * Event listener used by the HTTP protocol layer to report fatal exceptions
 * and events that may need to be logged using a logging toolkit.
 *
 * @since 4.0
 */
public interface EventListener {

    /**
     * Triggered when an I/O error caused a connection to be terminated.
     *
     * @param ex the I/O exception.
     * @param conn the connection.
     */
    void fatalIOException(IOException ex, NHttpConnection conn);

    /**
     * Triggered when an HTTP protocol error caused a connection to be
     * terminated.
     *
     * @param ex the protocol exception.
     * @param conn the connection.
     */
    void fatalProtocolException(HttpException ex, NHttpConnection conn);

    /**
     * Triggered when a new connection has been established.
     *
     * @param conn the connection.
     */
    void connectionOpen(NHttpConnection conn);

    /**
     * Triggered when a connection has been terminated.
     *
     * @param conn the connection.
     */
    void connectionClosed(NHttpConnection conn);

    /**
     * Triggered when a connection has timed out.
     *
     * @param conn the connection.
     */
    void connectionTimeout(NHttpConnection conn);

}

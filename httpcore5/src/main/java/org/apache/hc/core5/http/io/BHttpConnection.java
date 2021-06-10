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

import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.util.Timeout;

/**
 * Abstract blocking HTTP connection interface.
 *
 * @since 5.0
 */
public interface BHttpConnection extends HttpConnection {

    /**
     * Checks if input data is available from the connection. May wait for
     * the specified time until some data becomes available. Note that some
     * implementations may completely ignore the timeout parameter.
     *
     * @param timeout the maximum time to wait for data
     * @return true if data is available; false if there was no data available
     *         even after waiting for {@code timeout}.
     * @throws IOException if an error happens on the connection
     */
    boolean isDataAvailable(Timeout timeout) throws IOException;

    /**
     * Checks whether this connection has gone down.
     * Network connections may get closed during some time of inactivity
     * for several reasons. The next time a read is attempted on such a
     * connection it will throw an IOException.
     * This method tries to alleviate this inconvenience by trying to
     * find out if a connection is still usable. Implementations may do
     * that by attempting a read with a very small timeout. Thus this
     * method may block for a small amount of time before returning a result.
     * It is therefore an <i>expensive</i> operation.
     *
     * @return  {@code true} if attempts to use this connection are likely
     *          to fail and this connection should be closed,
     *          or {@code false} if they are likely to succeed
     */
    boolean isStale() throws IOException;

    /**
     * Writes out all pending buffered data over the open connection.
     *
     * @throws java.io.IOException in case of an I/O error
     */
    void flush() throws IOException;

}

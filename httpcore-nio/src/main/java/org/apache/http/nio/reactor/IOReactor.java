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

package org.apache.http.nio.reactor;

import java.io.IOException;

/**
 * HttpCore NIO is based on the Reactor pattern as described by Doug Lea.
 * The purpose of I/O reactors is to react to I/O events and to dispatch event
 * notifications to individual I/O sessions. The main idea of I/O reactor
 * pattern is to break away from the one thread per connection model imposed
 * by the classic blocking I/O model.
 * <p>
 * The IOReactor interface represents an abstract object implementing
 * the Reactor pattern.
 * <p>
 * I/O reactors usually employ a small number of dispatch threads (often as
 * few as one) to dispatch I/O event notifications to a much greater number
 * (often as many as several thousands) of I/O sessions or connections. It is
 * generally recommended to have one dispatch thread per CPU core.
 *
 * @since 4.0
 */
public interface IOReactor {

    /**
     * Returns the current status of the reactor.
     *
     * @return reactor status.
     */
    IOReactorStatus getStatus();

    /**
     * Starts the reactor and initiates the dispatch of I/O event notifications
     * to the given {@link IOEventDispatch}.
     *
     * @param eventDispatch the I/O event dispatch.
     * @throws IOException in case of an I/O error.
     */
    void execute(IOEventDispatch eventDispatch)
        throws IOException;

    /**
     * Initiates shutdown of the reactor and blocks approximately for the given
     * period of time in milliseconds waiting for the reactor to terminate all
     * active connections, to shut down itself and to release system resources
     * it currently holds.
     *
     * @param waitMs wait time in milliseconds.
     * @throws IOException in case of an I/O error.
     */
    void shutdown(long waitMs)
        throws IOException;

    /**
     * Initiates shutdown of the reactor and blocks for a default period of
     * time waiting for the reactor to terminate all active connections, to shut
     * down itself and to release system resources it currently holds. It is
     * up to individual implementations to decide for how long this method can
     * remain blocked.
     *
     * @throws IOException in case of an I/O error.
     */
    void shutdown()
        throws IOException;

}

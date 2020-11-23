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

package org.apache.hc.core5.reactor;

import java.net.SocketAddress;
import java.nio.channels.ByteChannel;
import java.util.concurrent.locks.Lock;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.SocketModalCloseable;
import org.apache.hc.core5.util.Identifiable;
import org.apache.hc.core5.util.Timeout;

/**
 * IOSession interface represents a sequence of logically related data exchanges
 * between two end points.
 * <p>
 * The channel associated with implementations of this interface can be used to
 * read data from and write data to the session.
 * <p>
 * I/O sessions are not bound to an execution thread, therefore one cannot use
 * the context of the thread to store a session's state. All details about
 * a particular session must be stored within the session itself, usually
 * using execution context associated with it.
 * <p>
 * Implementations of this interface are expected to be threading safe.
 *
 * @since 4.0
 */
@Internal
public interface IOSession extends ByteChannel, SocketModalCloseable, Identifiable {

    /**
     * This enum represents a set of states I/O session transitions through
     * during its life-span.
     */
    enum Status {

        ACTIVE,
        CLOSING,
        CLOSED

    }

    /**
     * Returns event handler associated with the session.
     *
     * @since 5.0
     */
    IOEventHandler getHandler();

    /**
     * Upgrades event handler associated with the session.
     *
     * @since 5.0
     */
    void upgrade(IOEventHandler handler);

    /**
     * Returns session lock that should be used by I/O event handlers
     * to synchronize access to the session.
     *
     * @since 5.0
     */
    Lock getLock();

    /**
     * Inserts {@link Command} at the end of the command queue.
     *
     * @since 5.0
     */
    void enqueue(Command command, Command.Priority priority);

    /**
     * Tests if there enqueued commands pending execution.
     *
     * @since 5.0
     */
    boolean hasCommands();

    /**
     * Removes first {@link Command} from the command queue if available.
     *
     * @since 5.0
     */
    Command poll();

    /**
     * Returns the underlying I/O channel associated with this session.
     *
     * @return the I/O channel.
     */
    ByteChannel channel();

    /**
     * Returns address of the remote peer.
     *
     * @return socket address.
     */
    SocketAddress getRemoteAddress();

    /**
     * Returns local address.
     *
     * @return socket address.
     */
    SocketAddress getLocalAddress();

    /**
     * Returns mask of I/O evens this session declared interest in.
     *
     * @return I/O event mask.
     */
    int getEventMask();

    /**
     * Declares interest in I/O event notifications by setting the event mask
     * associated with the session
     *
     * @param ops new I/O event mask.
     */
    void setEventMask(int ops);

    /**
     * Declares interest in a particular I/O event type by updating the event
     * mask associated with the session.
     *
     * @param op I/O event type.
     */
    void setEvent(int op);

    /**
     * Clears interest in a particular I/O event type by updating the event
     * mask associated with the session.
     *
     * @param op I/O event type.
     */
    void clearEvent(int op);

    /**
     * Terminates the session gracefully and closes the underlying I/O channel.
     * This method ensures that session termination handshake, such as the one
     * used by the SSL/TLS protocol, is correctly carried out.
     */
    @Override
    void close();

    /**
     * Returns status of the session:
     * <p>
     * {@link Status#ACTIVE}: session is active.
     * <p>
     * {@link Status#CLOSING}: session is being closed.
     * <p>
     * {@link Status#CLOSED}: session has been terminated.
     *
     * @return session status.
     */
    Status getStatus();

    /**
     * Returns value of the socket timeout in milliseconds. The value of
     * {@code 0} signifies the session cannot time out.
     *
     * @return socket timeout.
     */
    @Override
    Timeout getSocketTimeout();

    /**
     * Sets value of the socket timeout in milliseconds. The value of
     * {@code 0} signifies the session cannot time out.
     * <p>
     * Please note this operation may affect the last event time.
     *
     * @see #getLastEventTime()
     *
     * @param timeout socket timeout.
     */
    @Override
    void setSocketTimeout(Timeout timeout);

    /**
     * Returns timestamp of the last read event.
     *
     * @return timestamp.
     */
    long getLastReadTime();

    /**
     * Returns timestamp of the last write event.
     *
     * @return timestamp.
     */
    long getLastWriteTime();

    /**
     * Returns timestamp of the last I/O event including socket timeout reset.
     *
     * @see #getSocketTimeout()
     *
     * @return timestamp.
     */
    long getLastEventTime();

    /**
     * Updates the timestamp of the last read event
     */
    void updateReadTime();

    /**
     * Updates the timestamp of the last write event
     */
    void updateWriteTime();

}

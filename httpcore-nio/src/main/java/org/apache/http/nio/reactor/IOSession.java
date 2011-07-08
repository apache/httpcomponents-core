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

import java.net.SocketAddress;
import java.nio.channels.ByteChannel;

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
public interface IOSession {

    /**
     * Name of the context attribute key, which can be used to obtain the
     * session attachment object.
     */
    public static final String ATTACHMENT_KEY = "http.session.attachment";

    public static final int ACTIVE       = 0;
    public static final int CLOSING      = 1;
    public static final int CLOSED       = Integer.MAX_VALUE;

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
    void close();

    /**
     * Terminates the session by shutting down the underlying I/O channel.
     */
    void shutdown();

    /**
     * Returns status of the session:
     * <p>
     * {@link #ACTIVE}: session is active.
     * <p>
     * {@link #CLOSING}: session is being closed.
     * <p>
     * {@link #CLOSED}: session has been terminated.
     *
     * @return session status.
     */
    int getStatus();

    /**
     * Determines if the session has been terminated.
     *
     * @return <code>true</code> if the session has been terminated,
     *   <code>false</code> otherwise.
     */
    boolean isClosed();

    /**
     * Returns value of the socket timeout in milliseconds. The value of
     * <code>0</code> signifies the session cannot time out.
     *
     * @return socket timeout.
     */
    int getSocketTimeout();

    /**
     * Sets value of the socket timeout in milliseconds. The value of
     * <code>0</code> signifies the session cannot time out.
     *
     * @param timeout socket timeout.
     */
    void setSocketTimeout(int timeout);

    /**
     * Quite often I/O sessions need to maintain internal I/O buffers in order
     * to transform input / output data prior to returning it to the consumer or
     * writing it to the underlying channel. Memory management in HttpCore NIO
     * is based on the fundamental principle that the data consumer can read
     * only as much input data as it can process without having to allocate more
     * memory. That means, quite often some input data may remain unread in one
     * of the internal or external session buffers. The I/O reactor can query
     * the status of these session buffers, and make sure the consumer gets
     * notified correctly as more data gets stored in one of the session
     * buffers, thus allowing the consumer to read the remaining data once it
     * is able to process it
     * <p>
     * I/O sessions can be made aware of the status of external session buffers
     * using the {@link SessionBufferStatus} interface.
     *
     * @param status
     */
    void setBufferStatus(SessionBufferStatus status);

    /**
     * Determines if the input buffer associated with the session contains data.
     *
     * @return <code>true</code> if the session input buffer contains data,
     *   <code>false</code> otherwise.
     */
    boolean hasBufferedInput();

    /**
     * Determines if the output buffer associated with the session contains
     * data.
     *
     * @return <code>true</code> if the session output buffer contains data,
     *   <code>false</code> otherwise.
     */
    boolean hasBufferedOutput();

    /**
     * This method can be used to associate a particular object with the
     * session by the given attribute name.
     * <p>
     * I/O sessions are not bound to an execution thread, therefore one cannot
     * use the context of the thread to store a session's state. All details
     * about a particular session must be stored within the session itself.
     *
     * @param name name of the attribute.
     * @param obj value of the attribute.
     */
    void setAttribute(String name, Object obj);

    /**
     * Returns the value of the attribute with the given name. The value can be
     * <code>null</code> if not set.
     * <p>
     * The value of the session attachment object can be obtained using
     * {@link #ATTACHMENT_KEY} name.
     *
     * @see #setAttribute(String, Object)
     *
     * @param name name of the attribute.
     * @return value of the attribute.
     */
    Object getAttribute(String name);

    /**
     * Removes attribute with the given name.
     *
     * @see #setAttribute(String, Object)
     *
     * @param name name of the attribute to be removed.
     * @return value of the removed attribute.
     */
    Object removeAttribute(String name);

}

/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 *
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
     * Closes the session and the underlying I/O channel.
     */
    void close();
    
    void shutdown();
    
    int getStatus();
    
    boolean isClosed();

    int getSocketTimeout();
    
    void setSocketTimeout(int timeout);
    
    void setBufferStatus(SessionBufferStatus status);
    
    boolean hasBufferedInput();
    
    boolean hasBufferedOutput();
    
    void setAttribute(String name, Object obj);
    
    Object getAttribute(String name);
    
    Object removeAttribute(String name);

}

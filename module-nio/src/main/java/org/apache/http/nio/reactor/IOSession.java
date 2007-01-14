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

public interface IOSession {

    public static final String ATTACHMENT_KEY = "http.session.attachment";

    ByteChannel channel();
    
    SocketAddress getRemoteAddress();    
    
    SocketAddress getLocalAddress();    

    int getEventMask();
    
    void setEventMask(int ops);
    
    void setEvent(int op);

    void clearEvent(int op);

    void close();
    
    boolean isClosed();

    int getSocketTimeout();
    
    void setSocketTimeout(int timeout);
    
    SessionBufferStatus getBufferStatus();
    
    void setBufferStatus(SessionBufferStatus status);
    
    void setAttribute(String name, Object obj);
    
    Object getAttribute(String name);
    
    Object removeAttribute(String name);

}

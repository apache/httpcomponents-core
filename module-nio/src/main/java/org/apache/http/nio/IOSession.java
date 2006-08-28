/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2006 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.nio;

import java.nio.channels.ByteChannel;

public interface IOSession {

    ByteChannel channel();
    
    int getEventMask();
    
    void setEventMask(int ops);
    
    void setEvent(int op);

    void clearEvent(int op);

    void close();
    
    boolean isClosed();

    int getSocketTimeout();
    
    void setSocketTimeout(int timeout);
    
    void setAttribute(String name, Object obj);
    
    Object getAttribute(String name);
    
    Object removeAttribute(String name);

}

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

package org.apache.http.nio.reactor;

import java.io.IOException;
import java.net.SocketAddress;

public interface SessionRequest {

    public static final String ATTRIB_KEY = "http.connection-request";

    SocketAddress getRemoteAddress();
    
    SocketAddress getLocalAddress();
    
    Object getAttachment();
    
    boolean isCompleted();
    
    IOSession getSession();
    
    IOException getException();

    void waitFor() throws InterruptedException;
    
    void setConnectTimeout(int timeout);
    
    int getConnectTimeout();
    
    void setCallback(SessionRequestCallback callback);
    
    void cancel();
    
}

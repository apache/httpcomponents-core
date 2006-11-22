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

package org.apache.http.nio.impl.reactor;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.Set;

import org.apache.http.nio.reactor.IOSession;

public class BaseIOReactor extends AbstractIOReactor {

    private final long timeoutCheckInterval;
    
    private long lastTimeoutCheck;
    
    public BaseIOReactor(long selectTimeout) throws IOException {
        super(selectTimeout);
        this.timeoutCheckInterval = selectTimeout;
        this.lastTimeoutCheck = System.currentTimeMillis();
    }

    protected void acceptable(final SelectionKey key) {
    }

    protected void connectable(final SelectionKey key) {
    }

    protected void readable(final SelectionKey key) {
        SessionHandle handle = (SessionHandle) key.attachment();
        IOSession session = handle.getSession();
        handle.resetLastRead();

        this.eventDispatch.inputReady(session);
    }

    protected void writable(final SelectionKey key) {
        SessionHandle handle = (SessionHandle) key.attachment();
        IOSession session = handle.getSession();
        handle.resetLastWrite();
        
        this.eventDispatch.outputReady(session);
    }
    
    protected void validate(final Set keys) {
        long currentTime = System.currentTimeMillis();
        if( (currentTime - this.lastTimeoutCheck) >= this.timeoutCheckInterval) {
            this.lastTimeoutCheck = currentTime;
            if (keys != null) {
                for (Iterator it = keys.iterator(); it.hasNext();) {
                    SelectionKey key = (SelectionKey) it.next();
                    timeoutCheck(key, currentTime);
                }
            }
        }
    }

    protected void timeoutCheck(final SelectionKey key, long now) {
        Object attachment = key.attachment();
        if (attachment instanceof SessionHandle) {
            SessionHandle handle = (SessionHandle) key.attachment();
            IOSession session = handle.getSession();
            int timeout = session.getSocketTimeout();
            if (timeout > 0) {
                if (handle.getLastReadTime() + timeout < now) {
                    this.eventDispatch.timeout(session);
                }
            }
        }
    }

    protected void keyCreated(final SelectionKey key, final IOSession session) {
        SessionHandle handle = new SessionHandle(session); 
        key.attach(handle);
    }
    
    protected IOSession keyCancelled(final SelectionKey key) {
        Object attachment = key.attachment();
        if (attachment instanceof SessionHandle) {
            SessionHandle handle = (SessionHandle) attachment;
            return handle.getSession();
        } else {
            return null;
        }
    }
    
}

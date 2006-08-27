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

package org.apache.http.nio.impl;

import org.apache.http.nio.IOSession;

public class SessionHandle {

    private final IOSession session;
    private final long started;

    private long lastRead;
    private long lastWrite;
    
    public SessionHandle(final IOSession session) {
        super();
        if (session == null) {
            throw new IllegalArgumentException("Session may not be null");
        }
        this.session = session;
        long now = System.currentTimeMillis();
        this.started = now;
        this.lastRead = now;
        this.lastWrite = now;
    }

    public IOSession getSession() {
        return this.session;
    }

    public long getStarted() {
        return this.started;
    }

    public long getLastRead() {
        return this.lastRead;
    }

    public long getLastWrite() {
        return this.lastWrite;
    }
    
    public void resetLastRead() {
        this.lastRead = System.currentTimeMillis();
    }
    
    public void resetLastWrite() {
        this.lastWrite = System.currentTimeMillis();
    }
    
}

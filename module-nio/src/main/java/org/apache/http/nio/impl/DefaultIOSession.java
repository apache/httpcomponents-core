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

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.nio.IOSession;

public class DefaultIOSession implements IOSession {
    
    private volatile boolean closed = false;
    
    private final SelectionKey key;
    private final Map attributes;
    
    private int socketTimeout;
    
    public DefaultIOSession(final SelectionKey key) {
        super();
        if (key == null) {
            throw new IllegalArgumentException("Selection key may not be null");
        }
        this.key = key;
        this.attributes = Collections.synchronizedMap(new HashMap());
        this.socketTimeout = 0;
    }
    
    public ByteChannel channel() {
        return (ByteChannel) this.key.channel();
    }
    
    public int getEventMask() {
        return this.key.interestOps();
    }
    
    public void setEventMask(int ops) {
        synchronized (this.key) {
            this.key.interestOps(ops);
            this.key.selector().wakeup();
        }
    }
    
    public void setEvent(int op) {
        synchronized (this.key) {
            int ops = this.key.interestOps();
            this.key.interestOps(ops | op);
            this.key.selector().wakeup();
        }
    }
    
    public void clearEvent(int op) {
        synchronized (this.key) {
            int ops = this.key.interestOps();
            this.key.interestOps(ops & ~op);
            this.key.selector().wakeup();
        }
    }
    
    public int getSocketTimeout() {
        return this.socketTimeout;
    }
    
    public void setSocketTimeout(int timeout) {
        this.socketTimeout = timeout;
    }
    
    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        this.key.cancel();
        try {
            this.key.channel().close();
        } catch (IOException ex) {
            // Munching exceptions is not nice
            // but in this case it is justified
        }
        this.key.selector().wakeup();
    }
    
    public boolean isClosed() {
        return this.closed || !this.key.isValid();
    }
    
    public Object getAttribute(final String name) {
        return this.attributes.get(name);
    }

    public Object removeAttribute(final String name) {
        return this.attributes.remove(name);
    }

    public void setAttribute(final String name, final Object obj) {
        this.attributes.put(name, obj);
    }
    
}

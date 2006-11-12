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
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;

import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.SessionRequest;
import org.apache.http.nio.reactor.SessionRequestCallback;

class SessionRequestImpl implements SessionRequest {

    private volatile boolean completed;

    private final SelectionKey key;
    private final SocketAddress remoteAddress;
    private final SocketAddress localAddress;
    private final Object attachment;
    
    private int connectTimeout;
    private SessionRequestCallback callback;
    private IOSession session = null;
    private IOException exception = null;
    
    public SessionRequestImpl(
            final SocketAddress remoteAddress,
            final SocketAddress localAddress,
            final Object attachment,
            final SelectionKey key) {
        super();
        if (remoteAddress == null) {
            throw new IllegalArgumentException("Remote address may not be null");
        }
        if (key == null) {
            throw new IllegalArgumentException("Selection key may not be null");
        }
        this.remoteAddress = remoteAddress;
        this.localAddress = localAddress;
        this.attachment = attachment;
        this.key = key;
        this.connectTimeout = 0;
    }
    
    public SocketAddress getRemoteAddress() {
        return this.remoteAddress;
    }
    
    public SocketAddress getLocalAddress() {
        return this.localAddress;
    }
    
    public Object getAttachment() {
        return this.attachment;
    }
    
    public boolean isCompleted() {
        return this.completed;
    }
    
    public void waitFor() throws InterruptedException {
        if (this.completed) {
            return;
        }
        synchronized (this) {
            while (!this.completed) {
                wait();
            }
        }
    }
    
    public IOSession getSession() {
        synchronized (this) {
            return this.session;
        }
    }
    
    public IOException getException() {
        synchronized (this) {
            return this.exception;
        }
    }
    
    public void completed(final IOSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Session may not be null");
        }
        if (this.completed) {
            throw new IllegalStateException("Session request already completed");
        }
        this.completed = true;
        synchronized (this) {
            this.session = session;
            if (this.callback != null) {
                this.callback.completed(this);
            }
            notifyAll();
        }
    }
 
    public void failed(final IOException exception) {
        if (exception == null) {
            return;
        }
        if (this.completed) {
            throw new IllegalStateException("Session request already completed");
        }
        this.completed = true;
        synchronized (this) {
            this.exception = exception;
            if (this.callback != null) {
                this.callback.failed(this);
            }
            notifyAll();
        }
    }
 
    public void timeout() {
        if (this.completed) {
            throw new IllegalStateException("Session request already completed");
        }
        synchronized (this) {
            if (this.callback != null) {
                this.callback.timeout(this);
            }
        }
    }
 
    public int getConnectTimeout() {
        return this.connectTimeout;
    }

    public void setConnectTimeout(int timeout) {
        if (this.connectTimeout != timeout) {
            this.connectTimeout = timeout;
            this.key.selector().wakeup();
        }
    }

    public void setCallback(final SessionRequestCallback callback) {
        synchronized (this) {
            this.callback = callback;
            if (this.completed) {
                if (this.exception != null) {
                    callback.failed(this);
                } else if (this.session != null) {
                    callback.completed(this);
                }
            }
        }
    }

    public void cancel() {
        this.key.cancel();
        this.completed = true;
        synchronized (this) {
            notifyAll();
        }
    }
    
}

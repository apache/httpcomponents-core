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

package org.apache.http.impl.nio.reactor;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;

import org.apache.http.nio.reactor.ListenerEndpoint;

public class ListenerEndpointImpl implements ListenerEndpoint {

    private volatile boolean completed;
    private volatile boolean closed;
    private volatile SelectionKey key;
    private volatile SocketAddress address;
    private volatile ServerSocketChannel serverChannel = null;

    private IOException exception = null;
    
    public ListenerEndpointImpl(final SocketAddress address) {
        super();
        if (address == null) {
            throw new IllegalArgumentException("Address may not be null");
        }
        this.address = address;
    }
    
    public SocketAddress getAddress() {
        synchronized (this) {
            return this.address;
        }
    }
    
    public boolean isCompleted() {
        return this.completed;
    }
    
    public IOException getException() {
        synchronized (this) {
            return this.exception;
        }
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
    
    public void completed(final ServerSocketChannel serverChannel) {
        if (address == null) {
            throw new IllegalArgumentException("Address may not be null");
        }
        if (this.completed) {
            return;
        }
        this.completed = true;
        synchronized (this) {
            this.address = serverChannel.socket().getLocalSocketAddress();
            this.serverChannel = serverChannel;
            notifyAll();
        }
    }
 
    public void failed(final IOException exception) {
        if (exception == null) {
            return;
        }
        if (this.completed) {
            return;
        }
        this.completed = true;
        synchronized (this) {
            this.exception = exception;
            notifyAll();
        }
    }
 
    public void cancel() {
        if (this.completed) {
            return;
        }
        this.completed = true;
        this.closed = true;
        synchronized (this) {
            notifyAll();
        }
    }
 
    protected void setKey(final SelectionKey key) {
        this.key = key;
    }

    public boolean isClosed() {
        return this.closed || (this.key != null && !this.key.isValid());
    }

    public void close() {
        if (this.closed) {
            return;
        }
        this.completed = true;
        this.closed = true;
        if (this.serverChannel != null && this.serverChannel.isOpen()) {
            try {
                this.serverChannel.close();
            } catch (IOException ignore) {}
        }
        if (this.key != null) {
            this.key.cancel();
        }
        synchronized (this) {
            notifyAll();
        }
    }
    
}

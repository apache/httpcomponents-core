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
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.nio.reactor.IOSession;
import org.apache.http.nio.reactor.SessionBufferStatus;

public class IOSessionImpl implements IOSession {
    
    private volatile boolean closed = false;
    
    private final SelectionKey key;
    private final ByteChannel channel;
    private final SessionClosedCallback callback;
    private final Map attributes;
    
    private SessionBufferStatus bufferStatus;
    private int socketTimeout;
    
    public IOSessionImpl(final SelectionKey key, final SessionClosedCallback callback) {
        super();
        if (key == null) {
            throw new IllegalArgumentException("Selection key may not be null");
        }
        this.key = key;
        this.channel = new ChannelAdaptor((ByteChannel) this.key.channel());
        this.callback = callback;
        this.attributes = Collections.synchronizedMap(new HashMap());
        this.socketTimeout = 0;
    }
    
    public ByteChannel channel() {
        return this.channel;
    }
    
    public SocketAddress getLocalAddress() {
        Channel channel = this.key.channel();
        if (channel instanceof SocketChannel) {
            return ((SocketChannel)channel).socket().getLocalSocketAddress();
        } else {
            return null;
        }
    }

    public SocketAddress getRemoteAddress() {
        Channel channel = this.key.channel();
        if (channel instanceof SocketChannel) {
            return ((SocketChannel)channel).socket().getRemoteSocketAddress();
        } else {
            return null;
        }
    }

    public int getEventMask() {
        return this.key.interestOps();
    }
    
    public void setEventMask(int ops) {
        if (this.closed) {
            return;
        }
        synchronized (this.key) {
            this.key.interestOps(ops);
            this.key.selector().wakeup();
        }
    }
    
    public void setEvent(int op) {
        if (this.closed) {
            return;
        }
        synchronized (this.key) {
            int ops = this.key.interestOps();
            this.key.interestOps(ops | op);
            this.key.selector().wakeup();
        }
    }
    
    public void clearEvent(int op) {
        if (this.closed) {
            return;
        }
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
        if (this.callback != null) {
            this.callback.sessionClosed(this);
        }
        if (this.key.selector().isOpen()) {
            this.key.selector().wakeup();
        }
    }
    
    public boolean isClosed() {
        return this.closed || !this.key.isValid();
    }
    
    public boolean hasBufferedInput() {
        return this.bufferStatus != null && this.bufferStatus.hasBufferedInput();
    }
    
    public boolean hasBufferedOutput() {
        return this.bufferStatus != null && this.bufferStatus.hasBufferedOutput();
    }

    public void setBufferStatus(final SessionBufferStatus bufferStatus) {
        this.bufferStatus = bufferStatus;
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

    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("[");
        if (this.key.isValid()) {
            buffer.append("event mask: ");
            buffer.append(this.key.interestOps());
            buffer.append("; ready ops: ");
            buffer.append(this.key.readyOps());
        } else {
            buffer.append("invalid");
        }
        buffer.append("]");
        return buffer.toString();
    }
    
    private class ChannelAdaptor implements ByteChannel {

        private final ByteChannel channel;
        
        public ChannelAdaptor(final ByteChannel channel) {
            super();
            this.channel = channel;
        }
        
        public int write(final ByteBuffer src) throws IOException {
            if (IOSessionImpl.this.isClosed()) {
                return 0;
            }
            try {
                return this.channel.write(src);
            } catch (ClosedChannelException ex) {
                IOSessionImpl.this.close();
                return 0;
            }
        }

        public int read(final ByteBuffer dst) throws IOException {
            if (IOSessionImpl.this.isClosed()) {
                return -1;
            }
            try {
                return this.channel.read(dst);
            } catch (ClosedChannelException ex) {
                IOSessionImpl.this.close();
                return -1;
            }
        }

        public void close() throws IOException {
            this.channel.close();
        }

        public boolean isOpen() {
            return this.channel.isOpen();
        }

        public String toString() {
            return this.channel.toString();
        }
        
    }

}

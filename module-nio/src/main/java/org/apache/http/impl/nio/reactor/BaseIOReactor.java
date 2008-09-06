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

import java.io.InterruptedIOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.http.nio.reactor.EventMask;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.nio.reactor.IOReactorExceptionHandler;
import org.apache.http.nio.reactor.IOSession;

public class BaseIOReactor extends AbstractIOReactor {

    private final long timeoutCheckInterval;
    private final Set<IOSession> bufferingSessions;

    private long lastTimeoutCheck;

    private IOReactorExceptionHandler exceptionHandler = null;
    private IOEventDispatch eventDispatch = null;

    public BaseIOReactor(long selectTimeout) throws IOReactorException {
        super(selectTimeout);
        this.bufferingSessions = new HashSet<IOSession>();
        this.timeoutCheckInterval = selectTimeout;
        this.lastTimeoutCheck = System.currentTimeMillis();
    }

    public void execute(
            final IOEventDispatch eventDispatch) throws InterruptedIOException, IOReactorException {
        if (eventDispatch == null) {
            throw new IllegalArgumentException("Event dispatcher may not be null");
        }
        this.eventDispatch = eventDispatch;
        execute();
    }

    public void setExceptionHandler(IOReactorExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    protected void handleRuntimeException(final RuntimeException ex) {
        if (this.exceptionHandler == null || !this.exceptionHandler.handle(ex)) {
            throw ex;
        }
    }

    @Override
    protected void acceptable(final SelectionKey key) {
    }

    @Override
    protected void connectable(final SelectionKey key) {
    }

    @Override
    protected void readable(final SelectionKey key) {
        SessionHandle handle = (SessionHandle) key.attachment();
        IOSession session = handle.getSession();
        handle.resetLastRead();

        try {
            this.eventDispatch.inputReady(session);
        } catch (CancelledKeyException ex) {
            queueClosedSession(session);
            key.attach(null);
        } catch (RuntimeException ex) {
            handleRuntimeException(ex);
        }
        if (session.hasBufferedInput()) {
            this.bufferingSessions.add(session);
        }
    }

    @Override
    protected void writable(final SelectionKey key) {
        SessionHandle handle = (SessionHandle) key.attachment();
        IOSession session = handle.getSession();
        handle.resetLastWrite();

        try {
            this.eventDispatch.outputReady(session);
        } catch (CancelledKeyException ex) {
            queueClosedSession(session);
            key.attach(null);
        } catch (RuntimeException ex) {
            handleRuntimeException(ex);
        }
    }

    @Override
    protected void validate(final Set<SelectionKey> keys) {
        long currentTime = System.currentTimeMillis();
        if( (currentTime - this.lastTimeoutCheck) >= this.timeoutCheckInterval) {
            this.lastTimeoutCheck = currentTime;
            if (keys != null) {
                for (Iterator<SelectionKey> it = keys.iterator(); it.hasNext();) {
                    SelectionKey key = it.next();
                    timeoutCheck(key, currentTime);
                }
            }
        }
        if (!this.bufferingSessions.isEmpty()) {
            for (Iterator<IOSession> it = this.bufferingSessions.iterator(); it.hasNext(); ) {
                IOSession session = it.next();
                if (!session.hasBufferedInput()) {
                    it.remove();
                    continue;
                }
                
                int ops = 0;
                try {
                    ops = session.getEventMask();
                } catch (CancelledKeyException ex) {
                    it.remove();
                    queueClosedSession(session);
                    continue;
                }
                
                if ((ops & EventMask.READ) > 0) {
                    try {
                        this.eventDispatch.inputReady(session);
                    } catch (CancelledKeyException ex) {
                        it.remove();
                        queueClosedSession(session);
                    } catch (RuntimeException ex) {
                        handleRuntimeException(ex);
                    }
                    if (!session.hasBufferedInput()) {
                        it.remove();
                    }
                }
            }
        }
    }

    @Override
    protected void timeoutCheck(final SelectionKey key, long now) {
        Object attachment = key.attachment();
        if (attachment instanceof SessionHandle) {
            SessionHandle handle = (SessionHandle) key.attachment();
            IOSession session = handle.getSession();
            int timeout = session.getSocketTimeout();
            if (timeout > 0) {
                if (handle.getLastAccessTime() + timeout < now) {
                    try {
                        this.eventDispatch.timeout(session);
                    } catch (CancelledKeyException ex) {
                        queueClosedSession(session);
                        key.attach(null);
                    } catch (RuntimeException ex) {
                        handleRuntimeException(ex);
                    }
                }
            }
        }
    }

    @Override
    protected void sessionCreated(final SelectionKey key, final IOSession session) {
        SessionHandle handle = new SessionHandle(session);
        key.attach(handle);
        try {
            this.eventDispatch.connected(session);
        } catch (CancelledKeyException ex) {
            queueClosedSession(session);
            key.attach(null);
        } catch (RuntimeException ex) {
            handleRuntimeException(ex);
        }
    }

    @Override
    protected IOSession getSession(final SelectionKey key) {
        Object attachment = key.attachment();
        if (attachment instanceof SessionHandle) {
            SessionHandle handle = (SessionHandle) attachment;
            return handle.getSession();
        } else {
            return null;
        }
    }

    @Override
    protected void sessionClosed(final IOSession session) {
        try {
            this.eventDispatch.disconnected(session);
        } catch (CancelledKeyException ex) {
            // ignore
        } catch (RuntimeException ex) {
            handleRuntimeException(ex);
        }
    }

}

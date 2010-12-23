/*
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
import org.apache.http.nio.reactor.IOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.nio.reactor.IOReactorExceptionHandler;
import org.apache.http.nio.reactor.IOSession;

/**
 * Default implementation of {@link AbstractIOReactor} that serves as a base
 * for more advanced {@link IOReactor} implementations. This class adds
 * support for the I/O event dispatching using {@link IOEventDispatch},
 * management of buffering sessions, and session timeout handling.
 *
 * @since 4.0
 */
public class BaseIOReactor extends AbstractIOReactor {

    private final long timeoutCheckInterval;
    private final Set<IOSession> bufferingSessions;

    private long lastTimeoutCheck;

    private IOReactorExceptionHandler exceptionHandler = null;
    private IOEventDispatch eventDispatch = null;

    /**
     * Creates new BaseIOReactor instance.
     *
     * @param selectTimeout the select timeout.
     * @throws IOReactorException in case if a non-recoverable I/O error.
     */
    public BaseIOReactor(long selectTimeout) throws IOReactorException {
        this(selectTimeout, false);
    }

    /**
     * Creates new BaseIOReactor instance.
     *
     * @param selectTimeout the select timeout.
     * @param interestOpsQueueing Ops queueing flag.
     *
     * @throws IOReactorException in case if a non-recoverable I/O error.
     *
     * @since 4.1
     */
    public BaseIOReactor(
            long selectTimeout, boolean interestOpsQueueing) throws IOReactorException {
        super(selectTimeout, interestOpsQueueing);
        this.bufferingSessions = new HashSet<IOSession>();
        this.timeoutCheckInterval = selectTimeout;
        this.lastTimeoutCheck = System.currentTimeMillis();
    }

    /**
     * Activates the I/O reactor. The I/O reactor will start reacting to I/O
     * events and dispatch I/O event notifications to the given
     * {@link IOEventDispatch}.
     *
     * @throws InterruptedIOException if the dispatch thread is interrupted.
     * @throws IOReactorException in case if a non-recoverable I/O error.
     */
    public void execute(
            final IOEventDispatch eventDispatch) throws InterruptedIOException, IOReactorException {
        if (eventDispatch == null) {
            throw new IllegalArgumentException("Event dispatcher may not be null");
        }
        this.eventDispatch = eventDispatch;
        execute();
    }

    /**
     * Sets exception handler for this I/O reactor.
     *
     * @param exceptionHandler the exception handler.
     */
    public void setExceptionHandler(IOReactorExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * Handles the given {@link RuntimeException}. This method delegates
     * handling of the exception to the {@link IOReactorExceptionHandler},
     * if available.
     *
     * @param ex the runtime exception.
     */
    protected void handleRuntimeException(final RuntimeException ex) {
        if (this.exceptionHandler == null || !this.exceptionHandler.handle(ex)) {
            throw ex;
        }
    }

    /**
     * This I/O reactor implementation does not react to the
     * {@link SelectionKey#OP_ACCEPT} event.
     * <p>
     * Super-classes can override this method to react to the event.
     */
    @Override
    protected void acceptable(final SelectionKey key) {
    }

    /**
     * This I/O reactor implementation does not react to the
     * {@link SelectionKey#OP_CONNECT} event.
     * <p>
     * Super-classes can override this method to react to the event.
     */
    @Override
    protected void connectable(final SelectionKey key) {
    }

    /**
     * Processes {@link SelectionKey#OP_READ} event on the given selection key.
     * This method dispatches the event notification to the
     * {@link IOEventDispatch#inputReady(IOSession)} method.
     */
    @Override
    protected void readable(final SelectionKey key) {
        SessionHandle handle = (SessionHandle) key.attachment();
        IOSession session = handle.getSession();
        handle.resetLastRead();

        try {
            this.eventDispatch.inputReady(session);
            if (session.hasBufferedInput()) {
                this.bufferingSessions.add(session);
            }
        } catch (CancelledKeyException ex) {
            queueClosedSession(session);
            key.attach(null);
        } catch (RuntimeException ex) {
            handleRuntimeException(ex);
        }
    }

    /**
     * Processes {@link SelectionKey#OP_WRITE} event on the given selection key.
     * This method dispatches the event notification to the
     * {@link IOEventDispatch#outputReady(IOSession)} method.
     */
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

    /**
     * Verifies whether any of the sessions associated with the given selection
     * keys timed out by invoking the {@link #timeoutCheck(SelectionKey, long)}
     * method.
     * <p>
     * This method will also invoke the
     * {@link IOEventDispatch#inputReady(IOSession)} method on all sessions
     * that have buffered input data.
     */
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

    /**
     * Performs timeout check for the I/O session associated with the given
     * selection key.
     */
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

    /**
     * Processes newly created I/O session. This method dispatches the event
     * notification to the {@link IOEventDispatch#connected(IOSession)} method.
     */
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

    /**
     * Processes closed I/O session. This method dispatches the event
     * notification to the {@link IOEventDispatch#disconnected(IOSession)}
     * method.
     */
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

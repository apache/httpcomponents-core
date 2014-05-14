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

import org.apache.http.annotation.ThreadSafe;
import org.apache.http.nio.reactor.EventMask;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.nio.reactor.IOReactorExceptionHandler;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.util.Args;

/**
 * Default implementation of {@link AbstractIOReactor} that serves as a base
 * for more advanced {@link org.apache.http.nio.reactor.IOReactor}
 * implementations. This class adds support for the I/O event dispatching
 * using {@link IOEventDispatch}, management of buffering sessions, and
 * session timeout handling.
 *
 * @since 4.0
 */
@ThreadSafe // public methods only
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
    public BaseIOReactor(final long selectTimeout) throws IOReactorException {
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
            final long selectTimeout, final boolean interestOpsQueueing) throws IOReactorException {
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
    @Override
    public void execute(
            final IOEventDispatch eventDispatch) throws InterruptedIOException, IOReactorException {
        Args.notNull(eventDispatch, "Event dispatcher");
        this.eventDispatch = eventDispatch;
        execute();
    }

    /**
     * Sets exception handler for this I/O reactor.
     *
     * @param exceptionHandler the exception handler.
     */
    public void setExceptionHandler(final IOReactorExceptionHandler exceptionHandler) {
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
        final IOSession session = getSession(key);
        try {
            // Try to gently feed more data to the event dispatcher
            // if the session input buffer has not been fully exhausted
            // (the choice of 5 iterations is purely arbitrary)
            for (int i = 0; i < 5; i++) {
                this.eventDispatch.inputReady(session);
                if (!session.hasBufferedInput()
                        || (session.getEventMask() & SelectionKey.OP_READ) == 0) {
                    break;
                }
            }
            if (session.hasBufferedInput()) {
                this.bufferingSessions.add(session);
            }
        } catch (final CancelledKeyException ex) {
            queueClosedSession(session);
            key.attach(null);
        } catch (final RuntimeException ex) {
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
        final IOSession session = getSession(key);
        try {
            this.eventDispatch.outputReady(session);
        } catch (final CancelledKeyException ex) {
            queueClosedSession(session);
            key.attach(null);
        } catch (final RuntimeException ex) {
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
        final long currentTime = System.currentTimeMillis();
        if( (currentTime - this.lastTimeoutCheck) >= this.timeoutCheckInterval) {
            this.lastTimeoutCheck = currentTime;
            if (keys != null) {
                for (final SelectionKey key : keys) {
                    timeoutCheck(key, currentTime);
                }
            }
        }
        if (!this.bufferingSessions.isEmpty()) {
            for (final Iterator<IOSession> it = this.bufferingSessions.iterator(); it.hasNext(); ) {
                final IOSession session = it.next();
                if (!session.hasBufferedInput()) {
                    it.remove();
                    continue;
                }
                try {
                    if ((session.getEventMask() & EventMask.READ) > 0) {
                        this.eventDispatch.inputReady(session);
                        if (!session.hasBufferedInput()) {
                            it.remove();
                        }
                    }
                } catch (final CancelledKeyException ex) {
                    it.remove();
                    queueClosedSession(session);
                } catch (final RuntimeException ex) {
                    handleRuntimeException(ex);
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
        try {
            this.eventDispatch.connected(session);
        } catch (final CancelledKeyException ex) {
            queueClosedSession(session);
        } catch (final RuntimeException ex) {
            handleRuntimeException(ex);
        }
    }

    /**
     * Processes timed out I/O session. This method dispatches the event
     * notification to the {@link IOEventDispatch#timeout(IOSession)} method.
     */
    @Override
    protected void sessionTimedOut(final IOSession session) {
        try {
            this.eventDispatch.timeout(session);
        } catch (final CancelledKeyException ex) {
            queueClosedSession(session);
        } catch (final RuntimeException ex) {
            handleRuntimeException(ex);
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
        } catch (final CancelledKeyException ex) {
            // ignore
        } catch (final RuntimeException ex) {
            handleRuntimeException(ex);
        }
    }

}

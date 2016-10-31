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

package org.apache.hc.core5.reactor;

import java.io.InterruptedIOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.util.Set;

/**
 * Default implementation of {@link AbstractIOReactor} that serves as a base
 * for more advanced {@link IOReactor} implementations.
 *
 * @since 4.0
 */
public class BaseIOReactor extends AbstractIOReactor {

    private final long timeoutCheckInterval;
    private final IOReactorExceptionHandler exceptionHandler;

    private long lastTimeoutCheck;

    /**
     * Creates new BaseIOReactor instance.
     *
     * @param eventHandlerFactory the event handler factory.
     * @param reactorConfig the reactor configuration.
     */
    public BaseIOReactor(
            final IOEventHandlerFactory eventHandlerFactory,
            final IOReactorConfig reactorConfig,
            final IOReactorExceptionHandler exceptionHandler) {
        super(eventHandlerFactory, reactorConfig);
        this.timeoutCheckInterval = reactorConfig.getSelectInterval();
        this.exceptionHandler = exceptionHandler;
        this.lastTimeoutCheck = System.currentTimeMillis();
    }

    /**
     * Activates the I/O reactor. The I/O reactor will start reacting to I/O
     * events and and dispatch I/O event notifications to the {@link IOEventHandler}
     * associated with the given I/O session.
     *
     * @throws InterruptedIOException if the dispatch thread is interrupted.
     * @throws IOReactorException in case if a non-recoverable I/O error.
     */
    @Override
    public void execute() throws InterruptedIOException, IOReactorException {
        super.execute();
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
     * This method dispatches the event
     * to the {@link IOEventHandler#inputReady(IOSession)} method of the event
     * handler associated with the I/O session.
     */
    @Override
    protected void readable(final SelectionKey key) {
        final IOSession session = getSession(key);
        try {
            final IOEventHandler eventHandler = ensureEventHandler(session);
            eventHandler.inputReady(session);
        } catch (final CancelledKeyException ex) {
            session.shutdown();
            key.attach(null);
        } catch (final RuntimeException ex) {
            handleRuntimeException(ex);
        }
    }

    /**
     * Processes {@link SelectionKey#OP_WRITE} event on the given selection key.
     * This method dispatches the event to
     * the {@link IOEventHandler#outputReady(IOSession)} method of the event
     * handler associated with the I/O session.
     */
    @Override
    protected void writable(final SelectionKey key) {
        final IOSession session = getSession(key);
        try {
            final IOEventHandler eventHandler = ensureEventHandler(session);
            eventHandler.outputReady(session);
        } catch (final CancelledKeyException ex) {
            session.shutdown();
        } catch (final RuntimeException ex) {
            handleRuntimeException(ex);
        }
    }

    /**
     * Verifies whether any of the sessions associated with the given selection
     * keys timed out by invoking the {@link #timeoutCheck(SelectionKey, long)}
     * method.
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
    }

    /**
     * Processes newly created I/O session. This method dispatches the event
     * to the {@link IOEventHandler#connected(IOSession)} method of the event
     * handler associated with the I/O session.
     */
    @Override
    protected void sessionCreated(final IOSession session) {
        try {
            final IOEventHandler eventHandler = ensureEventHandler(session);
            eventHandler.connected(session);
        } catch (final CancelledKeyException ex) {
            session.shutdown();
        } catch (final RuntimeException ex) {
            handleRuntimeException(ex);
        }
    }

    /**
     * Processes timed out I/O session. This method dispatches the event
     * to the {@link IOEventHandler#timeout(IOSession)} method of the event
     * handler associated with the I/O session.
     */
    @Override
    protected void sessionTimedOut(final IOSession session) {
        try {
            final IOEventHandler eventHandler = ensureEventHandler(session);
            eventHandler.timeout(session);
        } catch (final CancelledKeyException ex) {
            session.shutdown();
        } catch (final RuntimeException ex) {
            handleRuntimeException(ex);
        }
    }

    /**
     * Processes closed I/O session. This method dispatches the event
     * to the {@link IOEventHandler#timeout(IOSession)} method of the event
     * handler associated with the I/O session.
     */
    @Override
    protected void sessionClosed(final IOSession session) {
        try {
            final IOEventHandler eventHandler = ensureEventHandler(session);
            eventHandler.disconnected(session);
        } catch (final CancelledKeyException ex) {
            // ignore
        } catch (final RuntimeException ex) {
            handleRuntimeException(ex);
        }
    }

}

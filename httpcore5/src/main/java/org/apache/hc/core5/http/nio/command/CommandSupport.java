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

package org.apache.hc.core5.http.nio.command;

import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOSession;
import org.apache.hc.core5.util.Args;

/**
 * {@link Command} utility methods.
 *
 * @since 5.0
 */
@Internal
public final class CommandSupport {

    /**
     * Fails all pending session {@link Command}s.
     */
    static public void failCommands(final IOSession ioSession, final Exception ex) {
        Args.notNull(ioSession, "I/O session");
        Command command;
        while ((command = ioSession.poll()) != null) {
            if (command instanceof RequestExecutionCommand) {
                final AsyncClientExchangeHandler exchangeHandler = ((RequestExecutionCommand) command).getExchangeHandler();
                try {
                    exchangeHandler.failed(ex);
                } finally {
                    exchangeHandler.releaseResources();
                }
            } else {
                command.cancel();
            }
        }
    }

    /**
     * Cancels all pending session {@link Command}s.
     */
    static public void cancelCommands(final IOSession ioSession) {
        Args.notNull(ioSession, "I/O session");
        Command command;
        while ((command = ioSession.poll()) != null) {
            if (command instanceof RequestExecutionCommand) {
                final AsyncClientExchangeHandler exchangeHandler = ((RequestExecutionCommand) command).getExchangeHandler();
                try {
                    if (!ioSession.isOpen()) {
                        exchangeHandler.failed(new ConnectionClosedException());
                    } else {
                        exchangeHandler.cancel();
                    }
                } finally {
                    exchangeHandler.releaseResources();
                }
            } else {
                command.cancel();
            }
        }
    }

}

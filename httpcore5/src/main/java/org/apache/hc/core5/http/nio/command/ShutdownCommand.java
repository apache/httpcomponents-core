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

import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.reactor.IOSession;

/**
 * Shutdown command. Two shutdown modes are supported: {@link CloseMode#GRACEFUL} and
 * {@link CloseMode#IMMEDIATE}. The exact implementation of both modes is protocol or handler
 * specific.
 *
 * @since 5.0
 */
public final class ShutdownCommand implements Command {

    public static final ShutdownCommand GRACEFUL = new ShutdownCommand(CloseMode.GRACEFUL);
    public static final ShutdownCommand IMMEDIATE = new ShutdownCommand(CloseMode.IMMEDIATE);

    public static final Callback<IOSession> GRACEFUL_IMMEDIATE_CALLBACK = createIOSessionCallback(Priority.IMMEDIATE);
    public static final Callback<IOSession> GRACEFUL_NORMAL_CALLBACK = createIOSessionCallback(Priority.NORMAL);

    private static Callback<IOSession> createIOSessionCallback(final Priority priority) {
        return session -> session.enqueue(ShutdownCommand.GRACEFUL, priority);
    }

    private final CloseMode type;

    public ShutdownCommand(final CloseMode type) {
        this.type = type;
    }

    public CloseMode getType() {
        return type;
    }

    @Override
    public boolean cancel() {
        return true;
    }

    @Override
    public String toString() {
        return "Shutdown: " + type;
    }

}

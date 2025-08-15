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
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.reactor.Command;
import org.apache.hc.core5.util.Args;

/**
 * Stale check command. The {@code callback} will be invoked after the client's event loop has finished processing
 * all pending reads on the connection. If the connection is still active at that point, the callback will be completed
 * with {@code true}. In any other event, the callback will be cancelled, failed, or completed with {@code false}.
 *
 * @since 5.4
 */
@Internal
public final class StaleCheckCommand implements Command {

    private final FutureCallback<Boolean> callback;

    public StaleCheckCommand(final FutureCallback<Boolean> callback) {
        this.callback = Args.notNull(callback, "Callback");
    }

    public FutureCallback<Boolean> getCallback() {
        return callback;
    }

    @Override
    public boolean cancel() {
        callback.cancelled();
        return true;
    }

}

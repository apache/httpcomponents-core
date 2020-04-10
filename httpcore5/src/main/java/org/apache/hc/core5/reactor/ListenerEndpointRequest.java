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

import java.io.Closeable;
import java.net.SocketAddress;

import org.apache.hc.core5.concurrent.BasicFuture;

final class ListenerEndpointRequest implements Closeable {

    final SocketAddress address;
    final Object attachment;
    final BasicFuture<ListenerEndpoint> future;

    ListenerEndpointRequest(final SocketAddress address, final Object attachment, final BasicFuture<ListenerEndpoint> future) {
        this.address = address;
        this.attachment = attachment;
        this.future = future;
    }

    public void completed(final ListenerEndpoint endpoint) {
        if (future != null) {
            future.completed(endpoint);
        }
    }

    public void failed(final Exception cause) {
        if (future != null) {
            future.failed(cause);
        }
    }

    public void cancel() {
        if (future != null) {
            future.cancel();
        }
    }

    public boolean isCancelled() {
        return future != null && future.isCancelled();
    }

    @Override
    public void close() {
        cancel();
    }

}

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

import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.function.Callback;
import org.apache.hc.core5.http.RequestNotExecutedException;
import org.apache.hc.core5.http.StreamControl;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncPushConsumer;
import org.apache.hc.core5.http.nio.HandlerFactory;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestRequestExecutionCommand {

    @Test
    void initiatedCallsCallback() {
        final AsyncClientExchangeHandler handler = Mockito.mock(AsyncClientExchangeHandler.class);
        @SuppressWarnings("unchecked")
        final Callback<StreamControl> callback = Mockito.mock(Callback.class);
        final RequestExecutionCommand command = new RequestExecutionCommand(handler, null, HttpCoreContext.create(), callback);
        final StreamControl streamControl = Mockito.mock(StreamControl.class);

        command.initiated(streamControl);

        Mockito.verify(callback).execute(streamControl);
    }

    @Test
    @SuppressWarnings("deprecation")
    void initiatedUpdatesDeprecatedDependency() {
        final AsyncClientExchangeHandler handler = Mockito.mock(AsyncClientExchangeHandler.class);
        @SuppressWarnings("unchecked")
        final HandlerFactory<AsyncPushConsumer> pushFactory = Mockito.mock(HandlerFactory.class);
        final CancellableDependency dependency = Mockito.mock(CancellableDependency.class);
        final RequestExecutionCommand command = new RequestExecutionCommand(handler, pushFactory, dependency, HttpCoreContext.create());
        final StreamControl streamControl = Mockito.mock(StreamControl.class);

        command.initiated(streamControl);

        Mockito.verify(dependency).setDependency(streamControl);
    }

    @Test
    void failedInvokesHandlerOnce() {
        final AsyncClientExchangeHandler handler = Mockito.mock(AsyncClientExchangeHandler.class);
        final RequestExecutionCommand command = new RequestExecutionCommand(handler, HttpCoreContext.create());

        command.failed(new RuntimeException("boom"));
        command.failed(new RuntimeException("again"));

        Mockito.verify(handler).failed(Mockito.any(Exception.class));
        Mockito.verify(handler).releaseResources();
    }

    @Test
    void cancelInvokesNotExecutedExceptionOnce() {
        final AsyncClientExchangeHandler handler = Mockito.mock(AsyncClientExchangeHandler.class);
        final RequestExecutionCommand command = new RequestExecutionCommand(handler, HttpCoreContext.create());

        Assertions.assertTrue(command.cancel());
        Assertions.assertFalse(command.cancel());

        Mockito.verify(handler).failed(Mockito.isA(RequestNotExecutedException.class));
        Mockito.verify(handler).releaseResources();
    }

    @Test
    void gettersExposeConstructorValues() {
        final AsyncClientExchangeHandler handler = Mockito.mock(AsyncClientExchangeHandler.class);
        @SuppressWarnings("unchecked")
        final HandlerFactory<AsyncPushConsumer> pushFactory = Mockito.mock(HandlerFactory.class);
        final RequestExecutionCommand command = new RequestExecutionCommand(handler, pushFactory, HttpCoreContext.create());

        Assertions.assertSame(handler, command.getExchangeHandler());
        Assertions.assertSame(pushFactory, command.getPushHandlerFactory());
        Assertions.assertNotNull(command.getContext());
    }

}

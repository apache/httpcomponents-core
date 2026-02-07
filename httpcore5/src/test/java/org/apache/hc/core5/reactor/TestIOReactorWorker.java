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

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestIOReactorWorker {

    private static class TestReactor extends AbstractSingleCoreIOReactor {

        private final RuntimeException runtimeException;
        private final Error error;

        TestReactor(final RuntimeException runtimeException, final Error error) {
            super(null);
            this.runtimeException = runtimeException;
            this.error = error;
        }

        @Override
        public void execute() {
            if (error != null) {
                throw error;
            }
            if (runtimeException != null) {
                throw runtimeException;
            }
        }

        @Override
        void doExecute() throws IOException {
        }

        @Override
        void doTerminate() throws IOException {
        }
    }

    @Test
    void capturesRuntimeException() {
        final RuntimeException ex = new RuntimeException("boom");
        final IOReactorWorker worker = new IOReactorWorker(new TestReactor(ex, null));

        worker.run();

        Assertions.assertSame(ex, worker.getThrowable());
    }

    @Test
    void rethrowsError() {
        final Error err = new AssertionError("fatal");
        final IOReactorWorker worker = new IOReactorWorker(new TestReactor(null, err));

        final Error thrown = Assertions.assertThrows(Error.class, worker::run);
        Assertions.assertSame(err, thrown);
        Assertions.assertSame(err, worker.getThrowable());
    }

}

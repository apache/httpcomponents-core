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

package org.apache.hc.core5.testing.framework;

import java.io.IOException;

import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.Test;

public class TestTestingFrameworkRequestHandler {
    @Test
    public void assertNothingThrown() throws Exception {
        final TestingFrameworkRequestHandler handler = new TestingFrameworkRequestHandler() {

            @Override
            public void handle(final ClassicHttpRequest request, final ClassicHttpResponse response, final HttpContext context)
                throws HttpException, IOException {
            }
        };

        handler.assertNothingThrown();
    }

    @Test
    public void assertNothingThrownThrows() throws Exception {
        final String errorMessage = "thrown intentionally";

        final TestingFrameworkRequestHandler handler = new TestingFrameworkRequestHandler() {

            @Override
            public void handle(final ClassicHttpRequest request, final ClassicHttpResponse response, final HttpContext context)
                    throws HttpException, IOException {
                thrown = new TestingFrameworkException(errorMessage);
            }
        };

        handler.handle(null, null, null);
        final TestingFrameworkException exception = Assert.assertThrows(TestingFrameworkException.class,
                () -> handler.assertNothingThrown());
        Assert.assertEquals("Unexpected message", errorMessage, exception.getMessage());
        // a second call should not throw
        handler.assertNothingThrown();
    }

}

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

public class TestingFrameworkException extends Exception {
    public static final String NO_HTTP_CLIENT = "none";

    private ClientTestingAdapter adapter;

    private FrameworkTest test;

    /**
     *
     */
    private static final long serialVersionUID = -1010516169283589675L;

    /**
     * Creates a WebServerTestingFrameworkException with the specified detail message.
     */
    public TestingFrameworkException(final String message) {
        super(message);
    }

    public TestingFrameworkException(final Throwable cause) {
        super(cause);
    }

    @Override
    public String getMessage() {
        String message = super.getMessage();
        if (adapter != null) {
            final ClientPOJOAdapter pojoAdapter = adapter.getClientPOJOAdapter();
            final String tempHttpClient = pojoAdapter == null ? null : pojoAdapter.getClientName();
            final String httpClient = tempHttpClient == null ? NO_HTTP_CLIENT : tempHttpClient;
            if (message == null) {
                message = "null";
            }
            message += "\nHTTP Client=" + httpClient;
        }
        if (test != null) {
            if (message == null) {
                message = "null";
            }
            message += "\ntest:\n" + test;
        }
        return message;
    }

    public void setAdapter(final ClientTestingAdapter adapter) {
        this.adapter = adapter;
    }

    public void setTest(final FrameworkTest test) {
        this.test = test;
    }
}

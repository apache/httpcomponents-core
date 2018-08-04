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

import java.util.Map;

public class ClientTestingAdapter {
    /**
     * This adapter will perform the HTTP request and return the response in the
     * expected format.
     */
    protected ClientPOJOAdapter adapter;

    /*
     * The following is not expected to be changed to true, but it is to highlight
     * where the execute method can call the requestHandler's assertNothingThrown()
     * method if desired.  Since this adapter's execute method does not check
     * the response, there is no need to call it.
     */
    protected boolean callAssertNothingThrown;

    public ClientTestingAdapter() {
    }

    public ClientTestingAdapter(final ClientPOJOAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * See the documentation for the same method in {@link ClientPOJOAdapter}.  This
     * method will typically call it.  However, this method also has access to the
     * test's response expectations if that is needed for some reason.  Furthermore,
     * this method also has access to the {@link TestingFrameworkRequestHandler} so
     * it can optionally call assertNothingThrown() before checking the response
     * further.  It is optional because the test framework will call it later.
     *
     * @param defaultURI           See execute method of {@link ClientPOJOAdapter}.
     * @param request              See execute method of {@link ClientPOJOAdapter}.
     * @param requestHandler       The request handler that checks the received HTTP request
     *                             with the request that was intended.  If there is a
     *                             mismatch of expectations, then the requestHandler will
     *                             throw an exception.  If this execute method does not want
     *                             to make further checks of the response in the case
     *                             the responseHandler threw, then the assertNothingThrown()
     *                             method should be called before doing further checks.
     * @param responseExpectations The response expectations of the test.
     * @return See return of the execute method of {@link ClientPOJOAdapter}.
     * @throws TestingFrameworkException in the case of a problem.
     */
    public Map<String, Object> execute(final String defaultURI, final Map<String, Object> request,
            final TestingFrameworkRequestHandler requestHandler,
            final Map<String, Object> responseExpectations) throws TestingFrameworkException {

        try {
            if (adapter == null) {
                throw new TestingFrameworkException("adapter cannot be null");
            }
            // Call the adapter's execute method to actually make the HTTP request.
            final Map<String, Object> response = adapter.execute(defaultURI, request);

            /*
             * Adapters may call assertNothingThrown() if they would like.  This would be to
             * make sure the following code is not executed in the event there was something
             * thrown in the request handler.
             *
             * Otherwise, the framework will call it when this method returns.  So, it is
             * optional.
             */
            if (callAssertNothingThrown) {
                if (requestHandler == null) {
                    throw new TestingFrameworkException("requestHandler cannot be null");
                }
                requestHandler.assertNothingThrown();
            }

            return response;
        } catch (final TestingFrameworkException e) {
            throw e;
        } catch (final Exception ex) {
            throw new TestingFrameworkException(ex);
        }
    }

    /**
     * See the documentation for the same method in {@link ClientPOJOAdapter}.
     */
    public boolean isRequestSupported(final Map<String, Object> request) {
        return (adapter == null) || adapter.checkRequestSupport(request) == null;
    }

    /**
     * See the documentation for the same method in {@link ClientPOJOAdapter}.
     */
    public Map<String, Object> modifyRequest(final Map<String, Object> request) {
       return (adapter == null) ? request : adapter.modifyRequest(request);
    }

    /**
     * Generally a test's response expectations should not need to be modified, but
     * if a particular HTTP client (such as Groovy's RESTClient which uses HttpClient)
     * needs to modify the response expectations, it should do so here.  After this
     * method returns, the {@link TestingFrameworkRequestHandler} is sent the
     * expectations so the request handler will return a response that matches the
     * expectations.  When the HTTP response is obtained, the received response
     * is matched against the expectations.
     *
     * @param request for the format, see the documentation for {@link ClientPOJOAdapter}.
     * @param responseExpectations for the format, see the documentation for {@link ClientPOJOAdapter}.
     * @return the same or modified response expectations.
     */
    public Map<String, Object> modifyResponseExpectations(final Map<String, Object> request,
                                                          final Map<String, Object> responseExpectations) {
        return responseExpectations;
    }

    /**
     * Getter for the {@link ClientPOJOAdapter} that is actually used to make the
     * HTTP request.
     *
     * @return the {@link ClientPOJOAdapter}.
     */
    public ClientPOJOAdapter getClientPOJOAdapter() {
        return adapter;
    }

}

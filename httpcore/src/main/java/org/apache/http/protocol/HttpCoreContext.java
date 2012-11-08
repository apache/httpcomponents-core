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

package org.apache.http.protocol;

import org.apache.http.HttpConnection;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.annotation.NotThreadSafe;
import org.apache.http.util.Args;

/**
 * Implementation of {@link HttpContext} that provides convenience
 * setters for user assignable attributes and getter for readable attributes.
 *
 * @since 4.3
 */
@NotThreadSafe
public class HttpCoreContext extends BasicHttpContext implements ExecutionContext {

    protected <T> T getAttribute(final String attribname, final Class<T> clazz) {
        Args.notNull(clazz, "Attribute class");
        Object obj = getAttribute(attribname);
        if (obj == null) {
            return null;
        }
        return clazz.cast(obj);
    }

    public <T extends HttpConnection> T getConnection(final Class<T> clazz) {
        return getAttribute(HTTP_CONNECTION, clazz);
    }

    public boolean isExpectContinue() {
        Boolean b = getAttribute(HTTP_EXPECT_CONT, Boolean.class);
        return b != null ? b.booleanValue() : false;
    }

    public void setExpectContinue() {
        setAttribute(HTTP_EXPECT_CONT, Boolean.TRUE);
    }

    public HttpRequest getRequest() {
        return getAttribute(HTTP_REQUEST, HttpRequest.class);
    }

    public boolean isRequestSent() {
        Boolean b = getAttribute(HTTP_REQ_SENT, Boolean.class);
        return b != null ? b.booleanValue() : false;
    }

    public HttpResponse getResponse() {
        return getAttribute(HTTP_RESPONSE, HttpResponse.class);
    }

    public void setTarget(final HttpHost host) {
        setAttribute(HTTP_TARGET_HOST, host);
    }

    public HttpHost getTarget() {
        return getAttribute(HTTP_TARGET_HOST, HttpHost.class);
    }

}

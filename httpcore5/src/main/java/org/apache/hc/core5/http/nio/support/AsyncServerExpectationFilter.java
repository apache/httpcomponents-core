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
package org.apache.hc.core5.http.nio.support;

import java.io.IOException;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.ThreadingBehavior;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.AsyncFilterChain;
import org.apache.hc.core5.http.nio.AsyncFilterHandler;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * @since 5.0
 */
@Contract(threading = ThreadingBehavior.STATELESS)
public class AsyncServerExpectationFilter implements AsyncFilterHandler {

    protected boolean verify(final HttpRequest request, final HttpContext context) throws HttpException {
        return true;
    }

    protected AsyncEntityProducer generateResponseContent(final HttpResponse expectationFailed) throws HttpException {
        return null;
    }

    @Override
    public final AsyncDataConsumer handle(
            final HttpRequest request,
            final EntityDetails entityDetails,
            final HttpContext context,
            final AsyncFilterChain.ResponseTrigger responseTrigger,
            final AsyncFilterChain chain) throws HttpException, IOException {
        if (entityDetails != null) {
            final Header h = request.getFirstHeader(HttpHeaders.EXPECT);
            if (h != null && HeaderElements.CONTINUE.equalsIgnoreCase(h.getValue())) {
                final boolean verified = verify(request, context);
                if (verified) {
                    responseTrigger.sendInformation(new BasicHttpResponse(HttpStatus.SC_CONTINUE));
                } else {
                    final HttpResponse expectationFailed = new BasicHttpResponse(HttpStatus.SC_EXPECTATION_FAILED);
                    final AsyncEntityProducer responseContentProducer = generateResponseContent(expectationFailed);
                    responseTrigger.submitResponse(expectationFailed, responseContentProducer);
                    return null;
                }
            }
        }
        return chain.proceed(request, entityDetails, context, responseTrigger);
    }

}

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

package org.apache.http.nio.protocol;

import java.io.IOException;
import java.util.Queue;

import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.nio.entity.BufferingNHttpEntity;
import org.apache.http.nio.entity.ConsumingNHttpEntity;
import org.apache.http.nio.util.HeapByteBufferAllocator;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

abstract class RequestExecutionHandler
    implements NHttpRequestExecutionHandler, HttpRequestExecutionHandler {

    public void initalizeContext(final HttpContext context, final Object attachment) {
        context.setAttribute("queue", attachment);
    }

    protected abstract HttpRequest generateRequest(Job testjob);

    public HttpRequest submitRequest(final HttpContext context) {

        @SuppressWarnings("unchecked")
        Queue<Job> queue = (Queue<Job>) context.getAttribute("queue");
        if (queue == null) {
            throw new IllegalStateException("Queue is null");
        }

        Job testjob = queue.poll();
        context.setAttribute("job", testjob);

        if (testjob != null) {
            return generateRequest(testjob);
        } else {
            return null;
        }
    }

    public ConsumingNHttpEntity responseEntity(
            final HttpResponse response,
            final HttpContext context) throws IOException {
        return new BufferingNHttpEntity(response.getEntity(),
                new HeapByteBufferAllocator());
    }

    public void handleResponse(final HttpResponse response, final HttpContext context) {
        Job testjob = (Job) context.removeAttribute("job");
        if (testjob == null) {
            throw new IllegalStateException("TestJob is null");
        }

        int statusCode = response.getStatusLine().getStatusCode();
        String content = null;

        HttpEntity entity = response.getEntity();
        if (entity != null) {
            try {
                content = EntityUtils.toString(entity);
            } catch (IOException ex) {
                content = "I/O exception: " + ex.getMessage();
            }
        }
        testjob.setResult(statusCode, content);
    }

    public void finalizeContext(final HttpContext context) {
        Job testjob = (Job) context.removeAttribute("job");
        if (testjob != null) {
            testjob.fail("Request failed");
        }
    }

}

/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
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

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.nio.util.ContentInputBuffer;
import org.apache.http.nio.util.ContentOutputBuffer;

/**
 * This class encapsulates the details of an internal state of a non-blocking 
 * server HTTP connection.
 * 
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 */
class ServerConnState {
   
    public static final int SHUTDOWN                   = -1;
    public static final int READY                      = 0;
    public static final int REQUEST_RECEIVED           = 1;
    public static final int REQUEST_BODY_STREAM        = 2;
    public static final int REQUEST_BODY_DONE          = 4;
    public static final int RESPONSE_SENT              = 8;
    public static final int RESPONSE_BODY_STREAM       = 16;
    public static final int RESPONSE_BODY_DONE         = 32;
    
    private final ContentInputBuffer inbuffer; 
    private final ContentOutputBuffer outbuffer;

    private volatile int inputState;
    private volatile int outputState;
    
    private volatile HttpRequest request;
    private volatile HttpResponse response;
    
    public ServerConnState(
            final ContentInputBuffer inbuffer,
            final ContentOutputBuffer outbuffer) {
        super();
        if (inbuffer == null) {
            throw new IllegalArgumentException("Input content buffer may not be null");
        }
        if (outbuffer == null) {
            throw new IllegalArgumentException("Output content buffer may not be null");
        }
        this.inbuffer = inbuffer;
        this.outbuffer = outbuffer;
        this.inputState = READY;
        this.outputState = READY;
    }

    public ContentInputBuffer getInbuffer() {
        return this.inbuffer;
    }

    public ContentOutputBuffer getOutbuffer() {
        return this.outbuffer;
    }
    
    public int getInputState() {
        return this.inputState;
    }

    public void setInputState(int inputState) {
        this.inputState = inputState;
    }

    public int getOutputState() {
        return this.outputState;
    }

    public void setOutputState(int outputState) {
        this.outputState = outputState;
    }

    public HttpRequest getRequest() {
        return this.request;
    }

    public void setRequest(final HttpRequest request) {
        this.request = request;
    }

    public HttpResponse getResponse() {
        return this.response;
    }

    public void setResponse(final HttpResponse response) {
        this.response = response;
    }

    public void shutdown() {
        this.inbuffer.shutdown();
        this.outbuffer.shutdown();
        this.inputState = SHUTDOWN;
        this.outputState = SHUTDOWN;
    }

    public void resetInput() {
        this.inbuffer.reset();
        this.request = null;
        this.inputState = READY;
    }
    
    public void resetOutput() {
        this.outbuffer.reset();
        this.response = null;
        this.outputState = READY;
    }
    
}
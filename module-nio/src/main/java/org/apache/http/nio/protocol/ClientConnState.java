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

import org.apache.http.nio.util.ContentInputBuffer;
import org.apache.http.nio.util.ContentOutputBuffer;

/**
 * This class encapsulates the details of an internal state of a non-blocking 
 * client HTTP connection.
 * 
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 */

public class ClientConnState {
    
    private final ContentInputBuffer inbuffer; 
    private final ContentOutputBuffer outbuffer;

    public ClientConnState(
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
    }

    public ContentInputBuffer getInbuffer() {
        return this.inbuffer;
    }

    public ContentOutputBuffer getOutbuffer() {
        return this.outbuffer;
    }
    
    public void shutdown() {
        this.inbuffer.shutdown();
        this.outbuffer.shutdown();
    }

    public void reset() {
        this.inbuffer.reset();
        this.outbuffer.reset();
    }
    
}
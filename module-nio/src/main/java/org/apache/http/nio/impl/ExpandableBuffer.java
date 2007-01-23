/*
 * $HeadURL:https://svn.apache.org/repos/asf/jakarta/httpcomponents/httpcore/trunk/module-nio/src/main/java/org/apache/http/nio/buffer/ExpandableBuffer.java $
 * $Revision:473999 $
 * $Date:2006-11-12 17:31:38 +0000 (Sun, 12 Nov 2006) $
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

package org.apache.http.nio.impl;

import java.nio.ByteBuffer;

public class ExpandableBuffer {
    
    public final static int INPUT_MODE = 0;
    public final static int OUTPUT_MODE = 1;
    
    private int mode;
    protected ByteBuffer buffer = null;

    public ExpandableBuffer(int buffersize) {
        super();
        this.buffer = ByteBuffer.allocateDirect(buffersize);
        this.mode = INPUT_MODE;
    }

    protected int getMode() {
        return this.mode;
    }
    
    protected void setOutputMode() {
        if (this.mode != OUTPUT_MODE) {
            this.buffer.flip();
            this.mode = OUTPUT_MODE;
        }
    }
    
    protected void setInputMode() {
        if (this.mode != INPUT_MODE) {
            if (this.buffer.hasRemaining()) {
                this.buffer.compact();
            } else {
                this.buffer.clear();
            }
            this.mode = INPUT_MODE;
        }
    }
    
    private void expandCapacity(int capacity) {
        ByteBuffer oldbuffer = this.buffer;
        this.buffer = ByteBuffer.allocateDirect(capacity);
        oldbuffer.flip();
        this.buffer.put(oldbuffer);
    }
    
    protected void expand() {
        int newcapacity = (this.buffer.capacity() + 1) << 1;
        if (newcapacity < 0) {
            newcapacity = Integer.MAX_VALUE;
        }
        expandCapacity(newcapacity);
    }
    
    protected void ensureCapacity(int requiredCapacity) {
        if (requiredCapacity > this.buffer.capacity()) {
            expandCapacity(requiredCapacity);
        }
    }
    
    public int capacity() {
        return this.buffer.capacity();
    }
 
    public boolean hasData() {
        setOutputMode();
        return this.buffer.hasRemaining();
    }
    
    public int length() {
        setOutputMode();
        return this.buffer.remaining();
    }
    
    protected void clear() {
        this.buffer.clear();        
        this.mode = INPUT_MODE;
    }
        
}

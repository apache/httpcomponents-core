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

package org.apache.http.mockup;

public class RequestCount {

    private volatile boolean aborted;
    private volatile int value;
    
    public RequestCount(int initialValue) {
        this.value = initialValue;
        this.aborted = false;
    }
    
    public int getValue() {
        return this.value;
    }
    
    public void decrement() {
        synchronized (this) {
            if (!this.aborted) {
                this.value--;
            }
            notifyAll();
        }
    }

    public void abort() {
        synchronized (this) {
            this.aborted = true;
            notifyAll();
        }
    }

    public boolean isAborted() {
        return this.aborted;
    }
    
    public void await(int count, long timeout) throws InterruptedException {
        synchronized (this) {
            long deadline = System.currentTimeMillis() + timeout;
            long remaining = timeout;
            while (!this.aborted && this.value > count) {
                wait(remaining);
                if (timeout > 0) {
                    remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        break;
                    }
                }
            }
        }
    }
    
    public void await(long timeout) throws InterruptedException {
        await(0, timeout);
    }
    
}

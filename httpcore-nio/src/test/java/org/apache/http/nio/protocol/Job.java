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

import java.util.Random;

public class Job {

    private static final Random RND = new Random();
    private static final String TEST_CHARS = "0123456789ABCDEF";

    private final int count;
    private final String pattern;

    private volatile boolean completed;
    private volatile int statusCode;
    private volatile String result;
    private volatile String failureMessage;
    private volatile Exception ex;

    public Job(int maxCount) {
        super();
        this.count = RND.nextInt(maxCount - 1) + 1;
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            char rndchar = TEST_CHARS.charAt(RND.nextInt(TEST_CHARS.length() - 1));
            buffer.append(rndchar);
        }
        this.pattern = buffer.toString();
    }

    public Job() {
        this(1000);
    }

    public Job(final String pattern, int count) {
        super();
        this.count = count;
        this.pattern = pattern;
    }

    public int getCount() {
        return this.count;
    }

    public String getPattern() {
        return this.pattern;
    }

    public String getExpected() {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < this.count; i++) {
            buffer.append(this.pattern);
        }
        return buffer.toString();
    }

    public int getStatusCode() {
        return this.statusCode;
    }

    public String getResult() {
        return this.result;
    }

    public boolean isSuccessful() {
        return this.result != null;
    }

    public String getFailureMessage() {
        return this.failureMessage;
    }

    public Exception getException() {
        return this.ex;
    }

    public boolean isCompleted() {
        return this.completed;
    }

    public synchronized void setResult(int statusCode, final String result) {
        if (this.completed) {
            return;
        }
        this.completed = true;
        this.statusCode = statusCode;
        this.result = result;
        notifyAll();
    }

    public synchronized void fail(final String message, final Exception ex) {
        if (this.completed) {
            return;
        }
        this.completed = true;
        this.result = null;
        this.failureMessage = message;
        this.ex = ex;
        notifyAll();
    }

    public void fail(final String message) {
        fail(message, null);
    }

    public synchronized void waitFor() throws InterruptedException {
        while (!this.completed) {
            wait();
        }
    }

}

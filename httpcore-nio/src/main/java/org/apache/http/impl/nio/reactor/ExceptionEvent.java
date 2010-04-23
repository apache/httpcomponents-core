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

package org.apache.http.impl.nio.reactor;

import java.util.Date;

/**
 * A {@link Throwable} instance along with a time stamp.
 *
 * @since 4.0
 */
public class ExceptionEvent {

    private final Throwable ex;
    private final long time;

    public ExceptionEvent(final Throwable ex, final Date timestamp) {
        super();
        this.ex = ex;
        if (timestamp != null) {
            this.time = timestamp.getTime();
        } else {
            this.time = 0;
        }
    }

    public ExceptionEvent(final Exception ex) {
        this(ex, new Date());
    }

    public Throwable getCause() {
        return ex;
    }

    public Date getTimestamp() {
        return new Date(this.time);
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append(new Date(this.time));
        buffer.append(" ");
        buffer.append(this.ex);
        return buffer.toString();
    }

}

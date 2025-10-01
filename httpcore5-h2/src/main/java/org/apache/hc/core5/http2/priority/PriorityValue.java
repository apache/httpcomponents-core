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
package org.apache.hc.core5.http2.priority;

import java.util.Objects;

public final class PriorityValue {

    public static final int DEFAULT_URGENCY = 3;
    public static final boolean DEFAULT_INCREMENTAL = false;

    private final int urgency;
    private final boolean incremental;

    public PriorityValue(final int urgency, final boolean incremental) {
        if (urgency < 0 || urgency > 7) {
            throw new IllegalArgumentException("urgency out of range [0..7]: " + urgency);
        }
        this.urgency = urgency;
        this.incremental = incremental;
    }

    public static PriorityValue of(final int urgency, final boolean incremental) {
        return new PriorityValue(urgency, incremental);
    }

    public static PriorityValue defaults() {
        return new PriorityValue(DEFAULT_URGENCY, DEFAULT_INCREMENTAL);
    }

    public int getUrgency() {
        return urgency;
    }

    public boolean isIncremental() {
        return incremental;
    }

    public PriorityValue withUrgency(final int newUrgency) {
        return new PriorityValue(newUrgency, this.incremental);
    }

    public PriorityValue withIncremental(final boolean newIncremental) {
        return new PriorityValue(this.urgency, newIncremental);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PriorityValue)) {
            return false;
        }
        final PriorityValue other = (PriorityValue) obj;
        return urgency == other.urgency && incremental == other.incremental;
    }

    @Override
    public int hashCode() {
        return Objects.hash(urgency, incremental);
    }

    @Override
    public String toString() {
        return "PriorityValue{u=" + urgency + ", i=" + incremental + '}';
    }
}
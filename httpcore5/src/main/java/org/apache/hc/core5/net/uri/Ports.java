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
package org.apache.hc.core5.net.uri;

import org.apache.hc.core5.annotation.Contract;
import org.apache.hc.core5.annotation.Internal;
import org.apache.hc.core5.annotation.ThreadingBehavior;

/**
 * Port parsing & validation.
 *
 */
@Internal
@Contract(threading = ThreadingBehavior.STATELESS)
final class Ports {

    static int parsePort(final char[] a, final int from, final int toExcl) {
        if (from >= toExcl) {
            return -1;
        }
        int v = 0;
        for (int i = from; i < toExcl; i++) {
            final char c = a[i];
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("Invalid port: non-digit");
            }
            v = v * 10 + c - '0';
            if (v > 65535) {
                throw new IllegalArgumentException("Invalid port: out of range");
            }
        }
        return v;
    }

    private Ports() {
    }
}

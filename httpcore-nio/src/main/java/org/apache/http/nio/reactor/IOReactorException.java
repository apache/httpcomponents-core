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

package org.apache.http.nio.reactor;

import java.io.IOException;

/**
 * I/O exception that can be thrown by an I/O reactor. Usually exceptions
 * of this type are fatal and are not recoverable.
 *
 * @since 4.0
 */
public class IOReactorException extends IOException {

    private static final long serialVersionUID = -4248110651729635749L;

    public IOReactorException(final String message, final Exception cause) {
        super(message);
        if (cause != null) {
            initCause(cause);
        }
    }

    public IOReactorException(final String message) {
        super(message);
    }

}

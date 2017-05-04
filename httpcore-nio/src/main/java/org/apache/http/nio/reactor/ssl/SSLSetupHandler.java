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

package org.apache.http.nio.reactor.ssl;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import org.apache.http.nio.reactor.IOSession;

/**
 * Callback interface that can be used to customize various aspects of
 * the TLS/SSL protocol.
 *
 * @since 4.2
 */
public interface SSLSetupHandler {

    /**
     * Triggered when the SSL connection is being initialized. Custom handlers
     * can use this callback to customize properties of the {@link SSLEngine}
     * used to establish the SSL session.
     *
     * @param sslEngine the SSL engine.
     * @throws SSLException if case of SSL protocol error.
     */
    //FIXME: fix type
    void initalize(SSLEngine sslEngine) throws SSLException;

    /**
     * Triggered when the SSL connection has been established and initial SSL
     * handshake has been successfully completed. Custom handlers can use
     * this callback to verify properties of the {@link SSLSession}.
     * For instance this would be the right place to enforce SSL cipher
     * strength, validate certificate chain and do hostname checks.
     *
     * @param ioSession the underlying IOSession for the SSL connection.
     * @param sslSession newly created SSL session.
     * @throws SSLException if case of SSL protocol error.
     */
    void verify(IOSession ioSession, SSLSession sslSession) throws SSLException;

}

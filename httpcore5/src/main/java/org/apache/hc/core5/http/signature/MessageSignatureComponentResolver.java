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
package org.apache.hc.core5.http.signature;

/**
 * Resolves a covered component to its RFC canonical component value.
 *
 * @since 5.5
 */
public interface MessageSignatureComponentResolver {

    /**
     * Resolves the given covered component to its canonical value in the supplied context.
     *
     * @param component the covered component to resolve.
     * @param context   the message signature context.
     * @return the canonical component value.
     * @throws MessageSignatureException if the component cannot be resolved.
     * @since 5.5
     */
    String resolve(final MessageSignatureComponent component, final MessageSignatureContext context)
            throws MessageSignatureException;

}

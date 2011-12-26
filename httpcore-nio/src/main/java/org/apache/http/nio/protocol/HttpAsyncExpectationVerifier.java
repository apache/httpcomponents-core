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

import java.io.IOException;

import org.apache.http.HttpException;
import org.apache.http.protocol.HttpContext;

/**
 * <tt>HttpAsyncExpectationVerifier</tt> defines a strategy to verify whether
 * or not an incoming HTTP request meets the target server's expectations.
 *<p/>
 * A server that does not understand or is unable to comply with any of
 * the expectation values in the Expect field of a request MUST respond
 * with appropriate error status. The server MUST respond with a 417
 * (Expectation Failed) status if any of the expectations cannot be met
 * or, if there are other problems with the request, some other 4xx
 * status.
 *
 * @since 4.2
 */
public interface HttpAsyncExpectationVerifier {

    void verify(
            HttpAsyncExchange httpExchange,
            HttpContext context) throws HttpException, IOException;

}

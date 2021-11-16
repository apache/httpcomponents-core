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

package org.apache.hc.core5.http.message;

import org.apache.hc.core5.http.message.StatusLine.StatusClass;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link org.apache.hc.core5.http.message.StatusLine}.
 */
public class TestBasicStatusLine {

    @Test
    public void testGetStatusClass() {
        StatusLine statusLine = new StatusLine(new BasicHttpResponse(100, "Continue"));
        Assertions.assertEquals(StatusClass.INFORMATIONAL, statusLine.getStatusClass());

        statusLine = new StatusLine(new BasicHttpResponse(200, "OK"));
        Assertions.assertEquals(StatusClass.SUCCESSFUL, statusLine.getStatusClass());

        statusLine = new StatusLine(new BasicHttpResponse(302, "Found"));
        Assertions.assertEquals(StatusClass.REDIRECTION, statusLine.getStatusClass());

        statusLine = new StatusLine(new BasicHttpResponse(409, "Conflict"));
        Assertions.assertEquals(StatusClass.CLIENT_ERROR, statusLine.getStatusClass());

        statusLine = new StatusLine(new BasicHttpResponse(502, "Bad Gateway"));
        Assertions.assertEquals(StatusClass.SERVER_ERROR, statusLine.getStatusClass());

        statusLine = new StatusLine(new BasicHttpResponse(999, "Not a status"));
        Assertions.assertEquals(StatusClass.OTHER, statusLine.getStatusClass());
    }

    @Test
    public void testGetStatusShorthand() {
        StatusLine statusLine = new StatusLine(new BasicHttpResponse(100, "Continue"));
        Assertions.assertTrue(statusLine.isInformational());
        Assertions.assertFalse(statusLine.isSuccessful());
        Assertions.assertFalse(statusLine.isError());

        statusLine = new StatusLine(new BasicHttpResponse(200, "OK"));
        Assertions.assertTrue(statusLine.isSuccessful());
        Assertions.assertFalse(statusLine.isRedirection());
        Assertions.assertFalse(statusLine.isError());

        statusLine = new StatusLine(new BasicHttpResponse(302, "Found"));
        Assertions.assertTrue(statusLine.isRedirection());
        Assertions.assertFalse(statusLine.isClientError());
        Assertions.assertFalse(statusLine.isError());

        statusLine = new StatusLine(new BasicHttpResponse(409, "Conflict"));
        Assertions.assertTrue(statusLine.isClientError());
        Assertions.assertTrue(statusLine.isError());
        Assertions.assertFalse(statusLine.isServerError());

        statusLine = new StatusLine(new BasicHttpResponse(502, "Bad Gateway"));
        Assertions.assertTrue(statusLine.isServerError());
        Assertions.assertTrue(statusLine.isError());
        Assertions.assertFalse(statusLine.isSuccessful());
    }
}

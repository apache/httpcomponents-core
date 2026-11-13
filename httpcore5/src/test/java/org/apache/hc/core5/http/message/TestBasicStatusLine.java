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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link org.apache.hc.core5.http.message.StatusLine}.
 */
class TestBasicStatusLine {

    @Test
    void testGetStatusClass() {
        StatusLine statusLine = new StatusLine(new BasicHttpResponse(100, "Continue"));
        Assertions.assertThat(statusLine.getStatusClass()).isEqualTo(StatusClass.INFORMATIONAL);

        statusLine = new StatusLine(new BasicHttpResponse(200, "OK"));
        Assertions.assertThat(statusLine.getStatusClass()).isEqualTo(StatusClass.SUCCESSFUL);

        statusLine = new StatusLine(new BasicHttpResponse(302, "Found"));
        Assertions.assertThat(statusLine.getStatusClass()).isEqualTo(StatusClass.REDIRECTION);

        statusLine = new StatusLine(new BasicHttpResponse(409, "Conflict"));
        Assertions.assertThat(statusLine.getStatusClass()).isEqualTo(StatusClass.CLIENT_ERROR);

        statusLine = new StatusLine(new BasicHttpResponse(502, "Bad Gateway"));
        Assertions.assertThat(statusLine.getStatusClass()).isEqualTo(StatusClass.SERVER_ERROR);

        statusLine = new StatusLine(new BasicHttpResponse(999, "Not a status"));
        Assertions.assertThat(statusLine.getStatusClass()).isEqualTo(StatusClass.OTHER);
    }

    @Test
    void testGetStatusShorthand() {
        StatusLine statusLine = new StatusLine(new BasicHttpResponse(100, "Continue"));
        Assertions.assertThat(statusLine.isInformational()).isTrue();
        Assertions.assertThat(statusLine.isSuccessful()).isFalse();
        Assertions.assertThat(statusLine.isError()).isFalse();

        statusLine = new StatusLine(new BasicHttpResponse(200, "OK"));
        Assertions.assertThat(statusLine.isSuccessful()).isTrue();
        Assertions.assertThat(statusLine.isRedirection()).isFalse();
        Assertions.assertThat(statusLine.isError()).isFalse();

        statusLine = new StatusLine(new BasicHttpResponse(302, "Found"));
        Assertions.assertThat(statusLine.isRedirection()).isTrue();
        Assertions.assertThat(statusLine.isClientError()).isFalse();
        Assertions.assertThat(statusLine.isError()).isFalse();

        statusLine = new StatusLine(new BasicHttpResponse(409, "Conflict"));
        Assertions.assertThat(statusLine.isClientError()).isTrue();
        Assertions.assertThat(statusLine.isError()).isTrue();
        Assertions.assertThat(statusLine.isServerError()).isFalse();

        statusLine = new StatusLine(new BasicHttpResponse(502, "Bad Gateway"));
        Assertions.assertThat(statusLine.isServerError()).isTrue();
        Assertions.assertThat(statusLine.isError()).isTrue();
        Assertions.assertThat(statusLine.isSuccessful()).isFalse();
    }
}

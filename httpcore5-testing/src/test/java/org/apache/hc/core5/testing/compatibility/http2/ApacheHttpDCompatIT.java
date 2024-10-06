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

package org.apache.hc.core5.testing.compatibility.http2;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.testing.compatibility.ContainerImages;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class ApacheHttpDCompatIT extends AbstractHttp2CompatIT {

    @Container
    static final GenericContainer<?> CONTAINER = ContainerImages.apacheHttpD();

    HttpHost targetHost() {
        return new HttpHost("http",
                CONTAINER.getHost(),
                CONTAINER.getMappedPort(ContainerImages.HTTP_PORT));
    }

    @Override
    @Test
    @Disabled
    void test_request_execution_with_push() throws Exception {
        super.test_request_execution_with_push();
    }

    @AfterAll
    static void cleanup() {
        CONTAINER.close();
    }

}

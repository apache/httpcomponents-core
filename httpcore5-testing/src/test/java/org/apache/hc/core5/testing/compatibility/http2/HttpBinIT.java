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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HttpBinIT {
    private H2CompatibilityTest h2CompatibilityTest;

    @Before
    public void start() throws Exception {
        h2CompatibilityTest = new H2CompatibilityTest();
        h2CompatibilityTest.start();
    }

    @After
    public void shutdown() throws Exception {
        h2CompatibilityTest.shutdown();
    }

    @Test
    public void executeHttpBin() throws Exception {
        h2CompatibilityTest.executeHttpBin(new HttpHost("http", "localhost", 8082));
    }
}

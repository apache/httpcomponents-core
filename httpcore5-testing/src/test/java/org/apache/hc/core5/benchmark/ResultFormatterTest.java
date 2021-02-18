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
package org.apache.hc.core5.benchmark;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.apache.hc.core5.http.HttpVersion;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;

public class ResultFormatterTest {

    @Test
    public void testBasics() throws Exception {
        final Results results = new Results(
                "TestServer/1.1",
                HttpVersion.HTTP_1_1,
                "localhost",
                8080,
                "/index.html",
                2924,
                5,
                3399,
                20000,
                0,
                20000,
                62640000,
                0,
                50000000);
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ResultFormatter.print(new PrintStream(buf, true, StandardCharsets.US_ASCII.name()), results);
        MatcherAssert.assertThat(new String(buf.toByteArray(), StandardCharsets.US_ASCII).replace("\r\n", "\n"),
                CoreMatchers.equalTo(
                "Server Software:\t\tTestServer/1.1\n" +
                        "Protocol version:\t\tHTTP/1.1\n" +
                        "Server Hostname:\t\tlocalhost\n" +
                        "Server Port:\t\t\t8080\n" +
                        "Document Path:\t\t\t/index.html\n" +
                        "Document Length:\t\t2924 bytes\n" +
                        "\n" +
                        "Concurrency Level:\t\t5\n" +
                        "Time taken for tests:\t3.399000 seconds\n" +
                        "Complete requests:\t\t20000\n" +
                        "Failed requests:\t\t0\n" +
                        "Kept alive:\t\t\t\t20000\n" +
                        "Total transferred:\t\t62640000 bytes\n" +
                        "Content transferred:\t50000000 bytes\n" +
                        "Requests per second:\t5,884.08 [#/sec] (mean)\n" +
                        "Time per request:\t\t0.850 [ms] (mean)\n" +
                        "Time per request:\t\t0.170 [ms] (mean, across all concurrent requests)\n" +
                        "Transfer rate:\t\t\t17,997.02 [Kbytes/sec] received\n"
        ));
     }

}

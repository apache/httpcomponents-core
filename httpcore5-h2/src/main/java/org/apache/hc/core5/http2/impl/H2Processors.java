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
package org.apache.hc.core5.http2.impl;

import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessorBuilder;
import org.apache.hc.core5.http.protocol.RequestExpectContinue;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.apache.hc.core5.http.protocol.ResponseDate;
import org.apache.hc.core5.http.protocol.ResponseServer;
import org.apache.hc.core5.http2.protocol.H2RequestConnControl;
import org.apache.hc.core5.http2.protocol.H2RequestContent;
import org.apache.hc.core5.http2.protocol.H2RequestTargetHost;
import org.apache.hc.core5.http2.protocol.H2RequestValidateHost;
import org.apache.hc.core5.http2.protocol.H2ResponseConnControl;
import org.apache.hc.core5.http2.protocol.H2ResponseContent;
import org.apache.hc.core5.util.TextUtils;
import org.apache.hc.core5.util.VersionInfo;

/**
 * @since 5.0
 */
public final class H2Processors {

    private final static String SOFTWARE = "Apache-HttpCore";

    public static HttpProcessorBuilder customServer(final String serverInfo) {
        return HttpProcessorBuilder.create()
                .addAll(
                        new ResponseDate(),
                        new ResponseServer(!TextUtils.isBlank(serverInfo) ? serverInfo :
                                VersionInfo.getSoftwareInfo(SOFTWARE, "org.apache.hc.core5", H2Processors.class)),
                        new H2ResponseContent(),
                        new H2ResponseConnControl())
                .addAll(
                        new H2RequestValidateHost());
    }

    public static HttpProcessor server(final String serverInfo) {
        return customServer(serverInfo).build();
    }

    public static HttpProcessor server() {
        return customServer(null).build();
    }

    public static HttpProcessorBuilder customClient(final String agentInfo) {
        return HttpProcessorBuilder.create()
                .addAll(
                        new H2RequestContent(),
                        new H2RequestTargetHost(),
                        new H2RequestConnControl(),
                        new RequestUserAgent(!TextUtils.isBlank(agentInfo) ? agentInfo :
                                VersionInfo.getSoftwareInfo(SOFTWARE, "org.apache.hc.core5", HttpProcessors.class)),
                        new RequestExpectContinue());
    }

    public static HttpProcessor client(final String agentInfo) {
        return customClient(agentInfo).build();
    }

    public static HttpProcessor client() {
        return customClient(null).build();
    }

}

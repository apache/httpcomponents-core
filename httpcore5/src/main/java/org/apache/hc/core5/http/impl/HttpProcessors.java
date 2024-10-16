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
package org.apache.hc.core5.http.impl;

import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessorBuilder;
import org.apache.hc.core5.http.protocol.RequestConformance;
import org.apache.hc.core5.http.protocol.RequestConnControl;
import org.apache.hc.core5.http.protocol.RequestContent;
import org.apache.hc.core5.http.protocol.RequestExpectContinue;
import org.apache.hc.core5.http.protocol.RequestTE;
import org.apache.hc.core5.http.protocol.RequestTargetHost;
import org.apache.hc.core5.http.protocol.RequestUserAgent;
import org.apache.hc.core5.http.protocol.RequestValidateHost;
import org.apache.hc.core5.http.protocol.ResponseConformance;
import org.apache.hc.core5.http.protocol.ResponseConnControl;
import org.apache.hc.core5.http.protocol.ResponseContent;
import org.apache.hc.core5.http.protocol.ResponseDate;
import org.apache.hc.core5.http.protocol.ResponseServer;
import org.apache.hc.core5.util.TextUtils;
import org.apache.hc.core5.util.VersionInfo;

/**
 * Factory class for standard {@link HttpProcessor} instances.
 *
 * @since 5.0
 */
public final class HttpProcessors {

    private final static String SOFTWARE = "Apache-HttpCore";

    /**
     * Creates {@link HttpProcessorBuilder} initialized with default protocol interceptors
     * for server side HTTP/1.1 processing.
     *
     * @param serverInfo the server info text or {@code null} for default.
     * @return the processor builder.
     */
    public static HttpProcessorBuilder customServer(final String serverInfo) {
        return HttpProcessorBuilder.create()
                .addAll(
                        ResponseConformance.INSTANCE,
                        ResponseDate.INSTANCE,
                        new ResponseServer(!TextUtils.isBlank(serverInfo) ? serverInfo :
                                VersionInfo.getSoftwareInfo(SOFTWARE, "org.apache.hc.core5", HttpProcessors.class)),
                        ResponseContent.INSTANCE,
                        ResponseConnControl.INSTANCE)
                .addAll(
                        RequestValidateHost.INSTANCE,
                        RequestConformance.INSTANCE);
    }

    /**
     * Creates {@link HttpProcessor} initialized with default protocol interceptors
     * for server side HTTP/1.1 processing.
     *
     * @param serverInfo the server info text or {@code null} for default.
     * @return the processor.
     */
    public static HttpProcessor server(final String serverInfo) {
        return customServer(serverInfo).build();
    }

    /**
     * Creates {@link HttpProcessor} initialized with default protocol interceptors
     * for server side HTTP/1.1 processing.
     *
     * @return the processor.
     */
    public static HttpProcessor server() {
        return customServer(null).build();
    }

    /**
     * Creates {@link HttpProcessorBuilder} initialized with default protocol interceptors
     * for client side HTTP/1.1 processing.
     *
     * @param agentInfo the agent info text or {@code null} for default.
     * @return the processor builder.
     */
    public static HttpProcessorBuilder customClient(final String agentInfo) {
        return HttpProcessorBuilder.create()
                .addAll(
                        RequestTargetHost.INSTANCE,
                        RequestContent.INSTANCE,
                        RequestConnControl.INSTANCE,
                        new RequestUserAgent(!TextUtils.isBlank(agentInfo) ? agentInfo :
                                VersionInfo.getSoftwareInfo(SOFTWARE, "org.apache.hc.core5", HttpProcessors.class)),
                        RequestExpectContinue.INSTANCE);
    }

    /**
     * Creates an {@link HttpProcessorBuilder} initialized with strict protocol interceptors
     * for client-side HTTP/1.1 processing.
     * <p>
     * This configuration enforces stricter validation and processing of client requests,
     * ensuring compliance with the HTTP protocol. It includes interceptors for handling
     * target hosts, content, connection controls, and TE header validation, among others.
     * The user agent can be customized using the provided {@code agentInfo} parameter.
     *
     * @param agentInfo the user agent info to be included in the {@code User-Agent} header.
     *                  If {@code null} or blank, a default value will be used.
     * @return the {@link HttpProcessorBuilder} configured with strict client-side interceptors.
     * @since 5.4
     */
    public static HttpProcessorBuilder strictClient(final String agentInfo) {
        return HttpProcessorBuilder.create()
                .addAll(
                        RequestTargetHost.INSTANCE,
                        RequestContent.INSTANCE,
                        RequestConnControl.INSTANCE,
                        RequestTE.INSTANCE,
                        new RequestUserAgent(!TextUtils.isBlank(agentInfo) ? agentInfo :
                                VersionInfo.getSoftwareInfo(SOFTWARE, "org.apache.hc.core5", HttpProcessors.class)),
                        RequestExpectContinue.INSTANCE);
    }

    /**
     * Creates {@link HttpProcessorBuilder} initialized with default protocol interceptors
     * for client side HTTP/1.1 processing.
     *
     * @param agentInfo the agent info text or {@code null} for default.
     * @return the processor builder.
     * @since 5.4
     */
    public static HttpProcessorBuilder customClient(final String agentInfo, final boolean strict) {
        return strict ? strictClient(agentInfo) : customClient(agentInfo);
    }

    /**
     * Creates {@link HttpProcessor} initialized with default protocol interceptors
     * for client side HTTP/1.1 processing.
     *
     * @param agentInfo the agent info text or {@code null} for default.
     * @return the processor.
     */
    public static HttpProcessor client(final String agentInfo) {
        return client(agentInfo, false);
    }

    /**
     * Creates {@link HttpProcessor} initialized with default protocol interceptors
     * for client side HTTP/1.1 processing.
     *
     * @return the processor.
     */
    public static HttpProcessor client() {
        return client(null);
    }

    /**
     * Creates an {@link HttpProcessor} for client-side HTTP/2 processing.
     * This method allows the option to include strict protocol interceptors.
     *
     * @param agentInfo the agent info text or {@code null} for default.
     * @param strict    if {@code true}, strict protocol interceptors will be added, including the {@code TE} header validation.
     * @return the configured HTTP processor.
     * @since 5.4
     */
    public static HttpProcessor client(final String agentInfo, final boolean strict) {
        return customClient(agentInfo, strict).build();
    }

    /**
     * Creates an {@link HttpProcessor} for client-side HTTP/2 processing
     * with strict protocol validation interceptors by default.
     * <p>
     * Strict validation includes additional checks such as validating the {@code TE} header.
     *
     * @return the configured strict HTTP processor.
     * @since 5.4
     */
    public static HttpProcessor clientStrict() {
        return customClient(null, true).build();
    }

    /**
     * Creates an {@link HttpProcessor} for client-side HTTP/2 processing
     * with strict protocol validation interceptors, using the specified agent information.
     * <p>
     * Strict validation includes additional checks such as validating the {@code TE} header.
     *
     * @param agentInfo the agent info text or {@code null} for default.
     * @return the configured strict HTTP processor.
     * @since 5.4
     */
    public static HttpProcessor clientStrict(final String agentInfo) {
        return customClient(agentInfo, true).build();
    }

}

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

import java.io.File;
import java.net.URI;
import java.util.Arrays;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.Timeout;

public class BenchmarkConfig {

    private final URI uri;
    private final int requests;
    private final int concurrencyLevel;
    private final boolean keepAlive;
    private final int verbosity;
    private final boolean headInsteadOfGet;
    private final boolean useHttp1_0;
    private final ContentType contentType;
    private final String[] headers;
    private final Timeout socketTimeout;
    private final String method;
    private final boolean useChunking;
    private final boolean useExpectContinue;
    private final boolean useAcceptGZip;
    private final File payloadFile;
    private final String payloadText;
    private final String soapAction;
    private final int timeLimit;

    private final boolean disableSSLVerification;
    private final String trustStorePath;
    private final String identityStorePath;
    private final String trustStorePassword;
    private final String identityStorePassword;

    private BenchmarkConfig(final URI uri,
                            final int requests,
                            final int concurrencyLevel,
                            final boolean keepAlive, final int verbosity,
                            final boolean headInsteadOfGet,
                            final boolean useHttp1_0,
                            final ContentType contentType,
                            final String[] headers,
                            final Timeout socketTimeout,
                            final String method,
                            final boolean useChunking,
                            final boolean useExpectContinue,
                            final boolean useAcceptGZip,
                            final File payloadFile,
                            final String payloadText,
                            final String soapAction,
                            final int timeLimit,
                            final boolean disableSSLVerification,
                            final String trustStorePath,
                            final String identityStorePath,
                            final String trustStorePassword,
                            final String identityStorePassword) {
        this.uri = uri;
        this.requests = requests;
        this.concurrencyLevel = concurrencyLevel;
        this.keepAlive = keepAlive;
        this.verbosity = verbosity;
        this.headInsteadOfGet = headInsteadOfGet;
        this.useHttp1_0 = useHttp1_0;
        this.contentType = contentType;
        this.headers = headers;
        this.socketTimeout = socketTimeout;
        this.method = method;
        this.useChunking = useChunking;
        this.useExpectContinue = useExpectContinue;
        this.useAcceptGZip = useAcceptGZip;
        this.payloadFile = payloadFile;
        this.payloadText = payloadText;
        this.soapAction = soapAction;
        this.timeLimit = timeLimit;
        this.disableSSLVerification = disableSSLVerification;
        this.trustStorePath = trustStorePath;
        this.identityStorePath = identityStorePath;
        this.trustStorePassword = trustStorePassword;
        this.identityStorePassword = identityStorePassword;
    }

    public URI getUri() {
        return uri;
    }

    public int getRequests() {
        return requests;
    }

    public int getConcurrencyLevel() {
        return concurrencyLevel;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public int getVerbosity() {
        return verbosity;
    }

    public boolean isHeadInsteadOfGet() {
        return headInsteadOfGet;
    }

    public boolean isUseHttp1_0() {
        return useHttp1_0;
    }

    public File getPayloadFile() {
        return payloadFile;
    }

    public ContentType getContentType() {
        return contentType;
    }

    public String[] getHeaders() {
        return headers != null ? headers.clone() : null;
    }

    public Timeout getSocketTimeout() {
        return socketTimeout;
    }

    public String getMethod() {
        return method;
    }

    public boolean isUseChunking() {
        return useChunking;
    }

    public boolean isUseExpectContinue() {
        return useExpectContinue;
    }

    public boolean isUseAcceptGZip() {
        return useAcceptGZip;
    }

    public String getPayloadText() {
        return payloadText;
    }

    public String getSoapAction() {
        return soapAction;
    }

    public boolean isDisableSSLVerification() {
        return disableSSLVerification;
    }

    public String getTrustStorePath() {
        return trustStorePath;
    }

    public String getIdentityStorePath() {
        return identityStorePath;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    public String getIdentityStorePassword() {
        return identityStorePassword;
    }

    public int getTimeLimit() {
        return timeLimit;
    }

    @Override
    public String toString() {
        return "[" +
                "uri=" + uri +
                ", requests=" + requests +
                ", concurrencyLevel=" + concurrencyLevel +
                ", keepAlive=" + keepAlive +
                ", verbosity=" + verbosity +
                ", headInsteadOfGet=" + headInsteadOfGet +
                ", useHttp1_0=" + useHttp1_0 +
                ", contentType=" + contentType +
                ", headers=" + Arrays.toString(headers) +
                ", socketTimeout=" + socketTimeout +
                ", method='" + method + '\'' +
                ", useChunking=" + useChunking +
                ", useExpectContinue=" + useExpectContinue +
                ", useAcceptGZip=" + useAcceptGZip +
                ", payloadFile=" + payloadFile +
                ", payloadText='" + payloadText + '\'' +
                ", soapAction='" + soapAction + '\'' +
                ", timeLimit=" + timeLimit +
                ", disableSSLVerification=" + disableSSLVerification +
                ", trustStorePath='" + trustStorePath + '\'' +
                ", identityStorePath='" + identityStorePath + '\'' +
                ", trustStorePassword='" + trustStorePassword + '\'' +
                ", identityStorePassword='" + identityStorePassword + '\'' +
                ']';
    }

    public static BenchmarkConfig.Builder custom() {
        return new BenchmarkConfig.Builder();
    }

    public static BenchmarkConfig.Builder copy(final BenchmarkConfig config) {
        Args.notNull(config, "Socket config");
        return new Builder()
                .setUri(config.getUri())
                .setRequests(config.getRequests())
                .setThreads(config.getConcurrencyLevel())
                .setKeepAlive(config.isKeepAlive())
                .setVerbosity(config.getVerbosity())
                .setHeadInsteadOfGet(config.isHeadInsteadOfGet())
                .setUseHttp1_0(config.isUseHttp1_0())
                .setContentType(config.getContentType())
                .setHeaders(config.getHeaders())
                .setSocketTimeout(config.getSocketTimeout())
                .setMethod(config.getMethod())
                .setUseChunking(config.isUseChunking())
                .setUseExpectContinue(config.isUseExpectContinue())
                .setUseAcceptGZip(config.isUseAcceptGZip())
                .setPayloadFile(config.getPayloadFile())
                .setPayloadText(config.getPayloadText())
                .setSoapAction(config.getSoapAction())
                .setTimeLimit(config.getTimeLimit())
                .setDisableSSLVerification(config.isDisableSSLVerification())
                .setTrustStorePath(config.getTrustStorePath())
                .setIdentityStorePath(config.getIdentityStorePath())
                .setTrustStorePassword(config.getTrustStorePassword())
                .setIdentityStorePassword(config.getIdentityStorePassword());
    }


    public static class Builder {

        private URI uri;
        private int requests;
        private int threads;
        private boolean keepAlive;
        private int verbosity;
        private boolean headInsteadOfGet;
        private boolean useHttp1_0;
        private ContentType contentType;
        private String[] headers;
        private Timeout socketTimeout;
        private String method;
        private boolean useChunking;
        private boolean useExpectContinue;
        private boolean useAcceptGZip;
        private File payloadFile;
        private String payloadText;
        private String soapAction;
        private int timeLimit = -1;

        private boolean disableSSLVerification = true;
        private String trustStorePath;
        private String identityStorePath;
        private String trustStorePassword;
        private String identityStorePassword;

        public Builder() {
            super();
            this.requests = 1;
            this.threads = 1;
            this.keepAlive = false;
            this.verbosity = 0;
            this.headInsteadOfGet = false;
            this.useHttp1_0 = false;
            this.socketTimeout = Timeout.ofSeconds(60);
        }

        public Builder setUri(final URI uri) {
            this.uri = uri;
            return this;
        }

        public Builder setRequests(final int requests) {
            this.requests = requests;
            return this;
        }

        public Builder setThreads(final int threads) {
            this.threads = threads;
            return this;
        }

        public Builder setKeepAlive(final boolean keepAlive) {
            this.keepAlive = keepAlive;
            return this;
        }

        public Builder setVerbosity(final int verbosity) {
            this.verbosity = verbosity;
            return this;
        }

        public Builder setHeadInsteadOfGet(final boolean headInsteadOfGet) {
            this.headInsteadOfGet = headInsteadOfGet;
            return this;
        }

        public Builder setUseHttp1_0(final boolean useHttp1_0) {
            this.useHttp1_0 = useHttp1_0;
            return this;
        }

        public Builder setContentType(final ContentType contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder setHeaders(final String[] headers) {
            this.headers = headers;
            return this;
        }

        public Builder setSocketTimeout(final Timeout socketTimeout) {
            this.socketTimeout = socketTimeout;
            return this;
        }

        public Builder setMethod(final String method) {
            this.method = method;
            return this;
        }

        public Builder setUseChunking(final boolean useChunking) {
            this.useChunking = useChunking;
            return this;
        }

        public Builder setUseExpectContinue(final boolean useExpectContinue) {
            this.useExpectContinue = useExpectContinue;
            return this;
        }

        public Builder setUseAcceptGZip(final boolean useAcceptGZip) {
            this.useAcceptGZip = useAcceptGZip;
            return this;
        }

        public Builder setPayloadFile(final File payloadFile) {
            this.payloadFile = payloadFile;
            return this;
        }

        public Builder setPayloadText(final String payloadText) {
            this.payloadText = payloadText;
            return this;
        }

        public Builder setSoapAction(final String soapAction) {
            this.soapAction = soapAction;
            return this;
        }

        public Builder setTimeLimit(final int timeLimit) {
            this.timeLimit = timeLimit;
            return this;
        }

        public Builder setDisableSSLVerification(final boolean disableSSLVerification) {
            this.disableSSLVerification = disableSSLVerification;
            return this;
        }

        public Builder setTrustStorePath(final String trustStorePath) {
            this.trustStorePath = trustStorePath;
            return this;
        }

        public Builder setIdentityStorePath(final String identityStorePath) {
            this.identityStorePath = identityStorePath;
            return this;
        }

        public Builder setTrustStorePassword(final String trustStorePassword) {
            this.trustStorePassword = trustStorePassword;
            return this;
        }

        public Builder setIdentityStorePassword(final String identityStorePassword) {
            this.identityStorePassword = identityStorePassword;
            return this;
        }

        public BenchmarkConfig build() {
            return new BenchmarkConfig(uri, requests, threads, keepAlive, verbosity, headInsteadOfGet, useHttp1_0,
                    contentType, headers, socketTimeout, method, useChunking, useExpectContinue, useAcceptGZip,
                    payloadFile, payloadText, soapAction, timeLimit, disableSSLVerification, trustStorePath,
                    identityStorePath, trustStorePassword, identityStorePassword);
        }

    }

}

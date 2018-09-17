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
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

public class BenchmarkConfig {

    private final URI uri;
    private final int requests;
    private final int concurrencyLevel;
    private final TimeValue timeLimit;
    private final Timeout socketTimeout;
    private final File payloadFile;
    private final ContentType contentType;
    private final int verbosity;
    private final boolean headInsteadOfGet;
    private final String[] headers;
    private final boolean keepAlive;
    private final String method;

    private final boolean useChunking;
    private final boolean useExpectContinue;
    private final boolean useAcceptGZip;
    private final String payloadText;
    private final String soapAction;
    private final boolean forceHttp2;
    private final boolean disableSSLVerification;
    private final String trustStorePath;
    private final String identityStorePath;
    private final String trustStorePassword;
    private final String identityStorePassword;

    private BenchmarkConfig(final URI uri,
                            final int requests,
                            final int concurrencyLevel,
                            final TimeValue timeLimit,
                            final Timeout socketTimeout,
                            final File payloadFile,
                            final ContentType contentType,
                            final int verbosity,
                            final boolean headInsteadOfGet,
                            final String[] headers,
                            final boolean keepAlive,
                            final String method,
                            final boolean useChunking,
                            final boolean useExpectContinue,
                            final boolean useAcceptGZip,
                            final String payloadText,
                            final String soapAction,
                            final boolean forceHttp2,
                            final boolean disableSSLVerification,
                            final String trustStorePath,
                            final String identityStorePath,
                            final String trustStorePassword,
                            final String identityStorePassword) {
        this.uri = uri;
        this.requests = requests;
        this.concurrencyLevel = concurrencyLevel;
        this.timeLimit = timeLimit;
        this.socketTimeout = socketTimeout;
        this.payloadFile = payloadFile;
        this.contentType = contentType;
        this.verbosity = verbosity;
        this.headInsteadOfGet = headInsteadOfGet;
        this.headers = headers;
        this.keepAlive = keepAlive;
        this.method = method;
        this.useChunking = useChunking;
        this.useExpectContinue = useExpectContinue;
        this.useAcceptGZip = useAcceptGZip;
        this.payloadText = payloadText;
        this.soapAction = soapAction;
        this.forceHttp2 = forceHttp2;
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

    public boolean isForceHttp2() {
        return forceHttp2;
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

    public TimeValue getTimeLimit() {
        return timeLimit;
    }

    @Override
    public String toString() {
        return "[" +
                "uri=" + uri +
                ", requests=" + requests +
                ", concurrencyLevel=" + concurrencyLevel +
                ", timeLimit=" + timeLimit +
                ", socketTimeout=" + socketTimeout +
                ", payloadFile=" + payloadFile +
                ", contentType=" + contentType +
                ", verbosity=" + verbosity +
                ", headInsteadOfGet=" + headInsteadOfGet +
                ", headers=" + Arrays.toString(headers) +
                ", keepAlive=" + keepAlive +
                ", method='" + method + '\'' +
                ", useChunking=" + useChunking +
                ", useExpectContinue=" + useExpectContinue +
                ", useAcceptGZip=" + useAcceptGZip +
                ", payloadText='" + payloadText + '\'' +
                ", soapAction='" + soapAction + '\'' +
                ", forceHttp2=" + forceHttp2+
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
                .setConcurrencyLevel(config.getConcurrencyLevel())
                .setTimeLimit(config.getTimeLimit())
                .setSocketTimeout(config.getSocketTimeout())
                .setPayloadFile(config.getPayloadFile())
                .setContentType(config.getContentType())
                .setVerbosity(config.getVerbosity())
                .setHeadInsteadOfGet(config.isHeadInsteadOfGet())
                .setHeaders(config.getHeaders())
                .setKeepAlive(config.isKeepAlive())
                .setMethod(config.getMethod())
                .setUseChunking(config.isUseChunking())
                .setUseExpectContinue(config.isUseExpectContinue())
                .setUseAcceptGZip(config.isUseAcceptGZip())
                .setPayloadText(config.getPayloadText())
                .setSoapAction(config.getSoapAction())
                .setForceHttp2(config.isForceHttp2())
                .setDisableSSLVerification(config.isDisableSSLVerification())
                .setTrustStorePath(config.getTrustStorePath())
                .setIdentityStorePath(config.getIdentityStorePath())
                .setTrustStorePassword(config.getTrustStorePassword())
                .setIdentityStorePassword(config.getIdentityStorePassword());
    }


    public static class Builder {

        private URI uri;
        private int requests;
        private int concurrencyLevel;
        private TimeValue timeLimit;
        private Timeout socketTimeout;
        private File payloadFile;
        private ContentType contentType;
        private int verbosity;
        private boolean headInsteadOfGet;
        private String[] headers;
        private boolean keepAlive;
        private String method;

        private boolean useChunking;
        private boolean useExpectContinue;
        private boolean useAcceptGZip;
        private String payloadText;
        private String soapAction;
        private boolean forceHttp2;
        private boolean disableSSLVerification;
        private String trustStorePath;
        private String identityStorePath;
        private String trustStorePassword;
        private String identityStorePassword;

        public Builder() {
            super();
            this.requests = 1;
            this.concurrencyLevel = 1;
            this.keepAlive = false;
            this.verbosity = 0;
            this.headInsteadOfGet = false;
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

        public Builder setConcurrencyLevel(final int concurrencyLevel) {
            this.concurrencyLevel = concurrencyLevel;
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

        public Builder setTimeLimit(final TimeValue timeLimit) {
            this.timeLimit = timeLimit;
            return this;
        }

        public Builder setForceHttp2(final boolean forceHttp2) {
            this.forceHttp2 = forceHttp2;
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
            return new BenchmarkConfig(
                    uri,
                    requests,
                    concurrencyLevel,
                    timeLimit,
                    socketTimeout,
                    payloadFile,
                    contentType,
                    verbosity,
                    headInsteadOfGet,
                    headers,
                    keepAlive,
                    method,
                    useChunking,
                    useExpectContinue,
                    useAcceptGZip,
                    payloadText,
                    soapAction,
                    forceHttp2,
                    disableSSLVerification,
                    trustStorePath,
                    identityStorePath,
                    trustStorePassword,
                    identityStorePassword);
        }

    }

}

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
package org.apache.hc.core5.http.benchmark;

import java.io.File;
import java.net.URL;

public class Config {

    private URL url;
    private int requests;
    private int threads;
    private boolean keepAlive;
    private int verbosity;
    private boolean headInsteadOfGet;
    private boolean useHttp1_0;
    private String contentType;
    private String[] headers;
    private int socketTimeout;
    private String method = "GET";
    private boolean useChunking;
    private boolean useExpectContinue;
    private boolean useAcceptGZip;
    private File payloadFile = null;
    private String payloadText = null;
    private String soapAction = null;

    private boolean disableSSLVerification = true;
    private String trustStorePath = null;
    private String identityStorePath = null;
    private String trustStorePassword = null;
    private String identityStorePassword = null;

    public Config() {
        super();
        this.url = null;
        this.requests = 1;
        this.threads = 1;
        this.keepAlive = false;
        this.verbosity = 0;
        this.headInsteadOfGet = false;
        this.useHttp1_0 = false;
        this.payloadFile = null;
        this.payloadText = null;
        this.contentType = null;
        this.headers = null;
        this.socketTimeout = 60000;
    }

    public URL getUrl() {
        return url;
    }

    public void setUrl(final URL url) {
        this.url = url;
    }

    public int getRequests() {
        return requests;
    }

    public void setRequests(final int requests) {
        this.requests = requests;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(final int threads) {
        this.threads = threads;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(final boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public int getVerbosity() {
        return verbosity;
    }

    public void setVerbosity(final int verbosity) {
        this.verbosity = verbosity;
    }

    public boolean isHeadInsteadOfGet() {
        return headInsteadOfGet;
    }

    public void setHeadInsteadOfGet(final boolean headInsteadOfGet) {
        this.headInsteadOfGet = headInsteadOfGet;
        this.method = "HEAD";
    }

    public boolean isUseHttp1_0() {
        return useHttp1_0;
    }

    public void setUseHttp1_0(final boolean useHttp1_0) {
        this.useHttp1_0 = useHttp1_0;
    }

    public File getPayloadFile() {
        return payloadFile;
    }

    public void setPayloadFile(final File payloadFile) {
        this.payloadFile = payloadFile;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(final String contentType) {
        this.contentType = contentType;
    }

    public String[] getHeaders() {
        return headers;
    }

    public void setHeaders(final String[] headers) {
        this.headers = headers;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(final int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public void setMethod(final String method) {
        this.method = method;
    }

    public void setUseChunking(final boolean useChunking) {
        this.useChunking = useChunking;
    }

    public void setUseExpectContinue(final boolean useExpectContinue) {
        this.useExpectContinue = useExpectContinue;
    }

    public void setUseAcceptGZip(final boolean useAcceptGZip) {
        this.useAcceptGZip = useAcceptGZip;
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

    public void setPayloadText(final String payloadText) {
        this.payloadText = payloadText;
    }

    public void setSoapAction(final String soapAction) {
        this.soapAction = soapAction;
    }

    public void setDisableSSLVerification(final boolean disableSSLVerification) {
        this.disableSSLVerification = disableSSLVerification;
    }

    public void setTrustStorePath(final String trustStorePath) {
        this.trustStorePath = trustStorePath;
    }

    public void setIdentityStorePath(final String identityStorePath) {
        this.identityStorePath = identityStorePath;
    }

    public void setTrustStorePassword(final String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    public void setIdentityStorePassword(final String identityStorePassword) {
        this.identityStorePassword = identityStorePassword;
    }

    public Config copy() {
        final Config copy = new Config();
        copy.url = this.url;
        copy.requests = this.requests;
        copy.threads = this.threads;
        copy.keepAlive = this.keepAlive;
        copy.verbosity = this.verbosity;
        copy.headInsteadOfGet = this.headInsteadOfGet;
        copy.useHttp1_0 = this.useHttp1_0;
        copy.contentType = this.contentType;
        copy.headers = this.headers;
        copy.socketTimeout = this.socketTimeout;
        copy.method = this.method;
        copy.useChunking = this.useChunking;
        copy.useExpectContinue = this.useExpectContinue;
        copy.useAcceptGZip = this.useAcceptGZip;
        copy.payloadFile = this.payloadFile;
        copy.payloadText = this.payloadText;
        copy.soapAction = this.soapAction;

        copy.disableSSLVerification = this.disableSSLVerification;
        copy.trustStorePath = this.trustStorePath;
        copy.identityStorePath = this.identityStorePath;
        copy.trustStorePassword = this.trustStorePassword;
        copy.identityStorePassword = this.identityStorePassword;
        return copy;
    }

}

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
package org.apache.http.benchmark;

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

    public void setUrl(URL url) {
        this.url = url;
    }

    public int getRequests() {
        return requests;
    }

    public void setRequests(int requests) {
        this.requests = requests;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public int getVerbosity() {
        return verbosity;
    }

    public void setVerbosity(int verbosity) {
        this.verbosity = verbosity;
    }

    public boolean isHeadInsteadOfGet() {
        return headInsteadOfGet;
    }

    public void setHeadInsteadOfGet(boolean headInsteadOfGet) {
        this.headInsteadOfGet = headInsteadOfGet;
        this.method = "HEAD";
    }

    public boolean isUseHttp1_0() {
        return useHttp1_0;
    }

    public void setUseHttp1_0(boolean useHttp1_0) {
        this.useHttp1_0 = useHttp1_0;
    }

    public File getPayloadFile() {
        return payloadFile;
    }

    public void setPayloadFile(File payloadFile) {
        this.payloadFile = payloadFile;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String[] getHeaders() {
        return headers;
    }

    public void setHeaders(String[] headers) {
        this.headers = headers;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public void setUseChunking(boolean useChunking) {
        this.useChunking = useChunking;
    }

    public void setUseExpectContinue(boolean useExpectContinue) {
        this.useExpectContinue = useExpectContinue;
    }

    public void setUseAcceptGZip(boolean useAcceptGZip) {
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

    public void setPayloadText(String payloadText) {
        this.payloadText = payloadText;
    }

    public void setSoapAction(String soapAction) {
        this.soapAction = soapAction;
    }

    public void setDisableSSLVerification(boolean disableSSLVerification) {
        this.disableSSLVerification = disableSSLVerification;
    }

    public void setTrustStorePath(String trustStorePath) {
        this.trustStorePath = trustStorePath;
    }

    public void setIdentityStorePath(String identityStorePath) {
        this.identityStorePath = identityStorePath;
    }

    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    public void setIdentityStorePassword(String identityStorePassword) {
        this.identityStorePassword = identityStorePassword;
    }
}

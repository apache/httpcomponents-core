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
package org.apache.http.contrib.benchmark;

import java.net.URL;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpVersion;
import org.apache.http.entity.FileEntity;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Main program of the HTTP benchmark.
 *
 *
 * @since 4.0
 */
public class HttpBenchmark {

    private final Config config;
    
    private HttpParams params = null;
    private HttpRequest[] request = null;
    private HttpHost host = null;
    private long contentLength = -1;

    public static void main(String[] args) throws Exception {

        Options options = CommandLineUtils.getOptions();
        CommandLineParser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, args);

        if (args.length == 0 || cmd.hasOption('h') || cmd.getArgs().length != 1) {
            CommandLineUtils.showUsage(options);
            System.exit(1);
        }

        Config config = new Config();
        CommandLineUtils.parseCommandLine(cmd, config);
        
        if (config.getUrl() == null) {
            CommandLineUtils.showUsage(options);
            System.exit(1);
        }
        
        HttpBenchmark httpBenchmark = new HttpBenchmark(config);
        httpBenchmark.execute();
    }
    
    public HttpBenchmark(final Config config) {
        super();
        this.config = config != null ? config : new Config();
    }
    
    private void prepare() {
        // prepare http params
        params = getHttpParams(config.getSocketTimeout(), config.isUseHttp1_0());

        URL url = config.getUrl();
        host = new HttpHost(url.getHost(), url.getPort(), url.getProtocol());

        StringBuilder buffer = new StringBuilder();
        buffer.append(url.getPath());
        if (url.getQuery() != null) {
            buffer.append('?');
            buffer.append(url.getQuery());
        }
        String requestURI = buffer.toString();
        
        // Prepare requests for each thread
        request = new HttpRequest[config.getThreads()];

        if (config.getPostFile() != null) {
            FileEntity entity = new FileEntity(config.getPostFile(), config.getContentType());
            contentLength = entity.getContentLength();
            if (config.getPostFile().length() > 100000) {
                entity.setChunked(true);
            }

            for (int i = 0; i < request.length; i++) {
                BasicHttpEntityEnclosingRequest httppost = 
                    new BasicHttpEntityEnclosingRequest("POST", requestURI);
                httppost.setEntity(entity);
                request[i] = httppost;
            }

        } else if (config.isHeadInsteadOfGet()) {
            for (int i = 0; i < request.length; i++) {
                request[i] = new BasicHttpRequest("HEAD", requestURI);
            }

        } else {
            for (int i = 0; i < request.length; i++) {
                request[i] = new BasicHttpRequest("GET", requestURI);
            }
        }

        if (!config.isKeepAlive()) {
            for (int i = 0; i < request.length; i++) {
                request[i].addHeader(new DefaultHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_CLOSE));
            }
        }

        String[] headers = config.getHeaders();
        if (headers != null) {
            for (int i = 0; i < headers.length; i++) {
                String s = headers[i];
                int pos = s.indexOf(':');
                if (pos != -1) {
                    Header header = new DefaultHeader(s.substring(0, pos).trim(), s.substring(pos + 1));
                    for (int j = 0; j < request.length; j++) {
                        request[j].addHeader(header);
                    }
                }
            }
        }
    }

    public void execute() {

        prepare();

        ThreadPoolExecutor workerPool = new ThreadPoolExecutor(
                config.getThreads(), config.getThreads(), 5, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(),
            new ThreadFactory() {
                
                public Thread newThread(Runnable r) {
                    return new Thread(r, "ClientPool");
                }
                
            });
        workerPool.prestartAllCoreThreads();

        BenchmarkWorker[] workers = new BenchmarkWorker[config.getThreads()];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new BenchmarkWorker(
                    params, 
                    config.getVerbosity(), 
                    request[i], 
                    host, 
                    config.getRequests(), 
                    config.isKeepAlive());
            workerPool.execute(workers[i]);
        }

        while (workerPool.getCompletedTaskCount() < config.getThreads()) {
            Thread.yield();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignore) {
            }
        }

        workerPool.shutdown();
        ResultProcessor.printResults(workers, host, config.getUrl().toString(), contentLength);
    }

    private HttpParams getHttpParams(int socketTimeout, boolean useHttp1_0) {
        HttpParams params = new BasicHttpParams();
        params.setParameter(HttpProtocolParams.PROTOCOL_VERSION,
            useHttp1_0 ? HttpVersion.HTTP_1_0 : HttpVersion.HTTP_1_1)
            .setParameter(HttpProtocolParams.USER_AGENT, "HttpCore-AB/1.1")
            .setBooleanParameter(HttpProtocolParams.USE_EXPECT_CONTINUE, false)
            .setBooleanParameter(HttpConnectionParams.STALE_CONNECTION_CHECK, false)
            .setIntParameter(HttpConnectionParams.SO_TIMEOUT, socketTimeout);
        return params;
    }

}

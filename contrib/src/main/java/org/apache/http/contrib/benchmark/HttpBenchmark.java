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

import java.io.File;
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

    private HttpParams params = null;
    private HttpRequest[] request = null;
    private HttpHost host = null;
    protected int verbosity = 0;
    protected boolean keepAlive = false;
    protected int requests = 1;
    protected int threads = 1;
    protected URL url = null;
    protected File postFile = null;
    protected String contentType = null;
    protected String[] headers = null;
    protected boolean doHeadInsteadOfGet = false;
    private long contentLength = -1;
    protected int socketTimeout = 60000;
    protected boolean useHttp1_0 = false;

    public static void main(String[] args) throws Exception {

        Options options = CommandLineUtils.getOptions();
        CommandLineParser parser = new PosixParser();
        CommandLine cmd = parser.parse(options, args);

        if (args.length == 0 || cmd.hasOption('h') || cmd.getArgs().length != 1) {
            CommandLineUtils.showUsage(options);
            System.exit(1);
        }

        HttpBenchmark httpBenchmark = new HttpBenchmark();
        CommandLineUtils.parseCommandLine(cmd, httpBenchmark);
        httpBenchmark.execute();
    }


    private void prepare() {
        // prepare http params
        params = getHttpParams(socketTimeout, useHttp1_0);

        host = new HttpHost(url.getHost(), url.getPort(), url.getProtocol());

        // Prepare requests for each thread
        request = new HttpRequest[threads];

        if (postFile != null) {
            FileEntity entity = new FileEntity(postFile, contentType);
            contentLength = entity.getContentLength();
            if (postFile.length() > 100000) {
                entity.setChunked(true);
            }

            for (int i = 0; i < threads; i++) {
                BasicHttpEntityEnclosingRequest httppost = 
                    new BasicHttpEntityEnclosingRequest("POST", url.getPath());
                httppost.setEntity(entity);
                request[i] = httppost;
            }

        } else if (doHeadInsteadOfGet) {
            for (int i = 0; i < threads; i++) {
                request[i] = new BasicHttpRequest("HEAD", url.getPath());
            }

        } else {
            for (int i = 0; i < threads; i++) {
                request[i] = new BasicHttpRequest("GET", url.getPath());
            }
        }

        if (!keepAlive) {
            for (int i = 0; i < threads; i++) {
                request[i].addHeader(new DefaultHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_CLOSE));
            }
        }

        if (headers != null) {
            for (int i = 0; i < headers.length; i++) {
                String s = headers[i];
                int pos = s.indexOf(':');
                if (pos != -1) {
                    Header header = new DefaultHeader(s.substring(0, pos).trim(), s.substring(pos + 1));
                    for (int j = 0; j < threads; j++) {
                        request[j].addHeader(header);
                    }
                }
            }
        }
    }

    private void execute() {

        prepare();

        ThreadPoolExecutor workerPool = new ThreadPoolExecutor(
            threads, threads, 5, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(),
            new ThreadFactory() {
                
                public Thread newThread(Runnable r) {
                    return new Thread(r, "ClientPool");
                }
                
            });
        workerPool.prestartAllCoreThreads();

        BenchmarkWorker[] workers = new BenchmarkWorker[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new BenchmarkWorker(
                    params, 
                    verbosity, 
                    request[i], 
                    host, 
                    requests, 
                    keepAlive);
            workerPool.execute(workers[i]);
        }

        while (workerPool.getCompletedTaskCount() < threads) {
            Thread.yield();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignore) {
            }
        }

        workerPool.shutdown();
        ResultProcessor.printResults(workers, host, url.toString(), contentLength);
    }

    private HttpParams getHttpParams(int socketTimeout, boolean useHttp1_0) {
        HttpParams params = new BasicHttpParams();
        params.setParameter(HttpProtocolParams.PROTOCOL_VERSION,
            useHttp1_0 ? HttpVersion.HTTP_1_0 : HttpVersion.HTTP_1_1)
            .setParameter(HttpProtocolParams.USER_AGENT, "Jakarta-HttpComponents-Bench/1.1")
            .setBooleanParameter(HttpProtocolParams.USE_EXPECT_CONTINUE, false)
            .setBooleanParameter(HttpConnectionParams.STALE_CONNECTION_CHECK, false)
            .setIntParameter(HttpConnectionParams.SO_TIMEOUT, socketTimeout);
        return params;
    }

}

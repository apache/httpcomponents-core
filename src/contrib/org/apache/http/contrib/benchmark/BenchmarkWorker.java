/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2006 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.http.contrib.benchmark;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.Iterator;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpClientConnection;
import org.apache.http.impl.protocol.DefaultHttpProcessor;
import org.apache.http.io.SocketFactory;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpExecutionContext;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.apache.http.util.EntityUtils;

/**
 * Worker thread for the {@link HttpBenchmark HttpBenchmark}.
 *
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 *
 * <!-- empty lines above to avoid 'svn diff' context problems -->
 * @version $Revision$
 * 
 * @since 4.0
 */
public class BenchmarkWorker {

    private byte[] buffer = new byte[4096];
    private final int verbosity;
    private final HttpParams params;
    private final HttpContext context;
    private final HttpRequestExecutor httpexecutor;
    private final ConnectionReuseStrategy connstrategy;
    
    public BenchmarkWorker(final HttpParams params, int verbosity) {
        super();
        this.params = params;
        this.context = new HttpExecutionContext(null);

        DefaultHttpProcessor httpproc = new DefaultHttpProcessor();
        this.httpexecutor = new HttpRequestExecutor(httpproc);
        this.httpexecutor.setParams(params);

        // Required request interceptors
        httpproc.addInterceptor(new RequestContent());
        httpproc.addInterceptor(new RequestTargetHost());
        // Recommended request interceptors
        httpproc.addInterceptor(new RequestConnControl());
        httpproc.addInterceptor(new RequestUserAgent());
        httpproc.addInterceptor(new RequestExpectContinue());
        
        this.connstrategy = new DefaultConnectionReuseStrategy();
        this.verbosity = verbosity;
    }
    
    public Stats execute(
            final HttpRequest request, 
            final HttpHost targetHost, 
            int count,
            boolean keepalive) throws HttpException {
        HttpResponse response = null;
        DefaultHttpClientConnection conn = new DefaultHttpClientConnection(); 
        Stats stats = new Stats();
        stats.start();
        for (int i = 0; i < count; i++) {
            try {
                resetHeader(request);
                if (!conn.isOpen()) {
                    SocketFactory socketfactory = targetHost.getScheme().getSocketFactory();
                    Socket socket = socketfactory.createSocket(
                            targetHost.getHostName(), targetHost.getPort(), 
                            null, 0, params);
                    conn.bind(socket, targetHost, params);
                }
                response = this.httpexecutor.execute(request, conn, this.context);
                if (this.verbosity >= 3) {
                    System.out.println(">> " + request.getRequestLine().toString());
                    Header[] headers = request.getAllHeaders();
                    for (int h = 0; h < headers.length; h++) {
                        System.out.println(">> " + headers[h].toString());
                    }
                    System.out.println();
                }
                if (this.verbosity >= 2) {
                    System.out.println(response.getStatusLine().getStatusCode());
                }
                if (this.verbosity >= 3) {
                    System.out.println("<< " + response.getStatusLine().toString());
                    Header[] headers = response.getAllHeaders();
                    for (int h = 0; h < headers.length; h++) {
                        System.out.println("<< " + headers[h].toString());
                    }
                    System.out.println();
                }
                HttpEntity entity = response.getEntity();
                String charset = EntityUtils.getContentCharSet(entity);
                if (charset == null) {
                    charset = HTTP.DEFAULT_CONTENT_CHARSET;
                }
                long contentlen = 0;
                if (entity != null) {
                    InputStream instream = entity.getContent();
                    int l = 0;
                    while ((l = instream.read(this.buffer)) != -1) {
                        stats.incTotal(l);
                        contentlen += l;
                        if (this.verbosity >= 4) {
                            String s = new String(this.buffer, 0, l, charset);
                            System.out.print(s);
                        }
                    }
                }
                if (this.verbosity >= 4) {
                    System.out.println();
                    System.out.println();
                }
                if (!keepalive || !this.connstrategy.keepAlive(response, this.context)) {
                    conn.close();
                }
                stats.setContentLength(contentlen);
                stats.incSuccessCount();
            } catch (IOException ex) {
                stats.incFailureCount();
                if (this.verbosity >= 2) {
                    System.err.println("I/O error: " + ex.getMessage());
                }
            }
        }
        stats.finish();
        if (response != null) {
            Header header = response.getFirstHeader("Server");
            if (header != null) {
                stats.setServerName(header.getValue());
            }
        }
        return stats;
    }

    private static void resetHeader(final HttpRequest request) {
        for (Iterator it = request.headerIterator(); it.hasNext(); ) {
            Header header = (Header) it.next();
            if (!(header instanceof DefaultHeader)) {
                it.remove();
            }
        }
    }
    
}

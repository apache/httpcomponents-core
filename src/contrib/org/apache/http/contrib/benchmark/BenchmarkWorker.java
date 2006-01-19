/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2004 The Apache Software Foundation
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

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.Header;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.protocol.HttpRequestExecutor;

/**
 * <p>
 * </p>
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @version $Revision$
 * 
 * @since 4.0
 */
public class BenchmarkWorker {

    private byte[] buffer = new byte[4096];
    private final int verbosity;
    private final HttpRequestExecutor httpexecutor;
    private final ConnectionReuseStrategy connstrategy;
    
    public BenchmarkWorker(final HttpRequestExecutor httpexecutor, int verbosity) {
        super();
        this.httpexecutor = httpexecutor;
        this.connstrategy = new DefaultConnectionReuseStrategy();
        this.verbosity = verbosity;
    }
    
    public Stats execute(
            final HttpRequest request, 
            final HttpClientConnection conn, 
            int count,
            boolean keepalive) throws HttpException {
        HttpResponse response = null;
        Stats stats = new Stats();
        stats.start();
        for (int i = 0; i < count; i++) {
            try {
                response = this.httpexecutor.execute(request, conn);
                if (this.verbosity >= 4) {
                    System.out.println(">> " + request.getRequestLine().toString());
                    Header[] headers = request.getAllHeaders();
                    for (int h = 0; h < headers.length; h++) {
                        System.out.println(">> " + headers[h].toString());
                    }
                    System.out.println();
                }
                if (this.verbosity >= 3) {
                    System.out.println(response.getStatusLine().getStatusCode());
                }
                if (this.verbosity >= 4) {
                    System.out.println("<< " + response.getStatusLine().toString());
                    Header[] headers = response.getAllHeaders();
                    for (int h = 0; h < headers.length; h++) {
                        System.out.println("<< " + headers[h].toString());
                    }
                    System.out.println();
                }
                HttpEntity entity = response.getEntity();
                long contentlen = 0;
                if (entity != null) {
                    InputStream instream = entity.getContent();
                    int l = 0;
                    while ((l = instream.read(this.buffer)) != -1) {
                        stats.incTotal(l);
                        contentlen += l;
                    }
                }
                if (!keepalive || !this.connstrategy.keepAlive(response)) {
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

}

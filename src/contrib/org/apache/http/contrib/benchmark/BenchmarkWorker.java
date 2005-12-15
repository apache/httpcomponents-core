package org.apache.http.contrib.benchmark;

import java.io.IOException;
import java.io.InputStream;

import org.apache.http.Header;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.executor.HttpRequestExecutor;

public class BenchmarkWorker {

    private final int verbosity;
    private final HttpRequestExecutor httpexecutor;
    
    public BenchmarkWorker(final HttpRequestExecutor httpexecutor, int verbosity) {
        super();
        this.httpexecutor = httpexecutor;
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
                if (this.verbosity >= 4) {
                    System.out.println(">> " + request.getRequestLine().toString());
                    Header[] headers = request.getAllHeaders();
                    for (int h = 0; h < headers.length; h++) {
                        System.out.println(">> " + headers[h].toString());
                    }
                    System.out.println();
                }
                response = this.httpexecutor.execute(request, conn);
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
                InputStream instream = response.getEntity().getContent();
                byte[] buffer = new byte[4096];
                long contentlen = 0;
                int l = 0;
                while ((l = instream.read(buffer)) != -1) {
                    stats.incTotal(l);
                    contentlen += l;
                }
                if (!keepalive) {
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

package org.apache.http.benchmark;

import java.io.IOException;
import java.net.URL;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SmokeTest {

    private HttpServer server;

    @Before
    public void setup() throws Exception {
        server = new HttpServer();
        server.registerHandler("/", new HttpRequestHandler() {
            public void handle(
                    final HttpRequest request,
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                response.setStatusCode(HttpStatus.SC_OK);
                response.setEntity(new StringEntity("0123456789ABCDEF", ContentType.TEXT_PLAIN));
            }
        });
        server.start();
    }

    @After
    public void shutdown() throws Exception {
        server.shutdown();
    }

    @Test
    public void testBasics() throws Exception {
        Config config = new Config();
        config.setKeepAlive(true);
        config.setMethod("GET");
        config.setUrl(new URL("http://localhost:" + server.getPort() + "/"));
        config.setThreads(3);
        config.setRequests(100);
        HttpBenchmark httpBenchmark = new HttpBenchmark(config);
        Results results = httpBenchmark.doExecute();
        Assert.assertNotNull(results);
        Assert.assertEquals(16, results.getContentLength());
        Assert.assertEquals(3, results.getConcurrencyLevel());
        Assert.assertEquals(300, results.getKeepAliveCount());
        Assert.assertEquals(300, results.getSuccessCount());
        Assert.assertEquals(0, results.getFailureCount());
        Assert.assertEquals(0, results.getWriteErrors());
        Assert.assertEquals(300 * 16, results.getTotalBytes());
        Assert.assertEquals(300 * 16, results.getTotalBytesRcvd());
    }

}

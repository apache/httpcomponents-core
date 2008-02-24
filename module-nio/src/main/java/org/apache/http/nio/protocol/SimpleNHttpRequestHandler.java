package org.apache.http.nio.protocol;

import java.io.IOException;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;

/**
 * A simple implementation of {@link NHttpRequestHandler} that abstracts away
 * the need to use {@link NHttpResponseTrigger}. Implementations need only to
 * implement {@link #handle(HttpRequest, HttpResponse, HttpContext)}.
 */
public abstract class SimpleNHttpRequestHandler implements NHttpRequestHandler {

    public final void handle(
            final HttpRequest request,
            final HttpResponse response,
            final NHttpResponseTrigger trigger,
            final HttpContext context) throws HttpException, IOException {
        handle(request, response, context);
        trigger.submitResponse(response);
    }

    public abstract void handle(HttpRequest request, HttpResponse response, HttpContext context)
        throws HttpException, IOException;

}
package org.apache.http.nio.impl;

import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.nio.NHttpClientHandler;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.params.HttpParams;

public class DefaultClientIOEventDispatch implements IOEventDispatch {

    private static final String NHTTP_CONN = "NHTTP_CONN";
    
    private final NHttpClientHandler handler;
    private final HttpParams params;
    
    public DefaultClientIOEventDispatch(final NHttpClientHandler handler, final HttpParams params) {
        super();
        if (handler == null) {
            throw new IllegalArgumentException("HTTP client handler may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.handler = handler;
        this.params = params;
    }
    
    public void connected(final IOSession session) {
        DefaultNHttpClientConnection conn = new DefaultNHttpClientConnection(
                session, 
                new DefaultHttpResponseFactory(),
                this.params); 
        session.setAttribute(NHTTP_CONN, conn);
        
        Object attachment = session.getAttribute(IOSession.ATTACHMENT_KEY);
        this.handler.connected(conn, attachment);
    }

    public void disconnected(final IOSession session) {
        DefaultNHttpClientConnection conn = (DefaultNHttpClientConnection) session.getAttribute(
                NHTTP_CONN);
        this.handler.closed(conn);
    }

    public void inputReady(final IOSession session) {
        DefaultNHttpClientConnection conn = (DefaultNHttpClientConnection) session.getAttribute(
                NHTTP_CONN);
        conn.consumeInput(this.handler);
    }

    public void outputReady(final IOSession session) {
        DefaultNHttpClientConnection conn = (DefaultNHttpClientConnection) session.getAttribute(
                NHTTP_CONN);
        conn.produceOutput(this.handler);
    }

    public void timeout(final IOSession session) {
        DefaultNHttpClientConnection conn = (DefaultNHttpClientConnection) session.getAttribute(
                NHTTP_CONN);
        this.handler.timeout(conn);
    }

}

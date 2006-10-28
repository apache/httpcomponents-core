package org.apache.http.nio.impl.handler;

import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.nio.IOEventDispatch;
import org.apache.http.nio.IOSession;
import org.apache.http.nio.SessionRequest;
import org.apache.http.nio.handler.NHttpClientHandler;
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
        
        SessionRequest request = (SessionRequest) session.getAttribute(SessionRequest.ATTRIB_KEY);
        
        this.handler.connected(conn, request.getAttachment());
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

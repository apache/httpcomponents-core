package org.apache.http.nio.impl.handler;

import org.apache.http.impl.DefaultHttpRequestFactory;
import org.apache.http.nio.IOEventDispatch;
import org.apache.http.nio.IOSession;
import org.apache.http.nio.handler.NHttpServiceHandler;
import org.apache.http.params.HttpParams;

public class DefaultServerIOEventDispatch implements IOEventDispatch {

    private static final String NHTTP_CONN = "NHTTP_CONN";
    
    private final NHttpServiceHandler handler;
    private final HttpParams params;
    
    public DefaultServerIOEventDispatch(final NHttpServiceHandler handler, final HttpParams params) {
        super();
        if (handler == null) {
            throw new IllegalArgumentException("HTTP service handler may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.handler = handler;
        this.params = params;
    }
    
    public void connected(final IOSession session) {
        DefaultNHttpServerConnection conn = new DefaultNHttpServerConnection(
                session, 
                new DefaultHttpRequestFactory(),
                this.params); 
        session.setAttribute(NHTTP_CONN, conn);
    }

    public void disconnected(final IOSession session) {
        DefaultNHttpServerConnection conn = (DefaultNHttpServerConnection) session.getAttribute(
                NHTTP_CONN);
        this.handler.closed(conn);
    }

    public void inputReady(final IOSession session) {
        DefaultNHttpServerConnection conn = (DefaultNHttpServerConnection) session.getAttribute(
                NHTTP_CONN);
        conn.consumeInput(this.handler);
    }

    public void outputReady(final IOSession session) {
        DefaultNHttpServerConnection conn = (DefaultNHttpServerConnection) session.getAttribute(
                NHTTP_CONN);
        conn.produceOutput(this.handler);
    }

    public void timeout(final IOSession session) {
        DefaultNHttpServerConnection conn = (DefaultNHttpServerConnection) session.getAttribute(
                NHTTP_CONN);
        this.handler.timeout(conn);
    }

}

package org.apache.http.nio.protocol;

import java.util.Map;

import org.apache.http.protocol.UriPatternMatcher;

/**
 * Maintains a map of HTTP request handlers keyed by a request URI pattern.
 * {@link NHttpRequestHandler} instances can be looked up by request URI
 * using the {@link NHttpRequestHandlerResolver} interface.<br/>
 * Patterns may have three formats:
 * <ul>
 *   <li><code>*</code></li>
 *   <li><code>*&lt;uri&gt;</code></li>
 *   <li><code>&lt;uri&gt;*</code></li>
 * </ul>
 *
 * @version $Revision$
 */
public class NHttpRequestHandlerRegistry implements NHttpRequestHandlerResolver {

    private final UriPatternMatcher matcher;

    public NHttpRequestHandlerRegistry() {
        matcher = new UriPatternMatcher();
    }

    public void register(final String pattern, final NHttpRequestHandler handler) {
        matcher.register(pattern, handler);
    }

    public void unregister(final String pattern) {
        matcher.unregister(pattern);
    }

    public void setHandlers(final Map<String, ? extends NHttpRequestHandler> map) {
        matcher.setHandlers(map);
    }

    public NHttpRequestHandler lookup(String requestURI) {
        return (NHttpRequestHandler) matcher.lookup(requestURI);
    }

}

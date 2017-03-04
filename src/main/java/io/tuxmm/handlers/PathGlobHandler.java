/**
 *
 *
 */
package io.tuxmm.handlers;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import jodd.util.Wildcard;

public class PathGlobHandler implements HttpHandler {

    private final Map<String, HttpHandler> paths = Collections.synchronizedMap(new LinkedHashMap<>());

    private HttpHandler defaultHandler = ResponseCodeHandler.HANDLE_500;

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (paths.isEmpty()) {
            defaultHandler.handleRequest(exchange);
            return;
        }
        final String path = exchange.getRelativePath();

        AtomicBoolean hit = new AtomicBoolean(false);
        paths.entrySet().forEach(entry -> {
            if (!hit.get()) {
                String pathKey = entry.getKey();
                if (pathKey.endsWith("/") && !pathKey.contains("*")) {
                    pathKey = pathKey + "*";
                }
                hit.set(Wildcard.match(path, pathKey));
                if (hit.get()) {
                    try {
                        entry.getValue().handleRequest(exchange);
                    } catch (Exception e) {
                        // TODO: log.error(e)
                    }
                }
            }
        });
        if (!hit.get()) {
            defaultHandler.handleRequest(exchange);
        }
    }

    public synchronized boolean contains(final String path) {
        return paths.containsKey(path);
    }

    public synchronized boolean addPath(final String path, final HttpHandler handler) {
        return paths.put(path, handler) == null;
    }

    public synchronized boolean removePath(final String path) {
        return paths.remove(path) == null;
    }

    public HttpHandler setDefaultHandler(HttpHandler defaultHandler) {
        this.defaultHandler = defaultHandler;
        return this;
    }

    public HttpHandler getDefaultHandler() {
        return this.defaultHandler;
    }

    public synchronized void clear() {
        paths.clear();
    }
}

/**
 *
 *
 */
package io.github.tuxmonteiro.planc.handlers;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import jodd.util.Wildcard;

public class PathGlobHandler implements HttpHandler {

    private final Map<PathOrdered, HttpHandler> paths = Collections.synchronizedMap(new TreeMap<PathOrdered, HttpHandler>());

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
                String pathKey = entry.getKey().getPath();
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
        return paths.containsKey(new PathOrdered(path, 0));
    }

    public synchronized boolean addPath(final String path, int order, final HttpHandler handler) {
        return paths.put(new PathOrdered(path, order), handler) == null;
    }

    public synchronized boolean removePath(final String path) {
        return paths.remove(new PathOrdered(path, 0)) == null;
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

    private static class PathOrdered implements Comparable<PathOrdered> {
        private final String path;
        private final int order;

        PathOrdered(String path, int order) {
            this.path = path;
            this.order = order;
        }

        String getPath() {
            return path;
        }

        public int getOrder() {
            return order;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PathOrdered that = (PathOrdered) o;
            return Objects.equals(path, that.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path);
        }

        @Override
        public int compareTo(final PathOrdered other) {
            return this.order < other.order ? -1 : this.order > other.order ? 1 : 0;
        }
    }
}

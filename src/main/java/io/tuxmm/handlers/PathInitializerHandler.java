/**
 *
 */

package io.tuxmm.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PathInitializerHandler implements HttpHandler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final PathGlobHandler pathGlobHandler;
    private final Set<String> paths = Collections.synchronizedSet(new HashSet<>());

    PathInitializerHandler(final PathGlobHandler pathGlobHandler) {
        this.pathGlobHandler = pathGlobHandler;
    }

    @Override
    public synchronized void handleRequest(HttpServerExchange exchange) throws Exception {
        String path = exchange.getRelativePath();
        if (paths.add(path)) {
            final ProxyPoolInitializerHandler proxyPoolInitializerHandler = new ProxyPoolInitializerHandler(pathGlobHandler, path);
            pathGlobHandler.addPath(path, proxyPoolInitializerHandler);

            logger.info("add path " + path + " (" +
                    "pathGlobHandler: " + pathGlobHandler.hashCode() + ", " +
                    "proxyPoolInitializerHandler: " + proxyPoolInitializerHandler.hashCode() + ")");
        }

        pathGlobHandler.handleRequest(exchange);
    }

    public synchronized void reset() {
        paths.clear();
    }

}

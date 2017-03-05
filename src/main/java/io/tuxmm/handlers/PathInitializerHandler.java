/**
 *
 */

package io.tuxmm.handlers;

import io.tuxmm.Application;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PathInitializerHandler implements HttpHandler {

    private StringRedisTemplate template;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final PathGlobHandler pathGlobHandler;
    private final Set<String> paths = Collections.synchronizedSet(new HashSet<>());

    PathInitializerHandler(final PathGlobHandler pathGlobHandler) {
        this.pathGlobHandler = pathGlobHandler;
    }

    public PathInitializerHandler setTemplate(StringRedisTemplate template) {
        this.template = template;
        return this;
    }

    @Override
    public synchronized void handleRequest(HttpServerExchange exchange) throws Exception {
        String host = exchange.getRequestHeaders().get(Headers.HOST_STRING).getFirst();
        if (paths.isEmpty()) {
            final Set<String> pathsRegistered = template.keys(Application.PREFIX + "@" + host + "@path@*");
            if (!pathsRegistered.isEmpty()) {
                pathsRegistered.forEach(keyComplete -> {
                    int orderFromIndex = keyComplete.lastIndexOf("@");
                    int order = Integer.valueOf(keyComplete.substring(orderFromIndex + 1, keyComplete.length()));
                    int pathFromIndex = keyComplete.substring(0, orderFromIndex - 1).lastIndexOf('@');
                    String path = keyComplete.substring(pathFromIndex + 1, orderFromIndex);
                    final ProxyPoolInitializerHandler proxyPoolInitializerHandler = new ProxyPoolInitializerHandler(pathGlobHandler, path, order);
                    proxyPoolInitializerHandler.setTemplate(template);
                    pathGlobHandler.addPath(path, order, proxyPoolInitializerHandler);
                    paths.add(path);

                    logger.info("add path " + path + " [" + order +"] (" +
                            "pathGlobHandler: " + pathGlobHandler.hashCode() + ", " +
                            "proxyPoolInitializerHandler: " + proxyPoolInitializerHandler.hashCode() + ")");
                });
                pathGlobHandler.setDefaultHandler(ResponseCodeHandler.HANDLE_500);
                pathGlobHandler.handleRequest(exchange);
                return;
            }
        }
        String pathRequested = exchange.getRelativePath();
        logger.error(this + ": " + host + "@" + pathRequested);
        ResponseCodeHandler.HANDLE_500.handleRequest(exchange);
    }

    public synchronized void reset() {
        paths.clear();
    }

}

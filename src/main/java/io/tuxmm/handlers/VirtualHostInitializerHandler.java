/**
 *
 */

package io.tuxmm.handlers;

import io.tuxmm.Application;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.NameVirtualHostHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class VirtualHostInitializerHandler implements HttpHandler {

    private StringRedisTemplate template;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final NameVirtualHostHandler nameVirtualHostHandler;
    private final Set<String> virtualhosts = Collections.synchronizedSet(new HashSet<>());

    public VirtualHostInitializerHandler(final NameVirtualHostHandler nameVirtualHostHandler) {
        this.nameVirtualHostHandler = nameVirtualHostHandler;
    }

    public VirtualHostInitializerHandler setTemplate(final StringRedisTemplate template) {
        this.template = template;
        return this;
    }

    @Override
    public synchronized void handleRequest(HttpServerExchange exchange) throws Exception {
        String host = exchange.getRequestHeaders().get(Headers.HOST_STRING).getFirst();
        if (template.hasKey(Application.PREFIX + "@" + host)) {
            if (virtualhosts.add(host)) {
                final PathGlobHandler pathGlobHandler = new PathGlobHandler();
                pathGlobHandler.setDefaultHandler(new PathInitializerHandler(pathGlobHandler).setTemplate(template));
                nameVirtualHostHandler.addHost(host, pathGlobHandler);

                logger.info("add vh " + host + " (pathGlobHandler: " + pathGlobHandler.hashCode() + ")");
            }
            nameVirtualHostHandler.handleRequest(exchange);
            return;
        }
        ResponseCodeHandler.HANDLE_500.handleRequest(exchange);
    }

    public synchronized void resetAll() {
        logger.info("resetting all");
        nameVirtualHostHandler.getHosts().clear();
        virtualhosts.clear();
    }

    public synchronized void reset(String virtualhost) {
        logger.info("resetting vh " + virtualhost);
        if (!nameVirtualHostHandler.getHosts().isEmpty()) {
            HttpHandler httpHandler = nameVirtualHostHandler.getHosts().get(virtualhost);
            if (httpHandler instanceof PathGlobHandler) {
                ((PathGlobHandler) httpHandler).clear();
                HttpHandler defaultHandler = ((PathGlobHandler) httpHandler).getDefaultHandler();
                if (defaultHandler instanceof PathInitializerHandler) {
                    ((PathInitializerHandler) defaultHandler).reset();
                }
            }
        }
        if (!virtualhosts.isEmpty()) {
            virtualhosts.remove(virtualhost);
        }
    }
}

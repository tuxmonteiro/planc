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
import org.zalando.boot.etcd.EtcdClient;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class VirtualHostInitializerHandler implements HttpHandler {

    private EtcdClient template;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final NameVirtualHostHandler nameVirtualHostHandler;
    private final Set<String> virtualhosts = Collections.synchronizedSet(new HashSet<>());

    public VirtualHostInitializerHandler(final NameVirtualHostHandler nameVirtualHostHandler) {
        this.nameVirtualHostHandler = nameVirtualHostHandler;
    }

    public VirtualHostInitializerHandler setTemplate(final EtcdClient template) {
        this.template = template;
        return this;
    }

    @Override
    public synchronized void handleRequest(HttpServerExchange exchange) throws Exception {
        final String host = exchange.getRequestHeaders().get(Headers.HOST_STRING).getFirst();
        final String prefixNodeName = "/" + Application.PREFIX;
        final String virtualhostNodePrefix = prefixNodeName + "/virtualhosts";
        final String virtualhostNodeName = virtualhostNodePrefix + "/" + host;
        boolean existVirtualhostPath = Optional.ofNullable(template.get(prefixNodeName).getNode().getNodes()).orElse(Collections.emptyList())
                                    .stream()
                                    .filter(node -> node.getKey().equals(virtualhostNodePrefix))
                                    .count() != 0;
        if (!existVirtualhostPath) {
            logger.error(virtualhostNodePrefix + " not found");
            ResponseCodeHandler.HANDLE_500.handleRequest(exchange);
            return;
        }

        boolean existHost = Optional.ofNullable(template.get(virtualhostNodePrefix).getNode().getNodes()).orElse(Collections.emptyList())
                .stream()
                .filter(node -> node.getKey().equals(virtualhostNodeName))
                .count() != 0;

        if (existHost) {
            if (virtualhosts.add(host)) {
                final PathGlobHandler pathGlobHandler = new PathGlobHandler();
                pathGlobHandler.setDefaultHandler(new PathInitializerHandler(pathGlobHandler).setTemplate(template));
                nameVirtualHostHandler.addHost(host, pathGlobHandler);

                logger.info("add vh " + host + " (pathGlobHandler: " + pathGlobHandler.hashCode() + ")");
            }
            nameVirtualHostHandler.handleRequest(exchange);
            return;
        }
        logger.error("vh " + host + " not exit (" + virtualhostNodeName + ")");
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

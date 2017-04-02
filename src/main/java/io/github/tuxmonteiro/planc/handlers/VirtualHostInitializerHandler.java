/**
 *
 */

package io.github.tuxmonteiro.planc.handlers;

import io.github.tuxmonteiro.planc.services.ExternalData;
import io.github.tuxmonteiro.planc.services.StatsdClient;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.NameVirtualHostHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static io.github.tuxmonteiro.planc.services.ExternalData.PREFIX_KEY;
import static io.github.tuxmonteiro.planc.services.ExternalData.VIRTUALHOSTS_KEY;

public class VirtualHostInitializerHandler implements HttpHandler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final NameVirtualHostHandler nameVirtualHostHandler;
    private final Set<String> virtualhosts = Collections.synchronizedSet(new HashSet<>());

    private ExternalData data;
    private StatsdClient statsdClient;

    public VirtualHostInitializerHandler(final NameVirtualHostHandler nameVirtualHostHandler) {
        this.nameVirtualHostHandler = nameVirtualHostHandler;
    }

    public synchronized VirtualHostInitializerHandler setExternalData(final ExternalData externalData) {
        this.data = externalData;
        return this;
    }

    public synchronized VirtualHostInitializerHandler setStatsdClient(final StatsdClient statsdClient) {
        this.statsdClient = statsdClient;
        return this;
    }

    @Override
    public synchronized void handleRequest(HttpServerExchange exchange) throws Exception {
        final String host = exchange.getHostName();
        final String virtualhostNodePrefix = VIRTUALHOSTS_KEY;
        final String virtualhostNodeName = virtualhostNodePrefix + "/" + host;
        boolean existVirtualhostPath = data.listFrom(PREFIX_KEY)
                                    .stream()
                                    .filter(node -> node.getKey().equals(virtualhostNodePrefix))
                                    .count() != 0;
        if (!existVirtualhostPath) {
            logger.error(virtualhostNodePrefix + " not found");
            ResponseCodeHandler.HANDLE_500.handleRequest(exchange);
            return;
        }

        boolean existHost = data.listFrom(virtualhostNodePrefix)
                .stream()
                .filter(node -> node.getKey().equals(virtualhostNodeName))
                .count() != 0;

        if (existHost) {
            if (virtualhosts.add(host)) {
                nameVirtualHostHandler.setDefaultHandler(new RuleInitializerHandler(nameVirtualHostHandler)
                        .setExternalData(data).setStatsdClient(statsdClient));

                logger.info("add vh " + host + " (nameVirtualHostHandler: " + nameVirtualHostHandler.hashCode() + ")");
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
                if (defaultHandler instanceof RuleInitializerHandler) {
                    ((RuleInitializerHandler) defaultHandler).reset();
                }
            }
        }
        if (!virtualhosts.isEmpty()) {
            virtualhosts.remove(virtualhost);
        }
    }
}

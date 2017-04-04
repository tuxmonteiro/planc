/**
 *
 */

package io.github.tuxmonteiro.planc.handlers;

import io.github.tuxmonteiro.planc.services.ExternalData;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.NameVirtualHostHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static io.github.tuxmonteiro.planc.services.ExternalData.VIRTUALHOSTS_KEY;

@Component
public class VirtualHostInitializerHandler implements HttpHandler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Set<String> virtualhosts = Collections.synchronizedSet(new HashSet<>());
    private final ExternalData data;
    private final ApplicationContext context;

    private final NameVirtualHostHandler nameVirtualHostHandler;

    @Autowired
    public VirtualHostInitializerHandler(final ExternalData externalData, final NameVirtualHostHandler nameVirtualHostHandler, final ApplicationContext context) {
        this.data = externalData;
        this.nameVirtualHostHandler = nameVirtualHostHandler;
        this.context = context;
    }

    @Override
    public synchronized void handleRequest(HttpServerExchange exchange) throws Exception {
        final String host = exchange.getHostName();
        final String virtualhostNodeName = VIRTUALHOSTS_KEY + "/" + host;
        if (!data.exist(VIRTUALHOSTS_KEY)) {
            logger.error(VIRTUALHOSTS_KEY + " not found");
            ResponseCodeHandler.HANDLE_500.handleRequest(exchange);
            return;
        }

        if (data.exist(virtualhostNodeName)) {
            if (virtualhosts.add(host)) {
                nameVirtualHostHandler.setDefaultHandler(newRuleInitializerHandler());
                logger.info("add vh " + host + " (nameVirtualHostHandler: " + nameVirtualHostHandler.hashCode() + ")");
            }
            nameVirtualHostHandler.handleRequest(exchange);
            return;
        }
        logger.error("vh " + host + " not exit (" + virtualhostNodeName + ")");
        ResponseCodeHandler.HANDLE_500.handleRequest(exchange);
    }

    private RuleInitializerHandler newRuleInitializerHandler() {
        return context.getBean(RuleInitializerHandler.class);
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

package io.github.tuxmonteiro.planc.client.hostselectors;

import io.github.tuxmonteiro.planc.client.ExtendedLoadBalancingProxyClient;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ClientStatisticsMarker {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected void stamp(final ExtendedLoadBalancingProxyClient.Host host, final HttpServerExchange exchange) {
        final int openConnections = host.getOpenConnection();
        final String uri = host.getUri().toString();
        exchange.getResponseHeaders().put(new HttpString("X-Uri-Target"), uri);
        exchange.getResponseHeaders().put(new HttpString("X-Uri-Conn"), openConnections);
        if (logger.isDebugEnabled()) {
            logger.debug("{\"client_statistic\": { \"uri\": \"" + uri + "\", \"open_connections\": " + openConnections + "} }");
        }
    }
}

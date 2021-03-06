/**
 *
 */

package io.github.tuxmonteiro.planc.client.hostselectors;

import io.github.tuxmonteiro.planc.client.ExtendedLoadBalancingProxyClient;
import io.undertow.server.HttpServerExchange;

import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinHostSelector extends ClientStatisticsMarker implements HostSelector {

    private final AtomicInteger currentHost = new AtomicInteger(0);

    @Override
    public int selectHost(final ExtendedLoadBalancingProxyClient.Host[] availableHosts, final HttpServerExchange exchange) {
        final int pos = currentHost.incrementAndGet() % availableHosts.length;
        stamp(availableHosts[pos], exchange);
        return pos;
    }
}

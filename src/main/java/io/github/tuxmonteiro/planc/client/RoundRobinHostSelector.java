/**
 *
 */

package io.github.tuxmonteiro.planc.client;

import io.undertow.server.HttpServerExchange;

import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinHostSelector implements ExtendedLoadBalancingProxyClient.HostSelector {

    private final AtomicInteger currentHost = new AtomicInteger(0);

    @Override
    public int selectHost(final ExtendedLoadBalancingProxyClient.Host[] availableHosts, final HttpServerExchange exchange) {
        return currentHost.incrementAndGet() % availableHosts.length;
    }
}

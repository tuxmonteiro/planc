/**
 *
 */

package io.github.tuxmonteiro.planc.client;

import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinHostSelector implements ExtendedLoadBalancingProxyClient.HostSelector {

    private final AtomicInteger currentHost = new AtomicInteger(0);

    @Override
    public int selectHost(ExtendedLoadBalancingProxyClient.Host[] availableHosts) {
        return currentHost.incrementAndGet() % availableHosts.length;
    }
}

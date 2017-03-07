/**
 *
 */

package io.github.tuxmonteiro.planc.client;

public enum HostSelectorAlgorithm {
    ROUNDROBIN (new RoundRobinHostSelector()),
    LEASTCONN (new LeastConnHostSelector());

    private final ExtendedLoadBalancingProxyClient.HostSelector hostSelector;
    public ExtendedLoadBalancingProxyClient.HostSelector getHostSelector() { return hostSelector; }
    HostSelectorAlgorithm(final ExtendedLoadBalancingProxyClient.HostSelector hostSelector) {
        this.hostSelector = hostSelector;
    }
}

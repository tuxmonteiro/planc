/**
 *
 */

package io.github.tuxmonteiro.planc.client;

public class HostSelectorInitializer implements ExtendedLoadBalancingProxyClient.HostSelector {

    private ExtendedLoadBalancingProxyClient.HostSelector hostSelector = new LeastConnHostSelector();

    public HostSelectorInitializer setHostSelector(final ExtendedLoadBalancingProxyClient.HostSelector hostSelector) {
        this.hostSelector = hostSelector;
        return this;
    }

    @Override
    public int selectHost(ExtendedLoadBalancingProxyClient.Host[] availableHosts) {
        return hostSelector.selectHost(availableHosts);
    }
}

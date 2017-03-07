/**
 *
 */

package io.github.tuxmonteiro.planc.client;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class LeastConnHostSelector implements ExtendedLoadBalancingProxyClient.HostSelector {

    @Override
    public int selectHost(final ExtendedLoadBalancingProxyClient.Host[] availableHosts) {
        return IntStream.range(0, availableHosts.length)
                .boxed()
                .collect(Collectors.toMap(i -> i, i -> availableHosts[i]))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(e -> e.getValue().getOpenConnection()))
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse(0);
    }
}

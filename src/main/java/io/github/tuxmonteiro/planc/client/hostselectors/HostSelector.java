/**
 *
 */

package io.github.tuxmonteiro.planc.client.hostselectors;

import io.github.tuxmonteiro.planc.client.ExtendedLoadBalancingProxyClient;
import io.undertow.server.HttpServerExchange;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public interface HostSelector {

    int selectHost(ExtendedLoadBalancingProxyClient.Host[] availableHosts, HttpServerExchange exchange);

    default Stream<Map.Entry<Integer, ExtendedLoadBalancingProxyClient.Host>> convertToMapStream(final ExtendedLoadBalancingProxyClient.Host[] availableHosts) {
        return IntStream.range(0, availableHosts.length)
                .boxed()
                .collect(Collectors.toMap(i -> i, i -> availableHosts[i]))
                .entrySet()
                .stream();
    }
}

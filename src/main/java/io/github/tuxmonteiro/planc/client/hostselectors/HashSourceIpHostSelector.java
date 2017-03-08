package io.github.tuxmonteiro.planc.client.hostselectors;

import io.github.tuxmonteiro.planc.client.ExtendedLoadBalancingProxyClient;
import io.github.tuxmonteiro.planc.consistenthash.ConsistentHash;
import io.github.tuxmonteiro.planc.consistenthash.HashAlgorithm;
import io.undertow.server.HttpServerExchange;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static io.github.tuxmonteiro.planc.consistenthash.HashAlgorithm.HashType.*;

public class HashSourceIpHostSelector implements HostSelector {

    private static final boolean IGNORE_XFORWARDED_FOR = Boolean.valueOf(System.getProperty("IGNORE_XFORWARDED_FOR", "false"));

    private HashAlgorithm hashAlgorithm = new HashAlgorithm(SIP24);
    private int numReplicas = 1;
    private final ConsistentHash<Integer> consistentHash = new ConsistentHash<>(hashAlgorithm, numReplicas, Collections.emptyList());
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    @Override
    public int selectHost(final ExtendedLoadBalancingProxyClient.Host[] availableHosts, final HttpServerExchange exchange) {
        if (!initialized.getAndSet(true)) {
            final LinkedHashSet<Integer> listPos = convertToMapStream(availableHosts)
                                                    .sorted(Comparator.comparing(e -> e.getValue().getOpenConnection()))
                                                    .map(Map.Entry::getKey)
                                                    .collect(Collectors.toCollection(LinkedHashSet::new));
            consistentHash.rebuild(hashAlgorithm, numReplicas, listPos);
        }
        return consistentHash.get(getKey(exchange));
    }

    private String getKey(final HttpServerExchange exchange) {
        String aSourceIP;
        String defaultSourceIp = "127.0.0.1";
        String httpHeaderXrealIp = "X-Real-IP";
        String httpHeaderXForwardedFor = "X-Forwarded-For";

        if (exchange == null) {
            return defaultSourceIp;
        }

        if (IGNORE_XFORWARDED_FOR) {
            aSourceIP = exchange.getSourceAddress().getHostString();
        } else {
            aSourceIP = exchange.getRequestHeaders().getFirst(httpHeaderXrealIp);
            if (aSourceIP!=null) {
                return aSourceIP;
            }
            aSourceIP = exchange.getRequestHeaders().getFirst(httpHeaderXForwardedFor);
            if (aSourceIP!=null) {
                return aSourceIP.contains(",") ? aSourceIP.split(",")[0] : aSourceIP;
            }
            aSourceIP = exchange.getSourceAddress().getHostString();
        }

        return aSourceIP!=null ? aSourceIP : defaultSourceIp;
    }
}

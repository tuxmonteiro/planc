/**
 *
 */

package io.github.tuxmonteiro.planc.handlers;

import io.github.tuxmonteiro.planc.client.ExtendedLoadBalancingProxyClient;
import io.github.tuxmonteiro.planc.client.hostselectors.HostSelector;
import io.github.tuxmonteiro.planc.client.hostselectors.HostSelectorAlgorithm;
import io.github.tuxmonteiro.planc.client.hostselectors.HostSelectorInitializer;
import io.github.tuxmonteiro.planc.services.ExternalData;
import io.github.tuxmonteiro.planc.services.StatsdClient;
import io.undertow.client.UndertowClient;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.ProxyClient;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zalando.boot.etcd.EtcdNode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static io.github.tuxmonteiro.planc.services.ExternalData.PREFIX_KEY;
import static io.github.tuxmonteiro.planc.services.ExternalData.POOLS_KEY;
import static io.github.tuxmonteiro.planc.services.ExternalData.VIRTUALHOSTS_KEY;

public class ProxyPoolInitializerHandler implements HttpHandler {

    private static final String X_FAKE_TARGET = "X-Fake-Target";

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final HttpHandler parentHandler;
    private final HostSelector hostSelectorInicializer = new HostSelectorInitializer();
    private final ProxyClient proxyClient = new ExtendedLoadBalancingProxyClient(UndertowClient.getInstance(), exchange -> {
                                                // we always create a new connection for upgrade requests
                                                return exchange.getRequestHeaders().contains(Headers.UPGRADE);
                                            }, hostSelectorInicializer)
                                            .setConnectionsPerThread(2000);
    private final HttpHandler defaultHandler = ResponseCodeHandler.HANDLE_500;
    private final String ruleKey;
    private final int order;
    private final ExtendedProxyHandler proxyHandler = new ExtendedProxyHandler(proxyClient, defaultHandler);
    private ExternalData data;

    ProxyPoolInitializerHandler(final HttpHandler parentHandler, final String ruleKey, final int order) {
        this.parentHandler = parentHandler;
        this.ruleKey = ruleKey;
        this.order = order;
    }

    public ProxyPoolInitializerHandler setExternalData(final ExternalData externalData) {
        this.data = externalData;
        return this;
    }

    public ProxyPoolInitializerHandler setStatsdClient(final StatsdClient statsdClient) {
        proxyHandler.setStatsdClient(statsdClient);
        return this;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        final HeaderMap requestHeaders = exchange.getRequestHeaders();
        final HeaderValues faKeTargetHeader = requestHeaders.get(X_FAKE_TARGET);
        if (faKeTargetHeader != null && !faKeTargetHeader.isEmpty()) {
            fakeTargetHandler().handleRequest(exchange);
            return;
        }
        final HeaderValues hostHeader = requestHeaders.get(Headers.HOST_STRING);
        if (hostHeader == null) {
            ResponseCodeHandler.HANDLE_500.handleRequest(exchange);
            return;
        }
        String host = hostHeader.getFirst();
        final String prefixNodeName = PREFIX_KEY;
        final String virtualhostNodeName = VIRTUALHOSTS_KEY + "/" + host;
        final String rulesNodeName = virtualhostNodeName + "/rules";
        final String poolNodeName = POOLS_KEY;

        if (isHostsEmpty(proxyClient)) {

            final List<EtcdNode> rulesRegistered = data.listFrom(rulesNodeName, true);

            final EtcdNode ruleNode = rulesRegistered.stream().filter(p -> p.getKey().equals(ruleKey)).findAny().orElse(data.emptyNode());
            final String poolName = data.listFrom(ruleNode)
                                        .stream().filter(n -> n.getKey().equals(ruleKey + "/target"))
                                        .findAny().orElse(data.emptyNode()).getValue();

            if ("".equals(poolName)) {
                this.defaultHandler.handleRequest(exchange);
                return;
            }

            final List<EtcdNode> poolsRegistered = data.listFrom(poolNodeName, true);

            if (!poolsRegistered.isEmpty()) {
                EtcdNode poolOfRuleSelected = poolsRegistered.stream()
                        .filter(p -> p.isDir() && p.getKey().equals(poolNodeName + "/" + poolName))
                        .findAny().orElse(data.emptyNode());
                if (!"".equals(poolOfRuleSelected.getKey())) {
                    logger.info("creating targetsPool (" +
                            "parentHandler: " + parentHandler.hashCode() + ", " +
                            "proxyHandler: " + proxyHandler.hashCode() + ", " +
                            "proxyClient: " + proxyClient.hashCode() + ", " +
                            "hostSelectorInicializer: " + hostSelectorInicializer.hashCode() + ")");

                    final List<EtcdNode> poolAttributes = Optional.ofNullable(poolOfRuleSelected.getNodes()).orElse(Collections.emptyList());
                    final List<EtcdNode> targets = new ArrayList<>();
                    poolAttributes.forEach(attrib -> {
                        if (attrib.getKey().equals(poolNodeName + "/" + poolName + "/loadbalance")) {
                            mapToTargetHostSelector(hostSelectorInicializer, HostSelectorAlgorithm.valueOf(attrib.getValue()).getHostSelector());
                            logger.info("LoadBalance algorithm: " + attrib.getValue());
                        }
                        if (attrib.getKey().equals(poolNodeName + "/" + poolName + "/targets")) {
                            targets.addAll(Optional.ofNullable(attrib.getNodes()).orElse(Collections.emptyList()));
                        }
                    });
                    targets.forEach(target -> {
                        addHost(proxyClient, URI.create(target.getValue()));
                        logger.info("added target " + target.getValue());
                    });
                } else {
                    logger.warn("pool " + poolName + " is empty [" + poolNodeName + "/" + poolName + "]");
                }
                int ruleFromIndex = ruleKey.lastIndexOf("/");
                String rule = ruleKey.substring(ruleFromIndex + 1, ruleKey.length());
                String ruleDecoded = new String(Base64.getDecoder().decode(rule), StandardCharsets.UTF_8);
                if (parentHandler instanceof PathGlobHandler) {
                    ((PathGlobHandler) parentHandler).addPath(ruleDecoded, order, proxyHandler);
                }
                proxyHandler.handleRequest(exchange);
                if (isHostsEmpty(proxyClient)) {
                    logger.error("hosts is empty");
                }
                return;
            }
        }

        this.defaultHandler.handleRequest(exchange);
    }

    private HttpHandler fakeTargetHandler() {
        return exchange -> {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            exchange.getResponseHeaders().put(Headers.SERVER, "PLANC");
            exchange.getResponseSender().send("2");
        };
    }

    private void mapToTargetHostSelector(final HostSelector hostSelector, final HostSelector targetHostSelector) {
        ((HostSelectorInitializer)hostSelector).setHostSelector(targetHostSelector);
    }

    private void addHost(final ProxyClient proxyClient, final URI uri) {
        ((ExtendedLoadBalancingProxyClient)proxyClient).addHost(uri);
    }

    private boolean isHostsEmpty(final ProxyClient proxyClient) {
        return ((ExtendedLoadBalancingProxyClient)proxyClient).isHostsEmpty();
    }

}

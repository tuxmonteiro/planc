/**
 *
 */

package io.github.tuxmonteiro.planc.handlers;

import io.github.tuxmonteiro.planc.Application;
import io.github.tuxmonteiro.planc.client.ExtendedLoadBalancingProxyClient;
import io.github.tuxmonteiro.planc.client.hostselectors.HostSelector;
import io.github.tuxmonteiro.planc.client.hostselectors.HostSelectorAlgorithm;
import io.github.tuxmonteiro.planc.client.hostselectors.HostSelectorInitializer;
import io.undertow.client.UndertowClient;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.ProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zalando.boot.etcd.EtcdClient;
import org.zalando.boot.etcd.EtcdNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ProxyPoolInitializerHandler implements HttpHandler {

    public static final String X_FAKE_TARGET = "X-Fake-Target";
    private EtcdClient template;

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
    private ProxyHandler proxyHandler = new ProxyHandler(proxyClient, defaultHandler);

    ProxyPoolInitializerHandler(final HttpHandler parentHandler, final String ruleKey, final int order) {
        this.parentHandler = parentHandler;
        this.ruleKey = ruleKey;
        this.order = order;
    }

    public ProxyPoolInitializerHandler setTemplate(final EtcdClient template) {
        this.template = template;
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
        final String prefixNodeName = "/" + Application.PREFIX;
        final String virtualhostNodePrefix = prefixNodeName + "/virtualhosts";
        final String virtualhostNodeName = virtualhostNodePrefix + "/" + host;
        final String rulesNodeName = virtualhostNodeName + "/rules";
        final String poolNodeName = prefixNodeName + "/pools";
        final EtcdNode nodeEmpty = new EtcdNode();
        nodeEmpty.setValue("");

        if (isHostsEmpty(proxyClient)) {

            final List<EtcdNode> rulesRegistered = Optional.ofNullable(template.get(rulesNodeName, true).getNode().getNodes()).orElse(Collections.emptyList());

            final EtcdNode ruleNode = rulesRegistered.stream().filter(p -> p.getKey().equals(ruleKey)).findAny().orElse(nodeEmpty);
            final String poolName = Optional.ofNullable(ruleNode.getNodes()).orElse(Collections.emptyList()).stream()
                    .filter(n -> n.getKey().equals(ruleKey + "/target")).findAny().orElse(nodeEmpty).getValue();

            if ("".equals(poolName)) {
                this.defaultHandler.handleRequest(exchange);
                return;
            }

            final List<EtcdNode> poolsRegistered = Optional.ofNullable(template.get(poolNodeName, true).getNode().getNodes()).orElse(Collections.emptyList());

            if (!poolsRegistered.isEmpty()) {
                EtcdNode poolOfRuleSelected = poolsRegistered.stream().filter(p -> p.isDir() && p.getKey().equals(poolNodeName + "/" + poolName)).findAny().orElse(nodeEmpty);
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
                String ruleDecoded = new String(Base64.getDecoder().decode(rule));
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

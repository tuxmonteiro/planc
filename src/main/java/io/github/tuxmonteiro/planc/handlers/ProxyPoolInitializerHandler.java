/**
 *
 */

package io.github.tuxmonteiro.planc.handlers;

import io.github.tuxmonteiro.planc.client.ExtendedLoadBalancingProxyClient;
import io.github.tuxmonteiro.planc.client.hostselectors.HostSelector;
import io.github.tuxmonteiro.planc.client.hostselectors.HostSelectorAlgorithm;
import io.github.tuxmonteiro.planc.client.hostselectors.HostSelectorInitializer;
import io.github.tuxmonteiro.planc.services.ExternalData;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.zalando.boot.etcd.EtcdNode;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static io.github.tuxmonteiro.planc.services.ExternalData.POOLS_KEY;
import static io.github.tuxmonteiro.planc.services.ExternalData.VIRTUALHOSTS_KEY;

@Component
@Scope("prototype")
public class ProxyPoolInitializerHandler implements HttpHandler {

    private static final String X_FAKE_TARGET = "X-Fake-Target";

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final HostSelector hostSelectorInicializer = new HostSelectorInitializer();
    private final ProxyClient proxyClient = new ExtendedLoadBalancingProxyClient(UndertowClient.getInstance(), exchange -> {
                                                // we always create a new connection for upgrade requests
                                                return exchange.getRequestHeaders().contains(Headers.UPGRADE);
                                            }, hostSelectorInicializer)
                                            .setConnectionsPerThread(2000);
    private final HttpHandler defaultHandler = ResponseCodeHandler.HANDLE_500;
    private HttpHandler parentHandler;
    private String ruleKey;
    private int ruleOrder;

    private final ExternalData data;
    private final ExtendedProxyHandler proxyHandler;

    @Autowired
    ProxyPoolInitializerHandler(final ExternalData externalData, final ExtendedProxyHandler proxyHandler) {
        this.data = externalData;
        this.proxyHandler = proxyHandler.setProxyClientAndDefaultHandler(proxyClient, defaultHandler);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (isFake(exchange)) return;

        String host = exchange.getHostName();
        final String rulesNodeName = VIRTUALHOSTS_KEY + "/" + host + "/rules";

        if (isHostsEmpty(proxyClient)) {
            final String poolName = data.node(ruleKey + "/target").getValue();

            if (poolName == null) {
                this.defaultHandler.handleRequest(exchange);
                return;
            }

            final String poolNameKey = POOLS_KEY + "/" + poolName;
            if (data.node(poolNameKey).getKey() != null) {
                logHashCodes();
                loadTargetsAndPoolAttributes(poolNameKey);
            } else {
                logger.warn("pool " + poolName + " is empty [" + poolNameKey + "]");
            }
            String ruleDecoded = extractRuleDecoded();
            if (parentHandler instanceof PathGlobHandler) {
                ((PathGlobHandler) parentHandler).addPath(ruleDecoded, ruleOrder, proxyHandler);
            }
            proxyHandler.handleRequest(exchange);
            if (isHostsEmpty(proxyClient)) {
                logger.error("hosts is empty");
            }
        }

        this.defaultHandler.handleRequest(exchange);
    }

    private void logHashCodes() {
        logger.info("creating targetsPool (" +
                "parentHandler: " + parentHandler.hashCode() + ", " +
                "proxyHandler: " + proxyHandler.hashCode() + ", " +
                "proxyClient: " + proxyClient.hashCode() + ", " +
                "hostSelectorInicializer: " + hostSelectorInicializer.hashCode() + ")");
    }

    private String extractRuleDecoded() {
        int ruleFromIndex = ruleKey.lastIndexOf("/");
        String rule = ruleKey.substring(ruleFromIndex + 1, ruleKey.length());
        return new String(Base64.getDecoder().decode(rule.getBytes(Charset.defaultCharset())), Charset.defaultCharset());
    }

    private boolean isFake(HttpServerExchange exchange) throws Exception {
        final HeaderMap requestHeaders = exchange.getRequestHeaders();
        final HeaderValues faKeTargetHeader = requestHeaders.get(X_FAKE_TARGET);
        if (faKeTargetHeader != null && !faKeTargetHeader.isEmpty()) {
            fakeTargetHandler().handleRequest(exchange);
            return true;
        }
        return false;
    }

    private void loadTargetsAndPoolAttributes(String poolNameKey) {
        final List<EtcdNode> poolAttributes = data.listFrom(poolNameKey, true);
        final List<EtcdNode> targets = new ArrayList<>();
        poolAttributes.forEach(attrib -> {
            if (attrib.getKey().equals(poolNameKey + "/loadbalance")) {
                mapToTargetHostSelector(hostSelectorInicializer, HostSelectorAlgorithm.valueOf(attrib.getValue()).getHostSelector());
                logger.info("LoadBalance algorithm: " + attrib.getValue());
            }
            if (attrib.getKey().equals(poolNameKey + "/targets")) {
                targets.addAll(data.listFrom(attrib));
            }
        });
        targets.forEach(target -> {
            addHost(proxyClient, URI.create(target.getValue()));
            logger.info("added target " + target.getValue());
        });
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

    public ProxyPoolInitializerHandler setParentHandler(final HttpHandler parentHandler) {
        this.parentHandler = parentHandler;
        return this;
    }

    public ProxyPoolInitializerHandler setRuleKey(String ruleKey) {
        this.ruleKey = ruleKey;
        return this;
    }

    public ProxyPoolInitializerHandler setRuleOrder(int ruleOrder) {
        this.ruleOrder = ruleOrder;
        return this;
    }
}

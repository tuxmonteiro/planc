/**
 *
 */

package io.github.tuxmonteiro.planc.handlers;

import io.github.tuxmonteiro.planc.Application;
import io.github.tuxmonteiro.planc.client.ExtendedLoadBalancingProxyClient;
import io.github.tuxmonteiro.planc.client.hostselectors.HostSelectorAlgorithm;
import io.github.tuxmonteiro.planc.client.hostselectors.HostSelectorInitializer;
import io.undertow.client.UndertowClient;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.util.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zalando.boot.etcd.EtcdClient;
import org.zalando.boot.etcd.EtcdNode;

import java.net.URI;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ProxyPoolInitializerHandler implements HttpHandler {

    private EtcdClient template;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final PathGlobHandler pathGlobHandler;
    private final HostSelectorInitializer hostSelectorInicializer = new HostSelectorInitializer();
    private final ExtendedLoadBalancingProxyClient proxyClient = new ExtendedLoadBalancingProxyClient(UndertowClient.getInstance(), null, hostSelectorInicializer);
    private final HttpHandler defaultHandler = ResponseCodeHandler.HANDLE_500;
    private final String pathKey;
    private final int order;
    private ProxyHandler proxyHandler = new ProxyHandler(proxyClient, defaultHandler);

    ProxyPoolInitializerHandler(final PathGlobHandler pathGlobHandler, final String pathKey, final int order) {
        this.pathGlobHandler = pathGlobHandler;
        this.pathKey = pathKey;
        this.order = order;
    }

    public ProxyPoolInitializerHandler setTemplate(final EtcdClient template) {
        this.template = template;
        return this;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String host = exchange.getRequestHeaders().get(Headers.HOST_STRING).getFirst();
        final String prefixNodeName = "/" + Application.PREFIX;
        final String virtualhostNodePrefix = prefixNodeName + "/virtualhosts";
        final String virtualhostNodeName = virtualhostNodePrefix + "/" + host;
        final String pathNodeName = virtualhostNodeName + "/path";
        final String poolNodeName = prefixNodeName + "/pools";
        final EtcdNode nodeEmpty = new EtcdNode();
        nodeEmpty.setValue("");

        if (proxyClient.isHostsEmpty()) {

            final List<EtcdNode> pathsRegistered = Optional.ofNullable(template.get(pathNodeName, true).getNode().getNodes()).orElse(Collections.emptyList());

            final EtcdNode pathNode = pathsRegistered.stream().filter(p -> p.getKey().equals(pathKey)).findAny().orElse(nodeEmpty);
            final String poolName = Optional.ofNullable(pathNode.getNodes()).orElse(Collections.emptyList()).stream()
                    .filter(n -> n.getKey().equals(pathKey + "/target")).findAny().orElse(nodeEmpty).getValue();

            if ("".equals(poolName)) {
                this.defaultHandler.handleRequest(exchange);
                return;
            }

            final List<EtcdNode> poolsRegistered = Optional.ofNullable(template.get(poolNodeName, true).getNode().getNodes()).orElse(Collections.emptyList());

            if (!poolsRegistered.isEmpty()) {
                EtcdNode poolOfPathSelected = poolsRegistered.stream().filter(p -> p.isDir() && p.getKey().equals(poolNodeName + "/" + poolName)).findAny().orElse(nodeEmpty);
                if (!"".equals(poolOfPathSelected.getKey())) {
                    logger.info("creating targetsPool (" +
                            "pathGlobHandler: " + pathGlobHandler.hashCode() + ", " +
                            "proxyHandler: " + proxyHandler.hashCode() + ", " +
                            "proxyClient: " + proxyClient.hashCode() + ", " +
                            "hostSelectorInicializer: " + hostSelectorInicializer.hashCode() + ")");

                    List<EtcdNode> targets = Optional.ofNullable(poolOfPathSelected.getNodes()).orElse(Collections.emptyList());
                    targets.forEach(target -> {
                        if (target.getKey().equals(poolNodeName + "/" + poolName + "/loadbalance")) {
                            hostSelectorInicializer.setHostSelector(HostSelectorAlgorithm.valueOf(target.getValue()).getHostSelector());
                            logger.info("LoadBalance algorithm: " + target.getValue());
                        } else {
                            proxyClient.addHost(URI.create(target.getValue()));
                            logger.info("added target " + target.getValue());
                        }
                    });
                } else {
                    logger.warn("pool " + poolName + " is empty [" + poolNodeName + "/" + poolName + "]");
                }
                int pathFromIndex = pathKey.lastIndexOf("/");
                String path = pathKey.substring(pathFromIndex + 1, pathKey.length());
                String pathDecoded = new String(Base64.getDecoder().decode(path));
                pathGlobHandler.addPath(pathDecoded, order, proxyHandler);
                proxyHandler.handleRequest(exchange);
                if (proxyClient.isHostsEmpty()) {
                    logger.error("hosts is empty");
                }
                return;
            }
        }

        this.defaultHandler.handleRequest(exchange);
    }

}

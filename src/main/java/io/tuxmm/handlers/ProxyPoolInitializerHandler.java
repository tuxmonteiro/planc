/**
 *
 */

package io.tuxmm.handlers;

import io.undertow.client.UndertowClient;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ProxyPoolInitializerHandler implements HttpHandler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final PathGlobHandler pathGlobHandler;
    private final List<String> targetPools = Collections.synchronizedList(Arrays.asList("http://127.0.0.1:8080".split(",")));
    private final LoadBalancingProxyClient.HostSelector hostSelectorInicializer = new HostSelectorInicializer();
    private final ExtendedLoadBalancingProxyClient proxyClient = new ExtendedLoadBalancingProxyClient(hostSelectorInicializer);
    private final HttpHandler defaultHandler = ResponseCodeHandler.HANDLE_500;
    private final String path;
    private ProxyHandler proxyHandler = new ProxyHandler(proxyClient, defaultHandler);

    ProxyPoolInitializerHandler(final PathGlobHandler pathGlobHandler, final String path) {
        this.pathGlobHandler = pathGlobHandler;
        this.path = path;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (proxyClient.isHostsNull(exchange)) {
            targetPools.forEach(target -> proxyClient.addHost(URI.create(target)));
            pathGlobHandler.addPath(path, proxyHandler);

            logger.info("creating targetsPool (" +
                            "pathGlobHandler: " + pathGlobHandler.hashCode() + ", " +
                            "proxyHandler: " + proxyHandler.hashCode() + ", " +
                            "proxyClient: " + proxyClient.hashCode() + ", " +
                            "hostSelectorInicializer: " + hostSelectorInicializer.hashCode() + ")");
        }

        proxyHandler.handleRequest(exchange);
    }

    private static class ExtendedLoadBalancingProxyClient extends LoadBalancingProxyClient {

        ExtendedLoadBalancingProxyClient(HostSelector hostSelectorInicializer) {
            super(UndertowClient.getInstance(), null, hostSelectorInicializer);
        }

        boolean isHostsNull(HttpServerExchange exchange) {
            return super.selectHost(exchange) == null;
        }
    }

    private class HostSelectorInicializer implements LoadBalancingProxyClient.HostSelector {
        @Override
        public int selectHost(LoadBalancingProxyClient.Host[] hosts) {
            return 0;
        }
    }
}

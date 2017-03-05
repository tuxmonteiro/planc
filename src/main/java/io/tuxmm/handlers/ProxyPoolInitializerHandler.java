/**
 *
 */

package io.tuxmm.handlers;

import io.tuxmm.Application;
import io.undertow.client.UndertowClient;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.util.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.URI;
import java.util.Set;

public class ProxyPoolInitializerHandler implements HttpHandler {

    private StringRedisTemplate template;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final PathGlobHandler pathGlobHandler;
    private final LoadBalancingProxyClient.HostSelector hostSelectorInicializer = new HostSelectorInicializer();
    private final ExtendedLoadBalancingProxyClient proxyClient = new ExtendedLoadBalancingProxyClient(hostSelectorInicializer);
    private final HttpHandler defaultHandler = ResponseCodeHandler.HANDLE_500;
    private final String path;
    private final int order;
    private ProxyHandler proxyHandler = new ProxyHandler(proxyClient, defaultHandler);

    ProxyPoolInitializerHandler(final PathGlobHandler pathGlobHandler, final String path, final int order) {
        this.pathGlobHandler = pathGlobHandler;
        this.path = path;
        this.order = order;
    }

    public ProxyPoolInitializerHandler setTemplate(final StringRedisTemplate template) {
        this.template = template;
        return this;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String host = exchange.getRequestHeaders().get(Headers.HOST_STRING).getFirst();

        if (proxyClient.isHostsNull(exchange)) {

            Set<String> targets = template.keys(Application.PREFIX + "@" + host + "@target@" + path + "@*");
            if (!targets.isEmpty()) {
                logger.info("creating targetsPool (" +
                        "pathGlobHandler: " + pathGlobHandler.hashCode() + ", " +
                        "proxyHandler: " + proxyHandler.hashCode() + ", " +
                        "proxyClient: " + proxyClient.hashCode() + ", " +
                        "hostSelectorInicializer: " + hostSelectorInicializer.hashCode() + ")");

                targets.forEach(v -> {
                    int fromIndex = v.lastIndexOf('@');
                    String target = v.substring(fromIndex + 1, v.length());
                    proxyClient.addHost(URI.create(target));
                    logger.info("added target " + target);
                });
                if (proxyClient.isHostsNull(exchange)) {
                    logger.error("hosts is null");
                }
                pathGlobHandler.addPath(path, order, proxyHandler);
                proxyHandler.handleRequest(exchange);
                return;
            }
        }

        this.defaultHandler.handleRequest(exchange);
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

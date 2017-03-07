/**
 *
 */

package io.github.tuxmonteiro.planc.handlers;

import io.github.tuxmonteiro.planc.Application;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zalando.boot.etcd.EtcdClient;
import org.zalando.boot.etcd.EtcdNode;

import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class PathInitializerHandler implements HttpHandler {

    private EtcdClient template;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final PathGlobHandler pathGlobHandler;
    private final Set<String> paths = Collections.synchronizedSet(new HashSet<>());

    PathInitializerHandler(final PathGlobHandler pathGlobHandler) {
        this.pathGlobHandler = pathGlobHandler;
    }

    public PathInitializerHandler setTemplate(final EtcdClient template) {
        this.template = template;
        return this;
    }

    @Override
    public synchronized void handleRequest(HttpServerExchange exchange) throws Exception {
        String host = exchange.getRequestHeaders().get(Headers.HOST_STRING).getFirst();
        final String virtualhostNodeName = "/" + Application.PREFIX + "/virtualhosts/" + host;
        final String pathNodeName = virtualhostNodeName + "/path";

        if (paths.isEmpty()) {
            List<EtcdNode> hostNodes = Optional.ofNullable(template.get(virtualhostNodeName).getNode().getNodes()).orElse(Collections.emptyList());
            boolean existPath = hostNodes.stream().filter(node -> node.getKey().equals(pathNodeName)).count() != 0;
            if (existPath) {
                final EtcdNode pathsNode = template.get(pathNodeName).getNode();
                final List<EtcdNode> pathsRegistered = Optional.ofNullable(pathsNode.getNodes()).orElse(Collections.emptyList());
                if (!pathsRegistered.isEmpty()) {
                    pathsRegistered.stream().filter(EtcdNode::isDir).forEach(keyComplete -> {
                        String pathKey = keyComplete.getKey();
                        int pathFromIndex = pathKey.lastIndexOf("/");
                        String path = pathKey.substring(pathFromIndex + 1, pathKey.length());
                        final EtcdNode nodeWithZero = new EtcdNode();
                        nodeWithZero.setValue("0");
                        int order = Integer.valueOf(Optional.ofNullable(keyComplete.getNodes()).orElse(Collections.emptyList()).stream()
                                .filter(n -> n.getKey().equals("order")).findAny().orElse(nodeWithZero).getValue());
                        String pathDecoded = new String(Base64.getDecoder().decode(path)).trim();
                        final ProxyPoolInitializerHandler proxyPoolInitializerHandler = new ProxyPoolInitializerHandler(pathGlobHandler, pathKey, order);
                        proxyPoolInitializerHandler.setTemplate(template);
                        pathGlobHandler.addPath(pathDecoded, order, proxyPoolInitializerHandler);
                        paths.add(pathDecoded);

                        logger.info("add path " + pathDecoded + " [" + order + "] (" +
                                "pathGlobHandler: " + pathGlobHandler.hashCode() + ", " +
                                "proxyPoolInitializerHandler: " + proxyPoolInitializerHandler.hashCode() + ")");
                    });
                    pathGlobHandler.setDefaultHandler(ResponseCodeHandler.HANDLE_500);
                    pathGlobHandler.handleRequest(exchange);
                    return;
                }
                logger.warn("pathRegistered is empty");
            } else {
                logger.warn(pathNodeName + " not found");
            }
        }
        String pathRequested = exchange.getRelativePath();
        logger.error(this + ": " + host + "@" + pathRequested);
        ResponseCodeHandler.HANDLE_500.handleRequest(exchange);
    }

    public synchronized void reset() {
        paths.clear();
    }

}

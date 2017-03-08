/**
 *
 */

package io.github.tuxmonteiro.planc.handlers;

import io.github.tuxmonteiro.planc.Application;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.NameVirtualHostHandler;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PathInitializerHandler implements HttpHandler {

    public enum RuleType {
        PATH,
        UNDEF
    }

    private EtcdClient template;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Set<String> paths = Collections.synchronizedSet(new HashSet<>());
    private final NameVirtualHostHandler nameVirtualHostHandler;

    PathInitializerHandler(final NameVirtualHostHandler nameVirtualHostHandler) {
        this.nameVirtualHostHandler = nameVirtualHostHandler;
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
            List<EtcdNode> hostNodes = Optional.ofNullable(template.get(virtualhostNodeName, true).getNode().getNodes()).orElse(Collections.emptyList());
            final EtcdNode pathNode = hostNodes.stream().filter(node -> node.getKey().equals(pathNodeName)).findFirst().orElse(new EtcdNode());
            if (pathNode.getKey() != null) {
                final List<EtcdNode> pathsRegistered = Optional.ofNullable(pathNode.getNodes()).orElse(Collections.emptyList());
                if (!pathsRegistered.isEmpty()) {
                    pathsRegistered.stream().filter(EtcdNode::isDir).forEach(keyComplete -> {
                        String pathKey = keyComplete.getKey();
                        int pathFromIndex = pathKey.lastIndexOf("/");
                        String path = pathKey.substring(pathFromIndex + 1, pathKey.length());
                        final EtcdNode nodeWithZero = new EtcdNode();
                        final EtcdNode nodeWithUndef = new EtcdNode();
                        nodeWithZero.setValue("0");
                        nodeWithUndef.setValue(RuleType.UNDEF.toString());
                        final Set<EtcdNode> nodes = Optional.ofNullable(keyComplete.getNodes()).orElse(Collections.emptyList()).stream().collect(Collectors.toSet());
                        int order = Integer.valueOf(nodes.stream().filter(n -> n.getKey().equals(pathKey + "/order")).findAny().orElse(nodeWithZero).getValue());
                        String type = nodes.stream().filter(n -> n.getKey().equals(pathKey + "/type")).findAny().orElse(nodeWithUndef).getValue();
                        String pathDecoded = new String(Base64.getDecoder().decode(path)).trim();
                        logger.info("add path " + pathDecoded + " [order:" + order + ", type:"+ type +"]");
                        if (RuleType.valueOf(type) == RuleType.PATH) {
                            final PathGlobHandler pathGlobHandler = new PathGlobHandler();
                            pathGlobHandler.setDefaultHandler(ResponseCodeHandler.HANDLE_500);
                            final ProxyPoolInitializerHandler proxyPoolInitializerHandler = new ProxyPoolInitializerHandler(pathGlobHandler, pathKey, order);
                            proxyPoolInitializerHandler.setTemplate(template);
                            pathGlobHandler.addPath(pathDecoded, order, proxyPoolInitializerHandler);
                            nameVirtualHostHandler.addHost(host, pathGlobHandler);

                            paths.add(pathDecoded);

                            logger.info("pathGlobHandler: " + pathGlobHandler.hashCode() + ", " +
                                    "proxyPoolInitializerHandler: " + proxyPoolInitializerHandler.hashCode() + ")");
                        } else {
                            nameVirtualHostHandler.setDefaultHandler(ResponseCodeHandler.HANDLE_500);
                        }
                    });
                    nameVirtualHostHandler.handleRequest(exchange);
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

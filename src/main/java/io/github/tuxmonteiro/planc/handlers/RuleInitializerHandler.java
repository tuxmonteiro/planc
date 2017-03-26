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

public class RuleInitializerHandler implements HttpHandler {

    public enum RuleType {
        PATH,
        UNDEF
    }

    private EtcdClient template;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Set<String> rules = Collections.synchronizedSet(new HashSet<>());
    private final NameVirtualHostHandler nameVirtualHostHandler;

    RuleInitializerHandler(final NameVirtualHostHandler nameVirtualHostHandler) {
        this.nameVirtualHostHandler = nameVirtualHostHandler;
    }

    public RuleInitializerHandler setTemplate(final EtcdClient template) {
        this.template = template;
        return this;
    }

    @Override
    public synchronized void handleRequest(HttpServerExchange exchange) throws Exception {
        String host = exchange.getRequestHeaders().get(Headers.HOST_STRING).getFirst();
        final String virtualhostNodeName = "/" + Application.PREFIX + "/virtualhosts/" + host;
        final String rulesNodeName = virtualhostNodeName + "/rules";

        if (rules.isEmpty()) {
            List<EtcdNode> hostNodes = Optional.ofNullable(template.get(virtualhostNodeName, true).getNode().getNodes()).orElse(Collections.emptyList());
            final EtcdNode ruleNode = hostNodes.stream().filter(node -> node.getKey().equals(rulesNodeName)).findFirst().orElse(new EtcdNode());
            if (ruleNode.getKey() != null) {
                final List<EtcdNode> rulesRegistered = Optional.ofNullable(ruleNode.getNodes()).orElse(Collections.emptyList());
                if (!rulesRegistered.isEmpty()) {
                    rulesRegistered.stream().filter(EtcdNode::isDir).forEach(keyComplete -> {
                        String ruleKey = keyComplete.getKey();
                        int ruleFromIndex = ruleKey.lastIndexOf("/");
                        String rule = ruleKey.substring(ruleFromIndex + 1, ruleKey.length());
                        final EtcdNode nodeWithZero = new EtcdNode();
                        final EtcdNode nodeWithUndef = new EtcdNode();
                        nodeWithZero.setValue("0");
                        nodeWithUndef.setValue(RuleType.UNDEF.toString());
                        final Set<EtcdNode> nodes = Optional.ofNullable(keyComplete.getNodes()).orElse(Collections.emptyList()).stream().collect(Collectors.toSet());
                        int order = Integer.valueOf(nodes.stream().filter(n -> n.getKey().equals(ruleKey + "/order")).findAny().orElse(nodeWithZero).getValue());
                        String type = nodes.stream().filter(n -> n.getKey().equals(ruleKey + "/type")).findAny().orElse(nodeWithUndef).getValue();
                        String ruleDecoded = new String(Base64.getDecoder().decode(rule)).trim();
                        logger.info("add rule " + ruleDecoded + " [order:" + order + ", type:"+ type +"]");
                        if (RuleType.valueOf(type) == RuleType.PATH) {
                            final HttpHandler pathGlobHandler = new PathGlobHandler(ResponseCodeHandler.HANDLE_500);
                            final ProxyPoolInitializerHandler proxyPoolInitializerHandler = new ProxyPoolInitializerHandler(pathGlobHandler, ruleKey, order);
                            proxyPoolInitializerHandler.setTemplate(template);
                            ((PathGlobHandler)pathGlobHandler).addPath(ruleDecoded, order, proxyPoolInitializerHandler);
                            nameVirtualHostHandler.addHost(host, pathGlobHandler);

                            rules.add(ruleDecoded);

                            logger.info("Rule httpHandler: (" + pathGlobHandler.getClass().getSimpleName() + ") " + pathGlobHandler.hashCode() + ", " +
                                    "proxyPoolInitializerHandler: " + proxyPoolInitializerHandler.hashCode() + ")");
                        } else {
                            nameVirtualHostHandler.setDefaultHandler(ResponseCodeHandler.HANDLE_500);
                        }
                    });
                    nameVirtualHostHandler.handleRequest(exchange);
                    return;
                }
                logger.warn("ruleRegistered is empty");
            } else {
                logger.warn(rulesNodeName + " not found");
            }
        }
        String pathRequested = exchange.getRelativePath();
        logger.error(this + ": " + host + "@" + pathRequested);
        ResponseCodeHandler.HANDLE_500.handleRequest(exchange);
    }

    public synchronized void reset() {
        rules.clear();
    }

}
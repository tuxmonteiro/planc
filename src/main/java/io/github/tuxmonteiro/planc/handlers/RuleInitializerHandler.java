/**
 *
 */

package io.github.tuxmonteiro.planc.handlers;

import io.github.tuxmonteiro.planc.services.ExternalData;
import io.github.tuxmonteiro.planc.services.StatsdClient;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.IPAddressAccessControlHandler;
import io.undertow.server.handlers.NameVirtualHostHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.util.StatusCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zalando.boot.etcd.EtcdNode;

import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.github.tuxmonteiro.planc.services.ExternalData.VIRTUALHOSTS_KEY;

public class RuleInitializerHandler implements HttpHandler {

    public enum RuleType {
        PATH,
        UNDEF
    }

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Set<String> rules = Collections.synchronizedSet(new HashSet<>());
    private final NameVirtualHostHandler nameVirtualHostHandler;
    private ExternalData data;
    private StatsdClient statsdClient;

    RuleInitializerHandler(final NameVirtualHostHandler nameVirtualHostHandler) {
        this.nameVirtualHostHandler = nameVirtualHostHandler;
    }

    public RuleInitializerHandler setExternalData(final ExternalData externalData) {
        this.data = externalData;
        return this;
    }

    public RuleInitializerHandler setStatsdClient(StatsdClient statsdClient) {
        this.statsdClient = statsdClient;
        return this;
    }

    @Override
    public synchronized void handleRequest(HttpServerExchange exchange) throws Exception {
        String host = exchange.getHostName();
        final String virtualhostNodeName = VIRTUALHOSTS_KEY + "/" + host;
        final String rulesNodeName = virtualhostNodeName + "/rules";

        if (rules.isEmpty()) {
            List<EtcdNode> hostNodes = data.listFrom(virtualhostNodeName, true);
            final EtcdNode ruleNode = hostNodes.stream().filter(node -> node.getKey().equals(rulesNodeName)).findFirst().orElse(data.nullNode());
            if (ruleNode.getKey() != null) {
                final List<EtcdNode> rulesRegistered = Optional.ofNullable(ruleNode.getNodes()).orElse(Collections.emptyList());
                if (!rulesRegistered.isEmpty()) {
                    rulesRegistered.stream().filter(EtcdNode::isDir).forEach((EtcdNode keyComplete) -> {
                        String ruleKey = keyComplete.getKey();
                        int ruleFromIndex = ruleKey.lastIndexOf("/");
                        String rule = ruleKey.substring(ruleFromIndex + 1, ruleKey.length());
                        final EtcdNode nodeWithZero = new EtcdNode();
                        final EtcdNode nodeWithUndef = new EtcdNode();
                        nodeWithZero.setValue("0");
                        nodeWithUndef.setValue(RuleType.UNDEF.toString());
                        final Set<EtcdNode> nodes = new HashSet<>(Optional.ofNullable(keyComplete.getNodes()).orElse(Collections.emptyList()));
                        int order = Integer.valueOf(nodes.stream().filter(n -> n.getKey().equals(ruleKey + "/order")).findAny().orElse(nodeWithZero).getValue());
                        String type = nodes.stream().filter(n -> n.getKey().equals(ruleKey + "/type")).findAny().orElse(nodeWithUndef).getValue();
                        String ruleDecoded = new String(Base64.getDecoder().decode(rule)).trim();
                        logger.info("add rule " + ruleDecoded + " [order:" + order + ", type:"+ type +"]");
                        if (RuleType.valueOf(type) == RuleType.PATH) {
                            final HttpHandler pathGlobHandler = new PathGlobHandler(ResponseCodeHandler.HANDLE_500);
                            final ProxyPoolInitializerHandler proxyPoolInitializerHandler = new ProxyPoolInitializerHandler(pathGlobHandler, ruleKey, order);
                            proxyPoolInitializerHandler.setExternalData(data).setStatsdClient(statsdClient);
                            ((PathGlobHandler)pathGlobHandler).addPath(ruleDecoded, order, proxyPoolInitializerHandler);
                            final EtcdNode allow = hostNodes.stream().filter(node -> node.getKey().equals(virtualhostNodeName + "/allow")).findFirst().orElse(new EtcdNode());
                            final HttpHandler ruleTargetHandler;
                            if (allow.getKey() != null) {
                                ruleTargetHandler = new IPAddressAccessControlHandler(pathGlobHandler, StatusCodes.FORBIDDEN);
                                Arrays.stream(allow.getValue().split(",")).forEach(((IPAddressAccessControlHandler)ruleTargetHandler)::addAllow);
                            } else {
                                ruleTargetHandler = pathGlobHandler;
                            }
                            nameVirtualHostHandler.addHost(host, ruleTargetHandler);

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

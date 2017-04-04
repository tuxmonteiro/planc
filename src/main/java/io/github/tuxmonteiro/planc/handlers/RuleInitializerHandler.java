/**
 *
 */

package io.github.tuxmonteiro.planc.handlers;

import io.github.tuxmonteiro.planc.services.ExternalData;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.IPAddressAccessControlHandler;
import io.undertow.server.handlers.NameVirtualHostHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.zalando.boot.etcd.EtcdNode;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.github.tuxmonteiro.planc.services.ExternalData.VIRTUALHOSTS_KEY;

@Component
@Scope("prototype")
public class RuleInitializerHandler implements HttpHandler {

    public enum RuleType {
        PATH,
        UNDEF
    }

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Set<String> rules = Collections.synchronizedSet(new HashSet<>());

    private final ExternalData data;
    private final NameVirtualHostHandler nameVirtualHostHandler;
    private final ApplicationContext context;

    @Autowired
    public RuleInitializerHandler(final ExternalData data, final NameVirtualHostHandler nameVirtualHostHandler, final ApplicationContext context) {
        this.data = data;
        this.nameVirtualHostHandler = nameVirtualHostHandler;
        this.context = context;
    }

    @Override
    public synchronized void handleRequest(HttpServerExchange exchange) throws Exception {
        String host = exchange.getHostName();
        final String rulesNodeName = VIRTUALHOSTS_KEY + "/" + host + "/rules";

        if (rules.isEmpty()) {
            final EtcdNode ruleNode = findRuleNode(rulesNodeName);
            if (ruleNode.getKey() != null) {
                final List<EtcdNode> rulesRegistered = data.listFrom(ruleNode);
                if (!rulesRegistered.isEmpty()) {
                    loadAllRules(host, rulesRegistered);
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

    private void loadAllRules(String host, final List<EtcdNode> rulesRegistered) {
        for (EtcdNode keyComplete : rulesRegistered) {
            if (keyComplete.isDir()) {
                String ruleKey = keyComplete.getKey();
                Integer order = extractRuleOrder(ruleKey);
                String type = extractRuleType(ruleKey);
                String rule = extractRule(ruleKey);
                String ruleDecoded = extractRuleDecoded(rule);

                logger.info("add rule " + ruleDecoded + " [order:" + order + ", type:" + type + "]");

                if (RuleType.valueOf(type) == RuleType.PATH) {
                    final HttpHandler pathGlobHandler = newPathGlobHandler();
                    final ProxyPoolInitializerHandler proxyPoolInitializerHandler = newProxyPoolInitializerHandler(ruleKey, order, pathGlobHandler);
                    ((PathGlobHandler) pathGlobHandler).addPath(ruleDecoded, order, proxyPoolInitializerHandler);
                    final HttpHandler ruleTargetHandler = defineRuleTargetHandler(pathGlobHandler, VIRTUALHOSTS_KEY + "/" + host);
                    nameVirtualHostHandler.addHost(host, ruleTargetHandler);

                    rules.add(ruleDecoded);

                    logger.info("Rule httpHandler: (" + pathGlobHandler.getClass().getSimpleName() + ") " + pathGlobHandler.hashCode() + ", " +
                            "proxyPoolInitializerHandler: " + proxyPoolInitializerHandler.hashCode() + ")");
                } else {
                    nameVirtualHostHandler.setDefaultHandler(ResponseCodeHandler.HANDLE_500);
                }
            }
        }
    }

    private HttpHandler defineRuleTargetHandler(final HttpHandler pathGlobHandler, final String virtualhostNodeName) {
        final EtcdNode allow = extractAllowAttribute(virtualhostNodeName);
        final HttpHandler ruleTargetHandler;
        if (allow.getKey() != null) {
            ruleTargetHandler = newIpAddressAccessControlHandler(pathGlobHandler);
            Arrays.stream(allow.getValue().split(",")).forEach(((IPAddressAccessControlHandler) ruleTargetHandler)::addAllow);
        } else {
            ruleTargetHandler = pathGlobHandler;
        }
        return ruleTargetHandler;
    }

    private PathGlobHandler newPathGlobHandler() {
        return context.getBean(PathGlobHandler.class);
    }

    private IPAddressAccessControlHandler newIpAddressAccessControlHandler(final HttpHandler pathGlobHandler) {
        return context.getBean(IPAddressAccessControlHandler.class).setNext(pathGlobHandler);
    }

    private ProxyPoolInitializerHandler newProxyPoolInitializerHandler(String ruleKey, Integer order, final HttpHandler pathGlobHandler) {
        return context.getBean(ProxyPoolInitializerHandler.class).setParentHandler(pathGlobHandler).setRuleKey(ruleKey).setRuleOrder(order);
    }

    private EtcdNode extractAllowAttribute(String virtualhostNodeName) {
        return data.node(virtualhostNodeName + "/allow");
    }

    private EtcdNode findRuleNode(String rulesNodeName) {
        return data.node(rulesNodeName);
    }

    private String extractRule(String ruleKey) {
        int ruleFromIndex = ruleKey.lastIndexOf("/");
        return ruleKey.substring(ruleFromIndex + 1, ruleKey.length());
    }

    private String extractRuleDecoded(String rule) {
        return new String(Base64.getDecoder().decode(rule.getBytes(Charset.defaultCharset())), Charset.defaultCharset()).trim();
    }

    private String extractRuleType(String ruleKey) {
        return Optional.ofNullable(data.node(ruleKey + "/type").getValue()).orElse(RuleType.PATH.toString());
    }

    private Integer extractRuleOrder(String ruleKey) {
        return Integer.valueOf(data.node(ruleKey + "/order", ExternalData.GenericNode.ZERO).getValue());
    }

    public synchronized void reset() {
        rules.clear();
    }

}

/**
 *
 */

package io.github.tuxmonteiro.planc.services;

import io.github.tuxmonteiro.planc.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.zalando.boot.etcd.EtcdClient;
import org.zalando.boot.etcd.EtcdException;
import org.zalando.boot.etcd.EtcdNode;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

@Service
public class AutoResetter {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Router router;
    private final EtcdClient template;
    private final String applicationPrefix = "/" + Application.PREFIX;

    @Autowired
    public AutoResetter(final Router router, final EtcdClient template) {
        this.router = router;
        this.template = template;
    }

    @PostConstruct
    public void run() {
        logger.info(this.getClass().getSimpleName() + " started");
    }

    @Scheduled(fixedRate = 5000)
    public void periodicReset() {
        boolean existPrefixRoot;
        try {
            existPrefixRoot = Optional.ofNullable(template.get("/").getNode().getNodes()).orElse(Collections.emptyList())
                    .stream().filter(node -> node.getKey().equals(applicationPrefix)).count() != 0;
        } catch (EtcdException|ResourceAccessException e) {
            logger.error(e.getMessage());
            return;
        }
        if (!existPrefixRoot) {
            logger.error(applicationPrefix + " not exist");
            return;
        }
        if (resetAll()) return;

        resetByVirtualhost();
    }

    private void resetByVirtualhost() {
        EtcdNode virtualhostNode;
        try {
            virtualhostNode = template.get(applicationPrefix + "/virtualhosts", true).getNode();
        } catch (EtcdException e) {
            logger.error(e.getMessage());
            return;
        }
        final List<EtcdNode> virtualhostNodes = Optional.ofNullable(virtualhostNode.getNodes()).orElse(Collections.emptyList());

        virtualhostNodes.forEach(node -> {
            if (hasReset(node)) {
                String virtualhost = getResetNodeStream(node).findAny().get().getValue();
                if (node.getKey().endsWith("/" + virtualhost)) {
                    router.reset(virtualhost);
                } else {
                    logger.warn("vh reset aborted: " + virtualhost + " not match with parent key " + node.getKey());
                }
            }
        });
    }

    private boolean hasReset(final EtcdNode node) {
        return getResetNodeStream(node).count() != 0;
    }

    private Stream<EtcdNode> getResetNodeStream(final EtcdNode node) {
        return Optional.ofNullable(node.getNodes()).orElse(Collections.emptyList())
                .stream().filter(v -> v.getKey().equals(node.getKey() + "/reset"));
    }

    private boolean resetAll() {
        final String keyResetAll = applicationPrefix + "/reset_all";
        final AtomicBoolean resetAll = new AtomicBoolean(false);
        try {
            final EtcdNode prefixNode = template.get(applicationPrefix).getNode();
            final List<EtcdNode> nodes = Optional.ofNullable(prefixNode.getNodes()).orElse(Collections.emptyList());
            logger.info("checking reset request");
            nodes.forEach(node -> {
                if (!node.isDir() && node.getKey().equals(keyResetAll)) {
                    router.resetAll();
                    resetAll.set(true);
                }
            });
        } catch (EtcdException e) {
            logger.error(e.getMessage());
        }
        return resetAll.get();
    }

}

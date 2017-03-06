/**
 *
 */

package io.tuxmm.services;

import io.tuxmm.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
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

@Service
@EnableScheduling
public class AutoResetter {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Router router;
    private final EtcdClient template;

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
                    .stream().filter(node -> node.getKey().equals("/" + Application.PREFIX)).count() != 0;
        } catch (EtcdException|ResourceAccessException e) {
            logger.error(e.getMessage());
            return;
        }
        if (!existPrefixRoot) {
            logger.error("/" + Application.PREFIX + " not exist");
            return;
        }
        final String keyResetAll = "/" + Application.PREFIX + "/reset_all";
        final EtcdNode prefixNode;
        try {
            prefixNode = template.get("/" + Application.PREFIX).getNode();
        } catch (EtcdException e) {
            logger.error(e.getMessage());
            return;
        }
        final List<EtcdNode> nodes = Optional.ofNullable(prefixNode.getNodes()).orElse(Collections.emptyList());
        logger.info("checking reset request");
        nodes.forEach(node -> {
            if (!node.isDir() && node.getKey().equals(keyResetAll)) {
                try {
                    template.delete(keyResetAll);
                } catch (EtcdException e) {
                    logger.error(e.getMessage());
                } finally {
                    router.resetAll();
                }
            }
            if (node.isDir() && node.getKey().equals("/" + Application.PREFIX + "/reset")) {
                node.getNodes().forEach(n -> {
                    String virtualhost = n.getValue();
                    try {
                        template.delete(n.getKey());
                    } catch (EtcdException e) {
                        logger.error(e.getMessage());
                    } finally {
                        router.reset(virtualhost);
                    }
                });
            }
        });
    }

}

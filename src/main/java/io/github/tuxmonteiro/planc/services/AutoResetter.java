/**
 *
 */

package io.github.tuxmonteiro.planc.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.zalando.boot.etcd.EtcdNode;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static io.github.tuxmonteiro.planc.services.ExternalData.*;

@Service
public class AutoResetter {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Router router;
    private final ExternalData data;

    @Autowired
    public AutoResetter(final Router router, final ExternalData externalData) {
        this.router = router;
        this.data = externalData;
    }

    @PostConstruct
    public void run() {
        logger.info(this.getClass().getSimpleName() + " started");
    }

    @Scheduled(fixedRate = 5000)
    public void periodicReset() {
        boolean existPrefixRoot = data.listFrom(ROOT_KEY).stream().filter(node -> node.getKey().equals(PREFIX_KEY)).count() != 0;
        if (!existPrefixRoot) {
            logger.error(PREFIX_KEY + " not exist");
            return;
        }
        if (resetAll()) return;

        resetByVirtualhost();
    }

    private void resetByVirtualhost() {
        final EtcdNode virtualhostNode = data.node(VIRTUALHOSTS_KEY, true);
        final List<EtcdNode> virtualhostNodes = Optional.ofNullable(virtualhostNode.getNodes()).orElse(Collections.emptyList());

        virtualhostNodes.forEach(node -> {
            if (hasReset(node)) {
                String virtualhost = getResetNodeStream(node).findAny().orElse(GenericNode.UNDEF.get()).getValue();
                if (data.endsWith(node,"/" + virtualhost)) {
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
        return data.listFrom(node).stream().filter(v -> v.getKey().equals(node.getKey() + "/reset"));
    }

    private boolean resetAll() {
        final AtomicBoolean resetAll = new AtomicBoolean(false);
        final List<EtcdNode> nodes = data.listFrom(PREFIX_KEY);
        logger.info("checking reset request");
        nodes.forEach(node -> {
            if (!node.isDir() && node.getKey().equals(RESET_ALL_KEY)) {
                router.resetAll();
                resetAll.set(true);
            }
        });
        return resetAll.get();
    }

}

/**
 *
 */

package io.tuxmm.services;

import io.tuxmm.handlers.VirtualHostInitializerHandler;
import io.undertow.Undertow;
import io.undertow.server.handlers.NameVirtualHostHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
public class Router {

    private final StringRedisTemplate template;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final NameVirtualHostHandler nameVirtualHostHandler = new NameVirtualHostHandler();
    private final VirtualHostInitializerHandler virtualHostInitializerHandler = new VirtualHostInitializerHandler(nameVirtualHostHandler);

    @Autowired
    public Router(StringRedisTemplate template) {
        this.template = template;
    }

    @PostConstruct
    public void run() {
        logger.info(this.getClass().getSimpleName() + " started");

        nameVirtualHostHandler.setDefaultHandler(virtualHostInitializerHandler.setTemplate(template));
        Undertow undertow = Undertow.builder().addHttpListener(8000, "0.0.0.0", nameVirtualHostHandler)
                .setIoThreads(4)
                .build();
        undertow.start();
    }

    public void resetAll() {
        virtualHostInitializerHandler.resetAll();
    }

    public void reset(String virtualhost) {
        virtualHostInitializerHandler.reset(virtualhost);
    }
}

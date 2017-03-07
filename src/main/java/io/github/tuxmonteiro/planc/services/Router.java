/**
 *
 */

package io.github.tuxmonteiro.planc.services;

import io.github.tuxmonteiro.planc.handlers.VirtualHostInitializerHandler;
import io.undertow.Undertow;
import io.undertow.server.handlers.NameVirtualHostHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.xnio.Options;
import org.zalando.boot.etcd.EtcdClient;

import javax.annotation.PostConstruct;

@Service
public class Router {

    private final EtcdClient template;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final NameVirtualHostHandler nameVirtualHostHandler = new NameVirtualHostHandler();
    private final VirtualHostInitializerHandler virtualHostInitializerHandler = new VirtualHostInitializerHandler(nameVirtualHostHandler);

    @Autowired
    public Router(EtcdClient template) {
        this.template = template;
    }

    @PostConstruct
    public void run() {
        logger.info(this.getClass().getSimpleName() + " started");

        nameVirtualHostHandler.setDefaultHandler(virtualHostInitializerHandler.setTemplate(template));
        Undertow undertow = Undertow.builder().addHttpListener(8000, "0.0.0.0", nameVirtualHostHandler)
                .setIoThreads(4)
                .setWorkerThreads(4 * 8)
                .setBufferSize(16384)
                .setDirectBuffers(true)
                .setSocketOption(Options.BACKLOG, 65535)
                .setSocketOption(Options.KEEP_ALIVE, true)
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setSocketOption(Options.TCP_NODELAY, true)
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

/**
 *
 */

package io.github.tuxmonteiro.planc.services;

import io.github.tuxmonteiro.planc.handlers.VirtualHostInitializerHandler;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.NameVirtualHostHandler;
import io.undertow.util.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.xnio.Options;

import javax.annotation.PostConstruct;

@Service
public class Router {

    private final ExternalData data;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final NameVirtualHostHandler nameVirtualHostHandler = new NameVirtualHostHandler();
    private final VirtualHostInitializerHandler virtualHostInitializerHandler = new VirtualHostInitializerHandler(nameVirtualHostHandler);
    private Undertow undertow = null;

    @Autowired
    public Router(final ExternalData externalData) {
        this.data = externalData;
    }

    @PostConstruct
    public void run() {
        logger.info(this.getClass().getSimpleName() + " started");

        nameVirtualHostHandler.addHost("__ping__", pingHandler());

        nameVirtualHostHandler.setDefaultHandler(virtualHostInitializerHandler.setExternalData(data));
        undertow = Undertow.builder().addHttpListener(8000, "0.0.0.0", nameVirtualHostHandler)
                .setIoThreads(4)
                .setWorkerThreads(4 * 8)
                .setBufferSize(16384)
                .setDirectBuffers(true)
                .setSocketOption(Options.BACKLOG, 65535)
                .setSocketOption(Options.KEEP_ALIVE, true)
                .setSocketOption(Options.REUSE_ADDRESSES, true)
                .setSocketOption(Options.TCP_NODELAY, true)
                .setServerOption(UndertowOptions.RECORD_REQUEST_START_TIME, true)
                .setServerOption(UndertowOptions.ENABLE_STATISTICS, true)
                .build();
    }

    public Undertow getUndertow() {
        return undertow;
    }

    private HttpHandler pingHandler() {
        return exchange -> {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
            exchange.getResponseHeaders().put(Headers.SERVER, "PLANC");
            exchange.getResponseSender().send("0");
        };
    }

    public void resetAll() {
        virtualHostInitializerHandler.resetAll();
    }

    public void reset(String virtualhost) {
        virtualHostInitializerHandler.reset(virtualhost);
    }
}

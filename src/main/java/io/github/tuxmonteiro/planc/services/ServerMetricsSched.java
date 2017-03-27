package io.github.tuxmonteiro.planc.services;

import io.undertow.Undertow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ServerMetricsSched {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Undertow undertow;

    public ServerMetricsSched(@Autowired Router router) {
        undertow = router.getUndertow();
    }

    @Scheduled(fixedRate = 5000)
    private void showMetrics() {
        undertow.getListenerInfo().forEach(listenerInfo -> {
            logger.info("{\"undertow_listener_info\": {" +
                    "\"protocol\": " + listenerInfo.getProtcol() + ", " +
                    "\"active_connections\": " + listenerInfo.getConnectorStatistics().getActiveConnections() + ", " +
                    "\"active_requests\": " + listenerInfo.getConnectorStatistics().getActiveRequests() + ", " +
                    "\"bytes_received\": " + listenerInfo.getConnectorStatistics().getBytesReceived() + ", " +
                    "\"bytes_sent\": " + listenerInfo.getConnectorStatistics().getBytesSent() + ", " +
                    "\"error_count\": " + listenerInfo.getConnectorStatistics().getErrorCount() + ", " +
                    "\"request_count\": " + listenerInfo.getConnectorStatistics().getRequestCount() + "} }");
        });
    }
}

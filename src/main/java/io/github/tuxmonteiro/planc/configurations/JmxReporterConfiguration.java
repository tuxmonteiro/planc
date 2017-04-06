/**
 *
 */

package io.github.tuxmonteiro.planc.configurations;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import io.github.tuxmonteiro.planc.services.Router;
import io.undertow.Undertow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToLongFunction;

@Configuration
public class JmxReporterConfiguration {

    private final Router router;

    private final AtomicLong lastRequestCount = new AtomicLong(0L);
    private final AtomicLong lastBytesReceived = new AtomicLong(0L);
    private final AtomicLong lastBytesSent = new AtomicLong(0L);

    @Autowired
    public JmxReporterConfiguration(final Router router) {
        this.router = router;
    }

    @Bean
    public MetricRegistry metricRegistry() {
        final MetricRegistry register = new MetricRegistry();
        register.register(MetricRegistry.name(Undertow.class, "active_connections"),
                (Gauge<Long>) () -> router.getUndertow().getListenerInfo().stream()
                                                .mapToLong(l -> l.getConnectorStatistics().getActiveConnections()).sum());
        register.register(MetricRegistry.name(Undertow.class, "active_requests"),
                (Gauge<Long>) () -> router.getUndertow().getListenerInfo().stream()
                                                .mapToLong(l -> l.getConnectorStatistics().getActiveRequests()).sum());
        register.register(MetricRegistry.name(Undertow.class, "request_count"), (Gauge<Long>) this::getRequestCount);
        register.register(MetricRegistry.name(Undertow.class, "bytes_received"), (Gauge<Long>) this::getBytesReceived);
        register.register(MetricRegistry.name(Undertow.class, "bytes_sent"), (Gauge<Long>) this::getBytesSent);
        final JmxReporter jmxReporter = JmxReporter.forRegistry(register).build();
        jmxReporter.start();
        return register;
    }

    private long extractDelta(final AtomicLong last, final ToLongFunction<Undertow.ListenerInfo> longFunction) {
        long start = System.nanoTime();
        double localLast = last.get() * 1.0;
        double current = router.getUndertow().getListenerInfo().stream().mapToLong(longFunction).sum() * 1.0;
        long end = System.nanoTime();
        last.set((long) current);
        return Math.round((current * (end/start)) - localLast);
    }

    private long getRequestCount() {
        return extractDelta(lastRequestCount, l -> l.getConnectorStatistics().getRequestCount());
    }

    private long getBytesReceived() {
        return extractDelta(lastBytesReceived, l -> l.getConnectorStatistics().getBytesReceived());
    }

    private long getBytesSent() {
        return extractDelta(lastBytesSent, l -> l.getConnectorStatistics().getBytesSent());
    }
}

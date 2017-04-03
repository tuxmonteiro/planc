/**
 *
 */

package io.github.tuxmonteiro.planc.services;

import com.timgroup.statsd.NonBlockingStatsDClient;
import io.github.tuxmonteiro.planc.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StatsdClient {

    private static final String STATSD_PREFIX = System.getProperty("STATSD_PREFIX", Application.PREFIX);
    private static final String STATSD_HOST   = System.getProperty("STATSD_HOST", "127.0.0.1");
    private static final int    STATSD_PORT   = Integer.parseInt(System.getProperty("STATSD_PORT", "8125"));

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final NonBlockingStatsDClient client = new NonBlockingStatsDClient(STATSD_PREFIX, STATSD_HOST, STATSD_PORT);

    public void incr(String metricName, int step, double rate) {
        count(metricName, step, rate);
    }

    public void incr(String metricName) {
        incr(metricName, 1, 1.0);
    }

    public void incr(String metricName, int step) {
        incr(metricName, step, 1.0);
    }

    public void incr(String metricName, double rate) {
        incr(metricName, 1, rate);
    }

    public void decr(String metricName, int step, double rate) {
        client.count(metricName, -1L * step, rate);
    }

    public void decr(String metricName) {
        decr(metricName, 1, 1.0);
    }

    public void decr(String metricName, int step) {
        decr(metricName, step, 1.0);
    }

    public void decr(String metricName, double rate) {
        decr(metricName, 1, rate);
    }

    public void count(String metricName, int value, double rate) {
        client.count(metricName, value, rate);
    }

    public void count(String metricName, int value) {
        count(metricName, value, 1.0);
    }

    public void gauge(String metricName, double value, double rate) {
        client.recordGaugeValue(metricName, value);
    }

    public void gauge(String metricName, double value) {
        gauge(metricName, value, 1.0);
    }

    public void set(String metricName, String value, double rate) {
        client.recordSetEvent(metricName, value);
    }

    public void set(String metricName, String value) {
        set(metricName, value, 1.0);
    }

    public void timing(String metricName, long value, double rate) {
        client.recordExecutionTime(metricName, value, rate);
    }

    public void timing(String metricName, long value) {
        timing(metricName, value, 1.0);
    }
}

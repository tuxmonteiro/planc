/**
 *
 */
package io.github.tuxmonteiro.planc.handlers;

import io.github.tuxmonteiro.planc.client.hostselectors.ClientStatisticsMarker;
import io.github.tuxmonteiro.planc.client.hostselectors.HostSelector;
import io.github.tuxmonteiro.planc.services.StatsdClient;
import io.undertow.attribute.ExchangeAttribute;
import io.undertow.attribute.ExchangeAttributes;
import io.undertow.attribute.ResponseTimeAttribute;
import io.undertow.attribute.SubstituteEmptyWrapper;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.proxy.ProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpHeaders;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExtendedProxyHandler implements HttpHandler, ProcessorLocalStatusCode {

    private static final int MAX_REQUEST_TIME = Integer.MAX_VALUE - 1;
    private static final String UNKNOWN = "UNKNOWN";
    private static final String REAL_DEST = "#REAL_DEST#";
    private static final String LOGPATTERN = "%a\t%v\t%r\t-\t-\tLocal:\t%s\t*-\t%B\t%D\tProxy:\t"+ REAL_DEST +"\t%s\t-\t%b\t-\t-" +
            "\tAgent:\t%{i,User-Agent}\tFwd:\t%{i,X-Forwarded-For}";

    private final Log logger = LogFactory.getLog(this.getClass());

    private final ExchangeAttribute tokens = ExchangeAttributes.parser(getClass().getClassLoader(), new SubstituteEmptyWrapper("-")).parse(LOGPATTERN);
    private final AccessLogCompletionListener accessLogCompletionListener = new AccessLogCompletionListener();
    private final StatsdCompletionListener statsdCompletionListener = new StatsdCompletionListener();
    private final ResponseTimeAttribute responseTimeAttribute = new ResponseTimeAttribute(TimeUnit.MILLISECONDS);
    private final ProxyHandler proxyHandler;
    private StatsdClient statsdClient;

    public ExtendedProxyHandler(ProxyClient proxyClient, int maxRequestTime, HttpHandler next) {
        proxyHandler = new ProxyHandler(proxyClient, maxRequestTime, next);
    }

    public ExtendedProxyHandler(ProxyClient proxyClient, HttpHandler defaultHandler) {
        proxyHandler = new ProxyHandler(proxyClient, defaultHandler);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        exchange.addExchangeCompleteListener(accessLogCompletionListener);
        exchange.addExchangeCompleteListener(statsdCompletionListener);
        proxyHandler.handleRequest(exchange);
    }

    public ExtendedProxyHandler setStatsdClient(final StatsdClient statsdClient) {
        this.statsdClient = statsdClient;
        return this;
    }

    private class AccessLogCompletionListener implements ExchangeCompletionListener {

        @Override
        public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
            try {
                final String tempRealDest = exchange.getAttachment(HostSelector.REAL_DEST);
                String realDest = tempRealDest != null ? tempRealDest : UNKNOWN;
                String message = tokens.readAttribute(exchange);
                int realStatus = exchange.getStatusCode();
                long responseBytesSent = exchange.getResponseBytesSent();
                final Integer responseTime = Math.round(Float.parseFloat(responseTimeAttribute.readAttribute(exchange)));
                int fakeStatusCode = getFakeStatusCode(tempRealDest, realStatus, responseBytesSent, responseTime, MAX_REQUEST_TIME);
                if (fakeStatusCode != NOT_MODIFIED) {
                    message = message.replaceAll("^(.*Local:\t)\\d{3}(\t.*Proxy:\t.*\t)\\d{3}(\t.*)$",
                            "$1" + String.valueOf(fakeStatusCode) + "$2" + String.valueOf(fakeStatusCode) + "$3");
                }
                Pattern compile = Pattern.compile("([^\\t]*\\t[^\\t]*\\t)([^\\t]+)(\\t.*)$");
                Matcher match = compile.matcher(message);
                if (match.find()) {
                    message = match.group(1) + match.group(2).replace(" ", "\t") + match.group(3);
                }
                logger.info(message.replaceAll(REAL_DEST, realDest));
            } catch (Exception e) {
                logger.error(e.getMessage());
            } finally {
                nextListener.proceed();
            }
        }
    }

    private class StatsdCompletionListener implements ExchangeCompletionListener {

        @Override
        public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
            try {
                final Integer clientOpenConnection = exchange.getAttachment(ClientStatisticsMarker.TARGET_CONN);
                final String targetUri = exchange.getAttachment(HostSelector.REAL_DEST);
                final boolean targetIsUndef = "UNDEF".equals(targetUri);
                final String virtualhost = exchange.getRequestHeaders().get(HttpHeaders.HOST).getFirst();
                final Integer statusCode = exchange.getStatusCode();
                final Integer responseTime = getResponseTime(exchange);
                final String method = exchange.getRequestMethod().toString();

                final String key = cleanUpKey(virtualhost) + "." + cleanUpKey(targetUri);
                sendStatusCodeCount(key, statusCode, targetIsUndef);
                sendActiveConnCount(key, clientOpenConnection, targetIsUndef);
                sendHttpMethodCount(key, method);
                sendResponseTime(key, responseTime, targetIsUndef);

            } catch (Exception e) {
                logger.error(e.getMessage());
            } finally {
                nextListener.proceed();
            }
        }

        private void sendStatusCodeCount(String key, Integer statusCode, boolean targetIsUndef) {
            int realStatusCode = targetIsUndef ? 503 : statusCode;
            String fullKey = key + ".status." + realStatusCode;
            statsdClient.incr(fullKey);
        }

        private void sendActiveConnCount(String key, Integer clientOpenConnection, boolean targetIsUndef) {
            int conn = (clientOpenConnection != null && !targetIsUndef) ? clientOpenConnection : 0;
            String fullKey = key + ".activeConns";
            statsdClient.gauge(fullKey, conn);
        }

        private void sendHttpMethodCount(String key, String method) {
            String fullKey = key + ".method." + method;
            statsdClient.count(fullKey, 1);
        }

        private void sendResponseTime(String key, long requestTime, boolean targetIsUndef) {
            long realRequestTime = targetIsUndef ? 0 : requestTime;
            String fullKey = key + ".responseTime";
            statsdClient.timing(fullKey, realRequestTime);
        }

        private int getResponseTime(HttpServerExchange exchange) {
            return Math.round(Float.parseFloat(responseTimeAttribute.readAttribute(exchange)));
        }

        private String cleanUpKey(String str) {
            return str.replaceAll("http://", "").replaceAll("[.:]", "_");
        }
    }
}
